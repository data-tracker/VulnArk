package com.vulnark.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vulnark.entity.Asset;
import com.vulnark.entity.SyncLog;
import com.vulnark.entity.SystemConfig;
import com.vulnark.entity.Vulnerability;
import com.vulnark.repository.AssetRepository;
import com.vulnark.repository.SyncLogRepository;
import com.vulnark.repository.SystemConfigRepository;
import com.vulnark.repository.VulnerabilityRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Dependency-Track 定期同步服务。
 *
 * 同步语义（与设置页展示一致）：
 * - 映射表为同步白名单：仅遍历已配置的 DTrack 项目 -> VulnArk 资产
 * - 新增：DTrack 有而平台无 -> 创建（API 内部走 computeBand 自动分级，source=API）
 * - 移除：DTrack 不再报告 且 平台状态=OPEN -> 自动置 RESOLVED（人工改过状态的绝不触碰）
 * - 刷新：仅对 source=API 的漏洞更新 CVSS/EPSS/KEV 并重算 Band（人工/Excel 数据不动）
 * - 人工数据（分析状态/状态/负责人/解决方案）永不覆盖
 */
@Service
public class DTrackSyncService {

    private static final Logger logger = LoggerFactory.getLogger(DTrackSyncService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Autowired private SystemConfigRepository configRepo;
    @Autowired private SyncLogRepository syncLogRepo;
    @Autowired private VulnerabilityRepository vulnRepo;
    @Autowired private AssetRepository assetRepo;
    @Autowired private AssetService assetService;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10)).build();

    private volatile boolean running = false;

    // ===== 配置读取 =====
    private String cfg(String key) {
        return configRepo.findByConfigKey(key).map(SystemConfig::getConfigValue).orElse("");
    }

    public boolean isConfigured() {
        return !cfg("dtrack.url").isEmpty() && !cfg("dtrack.api_key").isEmpty();
    }

    // ===== DTrack HTTP =====
    private JsonNode dtrackGet(String path) throws Exception {
        HttpRequest req = HttpRequest.newBuilder(URI.create(cfg("dtrack.url") + path))
                .header("X-Api-Key", cfg("dtrack.api_key"))
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(30))
                .GET().build();
        HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() != 200) {
            throw new RuntimeException("DTrack HTTP " + resp.statusCode() + " " + path);
        }
        return MAPPER.readTree(resp.body());
    }

    /** 测试连接：拉一次项目列表 */
    public Map<String, Object> testConnection() {
        Map<String, Object> r = new HashMap<>();
        try {
            JsonNode projects = dtrackGet("/api/v1/project");
            r.put("ok", true);
            r.put("projectCount", projects.size());
        } catch (Exception e) {
            r.put("ok", false);
            r.put("message", e.getMessage());
        }
        return r;
    }

    /** DTrack 项目名列表（供同名自动匹配） */
    public List<String> listDtrackProjects() throws Exception {
        List<String> names = new ArrayList<>();
        dtrackGet("/api/v1/project").forEach(p -> names.add(p.path("name").asText()));
        return names;
    }

    // ===== 同步主体 =====
    public boolean isRunning() { return running; }

    /** 定时触发：每分钟检查频率配置（hourly/daily/off） */
    @Scheduled(fixedDelay = 60000)
    public void scheduledTick() {
        try {
            String freq = cfg("dtrack.frequency");
            if ("off".equals(freq) || !isConfigured() || running) return;
            // 注意：必须查最近一次 AUTO 记录（此前写法先取最近1条再过滤，
            // 手动同步后会误判 last=null 而触发多余的定时同步）
            LocalDateTime last = syncLogRepo
                    .findFirstByTriggerTypeOrderBySyncTimeDesc("AUTO")
                    .map(SyncLog::getSyncTime).orElse(null);
            boolean due = false;
            LocalDateTime now = LocalDateTime.now();
            if (last == null) {
                due = true;
            } else if ("hourly".equals(freq)) {
                due = last.isBefore(now.minusHours(1));
            } else { // daily：每天凌晨 2:00 固定时刻（未到 2 点看昨天窗口）
                LocalDateTime windowStart = now.toLocalDate().atTime(2, 0);
                if (now.isBefore(windowStart)) windowStart = windowStart.minusDays(1);
                due = last.isBefore(windowStart);
            }
            if (due) runSync("AUTO", null);
        } catch (Exception e) {
            logger.error("同步定时检查异常", e);
        }
    }

    /** 手动触发（异步） */
    public void triggerManual() {
        if (running) throw new RuntimeException("同步正在进行中，请稍候");
        new Thread(() -> runSync("MANUAL", null)).start();
    }

    /** 执行同步（triggerType: AUTO/MANUAL；仅测试时传 dryMapping） */
    public SyncLog runSync(String triggerType, Void unused) {
        running = true;
        SyncLog log = new SyncLog();
        log.setTriggerType(triggerType);
        List<String> details = new ArrayList<>();
        try {
            Map<String, String> mapping = parseMapping();
            boolean autoResolve = Boolean.parseBoolean(cfg("dtrack.auto_resolve"));
            boolean refresh = Boolean.parseBoolean(cfg("dtrack.refresh_fields"));

            List<Map<String, Object>> addedDetail = new ArrayList<>();
            List<Map<String, Object>> updatedDetail = new ArrayList<>();
            List<Map<String, Object>> resolvedDetail = new ArrayList<>();
            for (Map.Entry<String, String> e : mapping.entrySet()) {
                String dtrackName = e.getKey(), assetName = e.getValue();
                try {
                    Map<String, Object> r = syncOne(dtrackName, assetName, autoResolve, refresh);
                    details.add(String.format("%s -> %s：新增%d 更新%d 置已解决%d 失败%d",
                            dtrackName, assetName,
                            (Integer) r.get("added"), (Integer) r.get("updated"),
                            (Integer) r.get("resolved"), (Integer) r.get("failed")));
                    log.setAdded(log.getAdded() + (Integer) r.get("added"));
                    log.setUpdated(log.getUpdated() + (Integer) r.get("updated"));
                    log.setResolved(log.getResolved() + (Integer) r.get("resolved"));
                    log.setFailed(log.getFailed() + (Integer) r.get("failed"));
                    addedDetail.addAll((List<Map<String, Object>>) r.get("addedList"));
                    updatedDetail.addAll((List<Map<String, Object>>) r.get("updatedList"));
                    resolvedDetail.addAll((List<Map<String, Object>>) r.get("resolvedList"));
                } catch (Exception ex) {
                    log.setFailed(log.getFailed() + 1);
                    details.add(dtrackName + " -> " + assetName + "：失败 " + ex.getMessage());
                }
            }
            if (mapping.isEmpty()) details.add("映射表为空，未同步任何项目");

            // ===== EPSS/KEV 自动补全（FIRST + CISA 原始数据源）=====
            Map<String, Object> enrichResult = null;
            if (Boolean.parseBoolean(cfg("dtrack.enrich"))) {
                try {
                    enrichResult = enrichRiskFields(refresh);
                } catch (Exception e) {
                    logger.error("EPSS/KEV 补全失败", e);
                    enrichResult = new LinkedHashMap<>();
                    enrichResult.put("error", e.getMessage());
                }
            }

            // detail 存 JSON：摘要 + 各类变更的 CVE 明细（前端展开显示）
            Map<String, Object> detailJson = new LinkedHashMap<>();
            detailJson.put("summary", String.join("\n", details));
            detailJson.put("added", addedDetail);
            detailJson.put("updated", updatedDetail);
            detailJson.put("resolved", resolvedDetail);
            if (enrichResult != null) detailJson.put("enrich", enrichResult);
            log.setDetail(MAPPER.writeValueAsString(detailJson));
        } catch (Exception e) {
            log.setFailed(log.getFailed() + 1);
            log.setDetail("同步失败: " + e.getMessage());
        } finally {
            running = false;
        }
        return syncLogRepo.save(log);
    }

    /**
     * EPSS/KEV 自动补全：
     * - KEV：下载 CISA 目录 JSON，全量比对（CISA 为权威源，字段按目录刷新）
     * - EPSS：FIRST API 批量查询（100/批）
     * - Band 重算：仅 source=API 且开关开启时（人工/Excel 的 Band 不动，
     *   但 EPSS/KEV 字段本身对所有漏洞补全——字段是事实，Band 是决策）
     */
    private Map<String, Object> enrichRiskFields(boolean recalcBand) throws Exception {
        Map<String, Object> result = new LinkedHashMap<>();
        List<Vulnerability> all = vulnRepo.findAll().stream()
                .filter(v -> Boolean.FALSE.equals(v.getDeleted()) && v.getCveId() != null).toList();

        // --- CISA KEV 目录 ---
        Set<String> kevSet = new HashSet<>();
        HttpRequest kevReq = HttpRequest.newBuilder(URI.create(
                "https://www.cisa.gov/sites/default/files/feeds/known_exploited_vulnerabilities.json"))
                .timeout(Duration.ofSeconds(60)).GET().build();
        HttpResponse<String> kevResp = http.send(kevReq, HttpResponse.BodyHandlers.ofString());
        if (kevResp.statusCode() == 200) {
            MAPPER.readTree(kevResp.body()).path("vulnerabilities")
                    .forEach(v -> kevSet.add(v.path("cveID").asText()));
        } else {
            throw new RuntimeException("CISA KEV 目录下载失败 HTTP " + kevResp.statusCode());
        }

        // --- FIRST EPSS 批量查询 ---
        Map<String, Double> epssMap = new HashMap<>();
        List<String> cves = all.stream().map(Vulnerability::getCveId).distinct().toList();
        for (int i = 0; i < cves.size(); i += 100) {
            List<String> batch = cves.subList(i, Math.min(i + 100, cves.size()));
            HttpRequest epssReq = HttpRequest.newBuilder(URI.create(
                    "https://api.first.org/data/v1/epss?cve=" + String.join(",", batch)))
                    .timeout(Duration.ofSeconds(30)).GET().build();
            HttpResponse<String> epssResp = http.send(epssReq, HttpResponse.BodyHandlers.ofString());
            if (epssResp.statusCode() == 200) {
                MAPPER.readTree(epssResp.body()).path("data").forEach(d ->
                        epssMap.put(d.path("cve").asText(), d.path("epss").asDouble()));
            }
        }

        // --- 应用更新 ---
        int epssUpdated = 0, kevUpdated = 0, bandRecalced = 0;
        List<Vulnerability> toSave = new ArrayList<>();
        for (Vulnerability v : all) {
            boolean changed = false;
            Double epss = epssMap.get(v.getCveId());
            if (epss != null) {
                BigDecimal bd = BigDecimal.valueOf(epss).setScale(5, java.math.RoundingMode.HALF_UP);
                if (v.getEpssScore() == null || v.getEpssScore().compareTo(bd) != 0) {
                    v.setEpssScore(bd); epssUpdated++; changed = true;
                }
            }
            boolean kev = kevSet.contains(v.getCveId());
            if ((v.getKev() == null || v.getKev()) != kev) {
                v.setKev(kev); kevUpdated++; changed = true;
            }
            if (changed) {
                // Band 重算仅限 API 来源（人工/Excel 的 Band 保留人工判定）
                if (recalcBand && "API".equals(String.valueOf(v.getSource()))
                        && v.getCvssScore() != null) {
                    Vulnerability.Severity band = Vulnerability.computeBand(
                            v.getCvssScore(), v.getEpssScore(), v.getKev());
                    if (band != null && band != v.getSeverity()) {
                        v.setSeverity(band); bandRecalced++;
                    }
                }
                toSave.add(v);
            }
        }
        if (!toSave.isEmpty()) vulnRepo.saveAll(toSave);

        result.put("epssUpdated", epssUpdated);
        result.put("kevUpdated", kevUpdated);
        result.put("bandRecalced", bandRecalced);
        result.put("kevCatalogSize", kevSet.size());
        logger.info("EPSS/KEV 补全完成: epss={}, kev={}, band 重算={}", epssUpdated, kevUpdated, bandRecalced);
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> parseMapping() throws Exception {
        String raw = cfg("dtrack.mapping");
        if (raw == null || raw.isBlank()) return Collections.emptyMap();
        return MAPPER.readValue(raw, Map.class);
    }

    /** 同步单个映射：返回计数与 CVE 级明细 */
    @SuppressWarnings("unchecked")
    private Map<String, Object> syncOne(String dtrackName, String assetName,
                          boolean autoResolve, boolean refresh) throws Exception {
        List<Map<String, Object>> addedList = new ArrayList<>();
        List<Map<String, Object>> updatedList = new ArrayList<>();
        List<Map<String, Object>> resolvedList = new ArrayList<>();
        // 目标资产必须存在（白名单右值）
        Asset asset = assetRepo.findAll().stream()
                .filter(a -> assetName.equals(a.getName())).findFirst()
                .orElseThrow(() -> new RuntimeException("资产不存在: " + assetName));

        // DTrack 项目
        JsonNode projects = dtrackGet("/api/v1/project");
        JsonNode target = null;
        for (JsonNode p : projects) {
            if (dtrackName.equals(p.path("name").asText())) { target = p; break; }
        }
        if (target == null) throw new RuntimeException("DTrack 项目不存在: " + dtrackName);
        String projectUuid = target.path("uuid").asText();

        // 拉取全部组件漏洞（组件内按 vulnId 去重），key=cveId
        Map<String, JsonNode> dtrackVulns = new HashMap<>();
        Map<String, String[]> compInfo = new HashMap<>();  // cveId -> [name, version]
        JsonNode components = dtrackGet("/api/v1/component/project/" + projectUuid + "?limit=500");
        for (JsonNode comp : components) {
            String cuid = comp.path("uuid").asText();
            String cname = comp.path("name").asText(), cver = comp.path("version").asText();
            try {
                JsonNode vulns = dtrackGet("/api/v1/vulnerability/component/" + cuid);
                Set<String> seen = new HashSet<>();
                for (JsonNode v : vulns) {
                    String vid = v.path("vulnId").asText();
                    if (vid.isEmpty() || !seen.add(vid)) continue;
                    dtrackVulns.putIfAbsent(vid, v);
                    compInfo.putIfAbsent(vid, new String[]{cname, cver});
                }
            } catch (Exception e) {
                logger.warn("组件 {} 漏洞拉取失败: {}", cname, e.getMessage());
            }
        }

        // 平台该资产现有漏洞（按 cveId 索引）
        List<Vulnerability> existing = vulnRepo.findAll().stream()
                .filter(v -> asset.getId().equals(v.getAssetId()) && Boolean.FALSE.equals(v.getDeleted()))
                .toList();
        Map<String, Vulnerability> existingByCve = new HashMap<>();
        for (Vulnerability v : existing) {
            if (v.getCveId() != null) existingByCve.putIfAbsent(v.getCveId(), v);
        }

        int added = 0, updated = 0, resolvedN = 0, failed = 0;

        // 新增与刷新
        for (Map.Entry<String, JsonNode> e : dtrackVulns.entrySet()) {
            String cveId = e.getKey();
            JsonNode dv = e.getValue();
            String[] ci = compInfo.get(cveId);
            Vulnerability cur = existingByCve.get(cveId);
            try {
                if (cur == null) {
                    Vulnerability v = buildVulnerability(cveId, dv, ci, asset);
                    vulnRepo.save(v);
                    added++;
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("cve", cveId); d.put("title", v.getTitle());
                    d.put("cvssLevel", v.getCvssLevel() == null ? "-" : String.valueOf(v.getCvssLevel()));
                    d.put("band", String.valueOf(v.getSeverity()));
                    addedList.add(d);
                } else if (refresh && "API".equals(cur.getSource())) {
                    // 仅刷新 API 同步来源的漏洞（人工/Excel 的不动）
                    boolean changed = applyRiskFields(cur, dv);
                    if (changed) {
                        vulnRepo.save(cur); updated++;
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("cve", cveId);
                        d.put("cvssLevel", cur.getCvssLevel() == null ? "-" : String.valueOf(cur.getCvssLevel()));
                        d.put("band", String.valueOf(cur.getSeverity()));
                        updatedList.add(d);
                    }
                }
            } catch (Exception ex) {
                failed++;
                logger.warn("同步 {} 失败: {}", cveId, ex.getMessage());
            }
        }

        // 移除语义：DTrack 不再报告 且 状态=OPEN
        if (autoResolve) {
            for (Vulnerability v : existing) {
                if (v.getCveId() == null) continue;
                if (!dtrackVulns.containsKey(v.getCveId())
                        && v.getStatus() == Vulnerability.Status.OPEN
                        && "API".equals(v.getSource())) {
                    v.setStatus(Vulnerability.Status.RESOLVED);
                    v.setResolvedDate(LocalDate.now());
                    String note = String.format("[%s] DTrack 不再报告该漏洞（组件可能已升级或移除），自动置为已解决",
                            LocalDate.now());
                    v.setSolution(v.getSolution() == null ? note : note + "\n\n" + v.getSolution());
                    vulnRepo.save(v);
                    resolvedN++;
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("cve", v.getCveId()); d.put("title", v.getTitle());
                    resolvedList.add(d);
                }
            }
        }
        // 刷新资产漏洞计数（创建/置已解决都会改变数量）
        try {
            assetService.updateAssetStatistics(asset.getId());
        } catch (Exception e) {
            logger.warn("资产统计刷新失败: {}", e.getMessage());
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("added", added); result.put("updated", updated);
        result.put("resolved", resolvedN); result.put("failed", failed);
        result.put("addedList", addedList);
        result.put("updatedList", updatedList);
        result.put("resolvedList", resolvedList);
        return result;
    }

    private Vulnerability buildVulnerability(String cveId, JsonNode dv, String[] ci, Asset asset) {
        Vulnerability v = new Vulnerability();
        String cname = ci != null ? ci[0] : "?", cver = ci != null ? ci[1] : "?";
        boolean isCve = cveId.startsWith("CVE-");
        String display = isCve ? cveId : dv.path("source").asText("") + ":" + cveId;
        String title = display + " (" + cname + "@" + cver + ")";
        v.setTitle(title.length() > 255 ? title.substring(0, 255) : title);
        v.setCveId(cveId);
        StringBuilder desc = new StringBuilder("组件: " + cname + "@" + cver + "\n来源: " + dv.path("source").asText());
        if (isCve) desc.append("\n\n参考: https://nvd.nist.gov/vuln/detail/").append(cveId);
        v.setDescription(desc.toString());
        v.setStatus(Vulnerability.Status.OPEN);
        v.setDiscoveredDate(LocalDate.now());
        v.setAssetId(asset.getId());
        v.setProjectId(getDefaultProjectId());
        v.setReporterId(1L);
        v.setSource(Vulnerability.VulnerabilitySource.API);
        v.setKev(Boolean.FALSE);
        v.setAffectedSystems("依赖组件 " + cname + "@" + cver);
        applyRiskFields(v, dv);
        return v;
    }

    /** 应用风险字段并按 CRA 规则重算 Band；返回是否有变化 */
    private boolean applyRiskFields(Vulnerability v, JsonNode dv) {
        boolean changed = false;
        Double cvss = dv.hasNonNull("cvssV3BaseScore") ? dv.get("cvssV3BaseScore").asDouble()
                : (dv.hasNonNull("cvssV2BaseScore") ? dv.get("cvssV2BaseScore").asDouble() : null);
        if (cvss != null) {
            BigDecimal bd = BigDecimal.valueOf(cvss).setScale(1, java.math.RoundingMode.HALF_UP);
            if (bd.compareTo(cvss(v)) != 0) { v.setCvssScore(bd); changed = true; }
            Vulnerability.CvssLevel lvl = Vulnerability.CvssLevel.valueOf(bandToLevel(bd.doubleValue()));
            if (lvl != v.getCvssLevel()) { v.setCvssLevel(lvl); changed = true; }
            Vulnerability.Severity band = Vulnerability.computeBand(bd,
                    v.getEpssScore(), v.getKev());
            if (band != null && band != v.getSeverity()) { v.setSeverity(band); changed = true; }
        }
        return changed;
    }

    private BigDecimal cvss(Vulnerability v) {
        return v.getCvssScore() == null ? BigDecimal.valueOf(-1) : v.getCvssScore();
    }

    private String bandToLevel(double score) {
        if (score >= 9.0) return "CRITICAL";
        if (score >= 7.0) return "HIGH";
        if (score >= 4.0) return "MEDIUM";
        return "LOW";
    }

    @Autowired private com.vulnark.repository.ProjectRepository projectRepo;

    private Long getDefaultProjectId() {
        return findDefaultProject();
    }

    private Long findDefaultProject() {
        return projectRepo.findFirstByName("默认项目").map(com.vulnark.entity.Project::getId)
                .orElseGet(() -> {
                    com.vulnark.entity.Project p = new com.vulnark.entity.Project();
                    p.setName("默认项目");
                    p.setPriority(com.vulnark.entity.Project.Priority.MEDIUM);
                    p.setStatus(com.vulnark.entity.Project.Status.ACTIVE);
                    return projectRepo.save(p).getId();
                });
    }
}
