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
import java.util.Properties;

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

    /**
     * 同步完成后发送邮件通知（有变更时才发，全 0 不发防骚扰）
     */
    private void sendSyncNotification(SyncLog log, Map<String, Object> detailJson) {
        if (!Boolean.parseBoolean(cfg("notify.enabled"))) return;
        if (log.getAdded() == 0 && log.getUpdated() == 0 && log.getResolved() == 0
                && log.getFailed() == 0) {
            logger.info("同步无变更，跳过通知邮件");
            return;
        }
        String recipients = cfg("notify.recipients");
        if (recipients == null || recipients.isBlank()) {
            logger.info("通知收件人未配置，跳过");
            return;
        }

        // 构建邮件正文（HTML）
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family: sans-serif; max-width: 800px; margin: 0 auto;'>");
        sb.append("<h2 style='color: #1a6ee0;'>Dependency-Track 数据同步报告</h2>");
        sb.append(String.format("<p>时间：%s &nbsp;|&nbsp; 触发：%s</p>",
                log.getSyncTime().toLocalDate(), "AUTO".equals(log.getTriggerType()) ? "定时" : "手动"));
        sb.append(String.format(
                "<p><b>新增 %d</b> · <b>更新 %d</b> · <b>置已解决 %d</b> · 失败 %d</p>",
                log.getAdded(), log.getUpdated(), log.getResolved(), log.getFailed()));

        // 按资产分块
        Map<String, Object> assets = (Map<String, Object>) detailJson.get("assets");
        if (assets != null) {
            for (Map.Entry<String, Object> e : assets.entrySet()) {
                Map<String, Object> blk = (Map<String, Object>) e.getValue();
                List<Map<String, Object>> added = (List<Map<String, Object>>) blk.get("added");
                List<Map<String, Object>> resolved = (List<Map<String, Object>>) blk.get("resolved");
                List<Map<String, Object>> updated = (List<Map<String, Object>>) blk.get("updated");
                if ((added == null || added.isEmpty()) && (resolved == null || resolved.isEmpty())
                        && (updated == null || updated.isEmpty())) continue;

                sb.append(String.format("<h3 style='color: #333; border-bottom: 2px solid #1a6ee0; padding-bottom: 4px;'>资产：%s</h3>", e.getKey()));

                if (added != null && !added.isEmpty()) {
                    sb.append(String.format("<p style='color: #dc2626; font-weight: bold;'>⚠ 新增 %d 条</p>", added.size()));
                    sb.append("<ul>");
                    for (Map<String, Object> d : added) {
                        sb.append(String.format("<li><b>%s</b> [%s] %s</li>",
                                d.get("cve"), d.get("band"), d.get("title")));
                    }
                    sb.append("</ul>");
                }
                if (updated != null && !updated.isEmpty()) {
                    sb.append(String.format("<p style='color: #ea580c; font-weight: bold;'>⟳ 更新 %d 条</p>", updated.size()));
                    sb.append("<ul>");
                    for (Map<String, Object> d : updated) {
                        String band = String.valueOf(d.get("band"));
                        String reason = d.containsKey("reason") ? " | " + d.get("reason") : "";
                        sb.append(String.format("<li><b>%s</b> %s%s</li>",
                                d.get("cve"), band.contains("→") ? band : "", reason));
                    }
                    sb.append("</ul>");
                }
                if (resolved != null && !resolved.isEmpty()) {
                    sb.append(String.format("<p style='color: #16a34a; font-weight: bold;'>✓ 置已解决 %d 条</p>", resolved.size()));
                    sb.append("<ul>");
                    for (Map<String, Object> d : resolved) {
                        sb.append(String.format("<li><b>%s</b> %s</li>", d.get("cve"), d.get("title")));
                    }
                    sb.append("</ul>");
                }
            }
        }

        // Band 重算告警
        Map<String, Object> enrich = (Map<String, Object>) detailJson.get("enrich");
        if (enrich != null) {
            List<Map<String, Object>> bandList = (List<Map<String, Object>>) enrich.get("bandRecalcedList");
            if (bandList != null && !bandList.isEmpty()) {
                sb.append("<h3 style='color: #dc2626;'>🚨 Band 变更告警</h3><ul>");
                for (Map<String, Object> d : bandList) {
                    sb.append(String.format("<li><b>%s</b> [%s]: %s → <span style='color:#dc2626;font-weight:bold;'>%s</span> (%s)</li>",
                            d.get("cve"), d.get("asset"), d.get("from"), d.get("to"), d.get("reason")));
                }
                sb.append("</ul>");
            }
        }

        sb.append("<hr style='border: none; border-top: 1px solid #ddd; margin: 20px 0;'>");
        sb.append("<p style='color: #888; font-size: 12px;'>此邮件由 VulnArk 自动发送 · 全 0 同步不发信</p>");
        sb.append("</body></html>");

        // 发送
        try {
            org.springframework.mail.javamail.JavaMailSenderImpl sender = new org.springframework.mail.javamail.JavaMailSenderImpl();
            sender.setHost(cfg("smtp.host"));
            sender.setPort(Integer.parseInt(cfg("smtp.port")));
            sender.setUsername(cfg("smtp.username"));
            sender.setPassword(cfg("smtp.password"));
            sender.setDefaultEncoding("UTF-8");
            Properties props = sender.getJavaMailProperties();
            boolean ssl = Boolean.parseBoolean(cfg("smtp.ssl"));
            props.put("mail.transport.protocol", "smtp");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.ssl.enable", String.valueOf(ssl));
            props.put("mail.smtp.starttls.enable", String.valueOf(!ssl));
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "30000");

            jakarta.mail.internet.MimeMessage msg = sender.createMimeMessage();
            org.springframework.mail.javamail.MimeMessageHelper helper = new org.springframework.mail.javamail.MimeMessageHelper(msg, "UTF-8");
            String dn = cfg("smtp.display_name").trim();
            helper.setFrom(dn.isEmpty() ? cfg("smtp.from")
                    : new String(dn.getBytes("UTF-8"), "UTF-8") + " <" + cfg("smtp.from") + ">");
            helper.setTo(recipients.split("[,;\s]+"));
            helper.setSubject(String.format("[漏洞平台] Dependency-Track 同步：新增 %d / 更新 %d / 置已解决 %d",
                    log.getAdded(), log.getUpdated(), log.getResolved()));
            helper.setText(sb.toString(), true);
            sender.send(msg);
            logger.info("同步通知邮件已发送至 {}", recipients);
        } catch (Exception e) {
            throw new RuntimeException("邮件发送失败: " + e.getMessage(), e);
        }
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

            // 明细按资产分块组织：资产名 -> {summary, added, updated, resolved}
            Map<String, Object> assetsDetail = new LinkedHashMap<>();
            int descSuppTotal = 0;
            for (Map.Entry<String, String> e : mapping.entrySet()) {
                String dtrackName = e.getKey(), assetName = e.getValue();
                Map<String, Object> block = new LinkedHashMap<>();
                block.put("summary", "");
                block.put("added", new ArrayList<Map<String, Object>>());
                block.put("updated", new ArrayList<Map<String, Object>>());
                block.put("resolved", new ArrayList<Map<String, Object>>());
                assetsDetail.put(assetName, block);
                try {
                    Map<String, Object> r = syncOne(dtrackName, assetName, autoResolve, refresh);
                    String line = String.format("新增%d 更新%d 置已解决%d 失败%d",
                            (Integer) r.get("added"), (Integer) r.get("updated"),
                            (Integer) r.get("resolved"), (Integer) r.get("failed"));
                    details.add(String.format("%s -> %s：%s", dtrackName, assetName, line));
                    block.put("summary", line);
                    log.setAdded(log.getAdded() + (Integer) r.get("added"));
                    log.setUpdated(log.getUpdated() + (Integer) r.get("updated"));
                    log.setResolved(log.getResolved() + (Integer) r.get("resolved"));
                    log.setFailed(log.getFailed() + (Integer) r.get("failed"));
                    descSuppTotal += (Integer) r.get("descSupplemented");
                    block.put("added", r.get("addedList"));
                    block.put("updated", r.get("updatedList"));
                    block.put("resolved", r.get("resolvedList"));
                } catch (Exception ex) {
                    log.setFailed(log.getFailed() + 1);
                    block.put("summary", "失败 " + ex.getMessage());
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
            detailJson.put("descSupplemented", descSuppTotal);
            detailJson.put("summary", String.join("\n", details));
            detailJson.put("assets", assetsDetail);
            if (enrichResult != null) detailJson.put("enrich", enrichResult);
            log.setDetail(MAPPER.writeValueAsString(detailJson));

            // ===== 同步邮件通知（有变更时才发）=====
            try {
                sendSyncNotification(log, detailJson);
            } catch (Exception e) {
                logger.error("同步通知邮件发送失败: {}", e.getMessage());
            }
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
        Map<Long, String> assetNames = new HashMap<>();
        assetRepo.findAll().forEach(a -> assetNames.put(a.getId(), a.getName()));
        int epssUpdated = 0, kevUpdated = 0, bandRecalced = 0;
        List<Map<String, Object>> bandRecalcedList = new ArrayList<>();
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
            // 统一更新策略：Band 全量重算（不受字段是否变化限制——存量漏洞
            // 即使 EPSS/KEV 无更新，也可能存在旧人工 Band 与最新数据不一致）
            if (recalcBand && v.getCvssScore() != null) {
                Vulnerability.Severity band = Vulnerability.computeBand(
                        v.getCvssScore(), v.getEpssScore(), v.getKev());
                if (band != null && band != v.getSeverity()) {
                    Map<String, Object> bd = new LinkedHashMap<>();
                    bd.put("asset", assetNames.getOrDefault(v.getAssetId(), "-"));
                    bd.put("cve", v.getCveId());
                    bd.put("from", String.valueOf(v.getSeverity()));
                    bd.put("reason", bandReason(v));
                    bd.put("to", String.valueOf(band));
                    bandRecalcedList.add(bd);
                    v.setSeverity(band); bandRecalced++;
                    changed = true;
                }
            }
            if (changed) {
                toSave.add(v);
            }
        }
        if (!toSave.isEmpty()) vulnRepo.saveAll(toSave);

        result.put("epssUpdated", epssUpdated);
        result.put("kevUpdated", kevUpdated);
        result.put("bandRecalced", bandRecalced);
        result.put("bandRecalcedList", bandRecalcedList);
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

        int added = 0, updated = 0, resolvedN = 0, failed = 0, descSupp = 0;

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
                    d.put("asset", asset.getName());
                    d.put("cve", cveId); d.put("title", v.getTitle());
                    d.put("cvssLevel", v.getCvssLevel() == null ? "-" : String.valueOf(v.getCvssLevel()));
                    d.put("band", String.valueOf(v.getSeverity()));
                    addedList.add(d);
                } else if (refresh) {
                    // 统一更新策略：所有已存在 CVE 均比对并刷新 CVSS/级别/Band
                    // （人工判定与分析内容：分析状态/处置状态/负责人/解决方案不碰）
                    String oldLevel = cur.getCvssLevel() == null ? "-" : String.valueOf(cur.getCvssLevel());
                    String oldScore = cur.getCvssScore() == null ? "-" : String.valueOf(cur.getCvssScore());
                    String oldBand = String.valueOf(cur.getSeverity());
                    boolean[] descSuppFlag = {false};
                    boolean changed = applyRiskFields(cur, dv, descSuppFlag);
                    if (descSuppFlag[0]) descSupp++;
                    boolean riskChanged = changed;
                    if (descSuppFlag[0] && !riskChanged) vulnRepo.save(cur); // 仅描述变化：保存但不计更新
                    if (riskChanged) {
                        vulnRepo.save(cur); updated++;
                        // 明细记录变化前后值（from -> to），便于追溯
                        Map<String, Object> d = new LinkedHashMap<>();
                        d.put("cve", cveId);
                        String newLevel = cur.getCvssLevel() == null ? "-" : String.valueOf(cur.getCvssLevel());
                        String newScore = cur.getCvssScore() == null ? "-" : String.valueOf(cur.getCvssScore());
                        String newBand = String.valueOf(cur.getSeverity());
                        d.put("cvssLevel", oldLevel.equals(newLevel) ? newLevel : oldLevel + "→" + newLevel);
                        d.put("cvssScore", oldScore.equals(newScore) ? newScore : oldScore + "→" + newScore);
                        d.put("band", oldBand.equals(newBand) ? newBand : oldBand + "→" + newBand);
                        if (!oldBand.equals(newBand)) {
                            d.put("reason", bandReason(cur));
                        }
                        updatedList.add(d);
                    }
                }
            } catch (Exception ex) {
                failed++;
                logger.warn("同步 {} 失败: {}", cveId, ex.getMessage());
            }
        }

        // 移除语义：DTrack 不再报告 且 状态=OPEN
        // 统一策略（用户确认）：无论 source（API 或 Excel 人工导入），DTrack 不再
        // 报告的待处理 CVE 均自动置已解决并附备注，保留记录不删除
        if (autoResolve) {
            for (Vulnerability v : existing) {
                if (v.getCveId() == null) continue;
                if (!dtrackVulns.containsKey(v.getCveId())
                        && v.getStatus() == Vulnerability.Status.OPEN) {
                    v.setStatus(Vulnerability.Status.RESOLVED);
                    v.setResolvedDate(LocalDate.now());
                    String note = String.format("[%s] DTrack 不再报告该漏洞（组件可能已升级或移除），自动置为已解决",
                            LocalDate.now());
                    v.setSolution(v.getSolution() == null ? note : note + "\n\n" + v.getSolution());
                    vulnRepo.save(v);
                    resolvedN++;
                    Map<String, Object> d = new LinkedHashMap<>();
                    d.put("asset", asset.getName());
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
        result.put("descSupplemented", descSupp);
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
        String dtrackDesc = dv.path("description").asText("");
        if (!dtrackDesc.isBlank()) {
            desc.append("\n\n漏洞描述: ").append(dtrackDesc.trim());
        }
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
        applyRiskFields(v, dv, null);
        return v;
    }

    /** 应用风险字段并按 CRA 规则重算 Band；返回是否有变化 */
    private boolean applyRiskFields(Vulnerability v, JsonNode dv, boolean[] descSupp) {
        boolean changed = false;
        // 补充 DTrack 官方漏洞描述（若缺失则追加，不覆盖已有内容）
        // 不计入"更新"（量大且重要性低于风险字段变化），单独统计
        String dtrackDesc = dv.path("description").asText("").trim();
        if (!dtrackDesc.isBlank() && (v.getDescription() == null
                || !v.getDescription().contains(dtrackDesc.substring(0, Math.min(50, dtrackDesc.length()))))) {
            String base = v.getDescription() == null ? "" : v.getDescription();
            v.setDescription(base + (base.isEmpty() ? "" : "\n\n") + "漏洞描述: " + dtrackDesc);
            if (descSupp != null) descSupp[0] = true;
        }
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

    /**
     * Band 变化的触发信号说明：标注哪些风险信号命中了 CRA 规则
     */
    private String bandReason(Vulnerability v) {
        List<String> reasons = new ArrayList<>();
        if (Boolean.TRUE.equals(v.getKev())) reasons.add("KEV");
        if (v.getEpssScore() != null && v.getEpssScore().doubleValue() > 0.5) {
            reasons.add("EPSS " + v.getEpssScore() + ">0.5");
        } else if (v.getEpssScore() == null) {
            reasons.add("EPSS待补全");  // enrich 步骤稍后补上，可能触发 Band 变化
        }
        if (v.getCvssScore() != null && v.getCvssScore().doubleValue() >= 9.0) {
            reasons.add("CVSS≥9");
        } else if (v.getCvssScore() != null && v.getCvssScore().doubleValue() >= 7.0) {
            reasons.add("CVSS≥7");
        }
        return reasons.isEmpty() ? "-" : String.join(" + ", reasons);
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
