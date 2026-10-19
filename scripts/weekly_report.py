#!/usr/bin/env python3
"""
VulnArk 漏洞周报生成与发送

数据来源（不依赖平台 API，直读容器内 MySQL）：
  1. vulnerabilities 表 —— 存量 Band 分布 / 分析进展 / 未分析清单
  2. sync_logs 表       —— 本周同步动态（新增/更新/置已解决 + Band 分布）
  3. system_configs 表  —— SMTP 配置 + 默认收件人

用法：
  生成预览（不发送）: python3 scripts/weekly_report.py --preview
  实际发送:           python3 scripts/weekly_report.py --to a@x.com,b@x.com
  （--to 缺省时使用平台配置的 notify.recipients）
"""
import os
import subprocess
import sys
import json
import re
import smtplib
import argparse
from email.mime.text import MIMEText
from email.header import Header
from datetime import datetime, timedelta

# ===== MySQL 通道 =====
COMPOSE = ['docker', 'compose', 'exec', '-T', 'mysql', 'sh', '-c',
           'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" vulnark -N -B']


def q(sql: str) -> str:
    import os
    r = subprocess.run(COMPOSE, input=sql, capture_output=True, text=True,
                       cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    if r.returncode != 0:
        raise RuntimeError(f'MySQL 查询失败: {r.stderr[:200]}')
    return r.stdout


def rows(sql: str):
    out = q(sql).strip()
    if not out:
        return []
    return [line.split('\t') for line in out.split('\n')]


BANDS = ['EMERGENCY', 'CRITICAL', 'HIGH', 'MEDIUM', 'LOW']
BAND_CN = {'EMERGENCY': '紧急', 'CRITICAL': '严重', 'HIGH': '高危',
           'MEDIUM': '中危', 'LOW': '低危'}
VS_CN = {'AFFECTED': '受影响', 'NOT_AFFECTED': '不受影响',
         'FALSE_POSITIVE': '误报', 'NULL': '未分析'}


# ===== 数据采集 =====
def collect():
    d = {}
    # 1. 各资产存量 Band 分布
    d['band'] = {}
    for r in rows("""SELECT a.name, v.severity, COUNT(*) FROM vulnerabilities v
                     JOIN assets a ON v.asset_id=a.id
                     WHERE v.deleted=0 GROUP BY a.name, v.severity"""):
        d['band'].setdefault(r[0], {})[r[1]] = int(r[2])

    # 2. 本周同步动态（sync_logs 近7天，排除保护占位）
    week_logs = rows("""SELECT detail FROM sync_logs
                        WHERE sync_time >= DATE_SUB(NOW(), INTERVAL 7 DAY)
                        AND detail LIKE '%assets%'""")
    sync = {}   # asset -> {added, updated, resolved, addedBands{}}
    band_ups = []  # 本周 Band 升级（enrich）
    for (detail,) in week_logs:
        try:
            j = json.loads(detail)
        except Exception:
            continue
        for asset, blk in (j.get('assets') or {}).items():
            s = sync.setdefault(asset, {'added': 0, 'updated': 0, 'resolved': 0, 'addedBands': {}})
            s['added'] += len(blk.get('added') or [])
            s['updated'] += len(blk.get('updated') or [])
            s['resolved'] += len(blk.get('resolved') or [])
            for it in (blk.get('added') or []):
                b = str(it.get('band', 'LOW')).split('→')[-1].strip()
                s['addedBands'][b] = s['addedBands'].get(b, 0) + 1
            # 更新路径的 Band 升级（band 含 from→to 且目标为 E/C）
            for it in (blk.get('updated') or []):
                b = str(it.get('band', ''))
                if '→' in b:
                    frm, to = [x.strip() for x in b.split('→')]
                    if to in ('EMERGENCY', 'CRITICAL'):
                        band_ups.append({'asset': it.get('asset', asset), 'cve': it.get('cve'),
                                         'from': frm, 'to': to, 'reason': it.get('reason', '-')})
        for it in ((j.get('enrich') or {}).get('bandRecalcedList') or []):
            band_ups.append(it)
    d['sync'] = sync
    # Band 升级按 CVE 去重（多轮同步可能重复记录同一升级，保留最新）
    seen = {}
    for u in band_ups:
        seen[(u.get('asset'), u.get('cve'), u.get('from'), u.get('to'))] = u
    d['band_ups'] = list(seen.values())

    # 3. E+C 范围内分析进展（按资产）
    d['range'] = {}
    for r in rows("""SELECT a.name,
                        COALESCE(v.verification_status, 'NULL'), COUNT(*)
                     FROM vulnerabilities v JOIN assets a ON v.asset_id=a.id
                     WHERE v.deleted=0 AND v.severity IN ('EMERGENCY','CRITICAL')
                     GROUP BY a.name, COALESCE(v.verification_status,'NULL')"""):
        d['range'].setdefault(r[0], {})[r[1]] = int(r[2])

    # 4. 未分析清单（E+C，附触发信号）
    d['unanalyzed'] = rows("""SELECT a.name, v.cve_id, v.severity,
                                v.epss_score, v.kev+0
                             FROM vulnerabilities v JOIN assets a ON v.asset_id=a.id
                             WHERE v.deleted=0 AND v.severity IN ('EMERGENCY','CRITICAL')
                               AND v.verification_status IS NULL
                             ORDER BY FIELD(v.severity,'EMERGENCY','CRITICAL'), v.epss_score DESC""")

    # 4.5 CVE -> 当前分析状态（过滤已确认不受影响的 Band 升级）
    d['vstatus'] = {r[0]: r[1] for r in rows("SELECT cve_id, verification_status FROM vulnerabilities WHERE deleted=0 AND verification_status IS NOT NULL")}

    # 5. SMTP / 收件人配置
    cfg = {r[0]: r[1] for r in rows(
        "SELECT config_key, config_value FROM system_configs WHERE config_key LIKE 'smtp.%' OR config_key='notify.recipients'")}
    d['cfg'] = cfg
    return d


# ===== HTML 生成 =====
TD = "style='border:1px solid #ddd;padding:5px 10px;font-size:13px;'"
TH = "style='border:1px solid #ddd;padding:6px 10px;font-size:13px;background:#f1f5f9;text-align:left;'"
TBL = "style='border-collapse:collapse;width:100%;margin:8px 0;'"
H3 = "style='color:#1a6ee0;border-bottom:2px solid #1a6ee0;padding-bottom:4px;margin:22px 0 8px 0;'"
P = "style='font-size:13px;color:#475569;margin:6px 0;'"
NOTE = "style='font-size:12px;color:#94a3b8;margin:4px 0 14px 0;'"


def band_line(dist: dict) -> str:
    parts = [f"{BAND_CN.get(b, b)} {dist.get(b, 0)}" for b in BANDS if dist.get(b, 0)]
    return ' · '.join(parts) or '无'


def build_html(d, start, end):
    sb = []
    sb.append("<html><body style='font-family:sans-serif;max-width:800px;margin:0 auto;'>")
    sb.append(f"<h2 style='color:#1a6ee0;'>安全漏洞管理周报</h2>")
    sb.append(f"<p {P}>统计周期：{start:%m/%d} - {end:%m/%d} · 数据截至 {end:%m/%d %H:%M}</p>")

    # 一、总体概况
    sb.append(f"<h3 {H3}>一、总体概况</h3>")
    for asset, dist in d['band'].items():
        total = sum(dist.values())
        sb.append(f"<p {P}><b>{asset}</b>　总 {total:,} 条</p>")
        sb.append(f"<p style='font-size:13px;color:#64748b;margin:2px 0 10px 24px;'>{band_line(dist)}</p>")

    # 二、本周动态
    sb.append(f"<h3 {H3}>二、本周动态（数据同步）</h3>")
    if d['sync']:
        for asset in sorted(set(list(d['sync'].keys()) + list(d['band'].keys()))):
            s = d['sync'].get(asset, {'added': 0, 'updated': 0, 'resolved': 0, 'addedBands': {}})
            if not (s['added'] or s['updated'] or s['resolved']):
                sb.append(f"<p {P}><b>{asset}</b>　本周无同步变更</p>")
                continue
            ab = ' · '.join(f"{BAND_CN.get(b, b)} {n}" for b, n in
                            sorted(s['addedBands'].items(), key=lambda x: BANDS.index(x[0]) if x[0] in BANDS else 9))
            sb.append(f"<p {P}><b>{asset}</b>　新增 {s['added']}"
                      + (f"（{ab}）" if ab else "")
                      + f" · 更新 {s['updated']} · 置已解决 {s['resolved']}</p>")
        ups = [u for u in d['band_ups'] if u.get('to') in ('EMERGENCY', 'CRITICAL')
               and d.get('vstatus', {}).get(u.get('cve')) not in ('NOT_AFFECTED', 'FALSE_POSITIVE')]
        if ups:
            sb.append(f"<p {P}><b>本周 Band 升级（升至紧急/严重）</b></p><table {TBL}>"
                      f"<tr><th {TH}>资产</th><th {TH}>CVE</th><th {TH}>变化</th><th {TH}>触发信号</th></tr>")
            for u in ups[:10]:
                sb.append(f"<tr><td {TD}>{u.get('asset','-')}</td><td {TD}><b>{u.get('cve')}</b></td>"
                          f"<td {TD}>{u.get('from')} → <span style='color:#dc2626;font-weight:bold;'>{u.get('to')}</span></td>"
                          f"<td {TD} style='color:#1a6ee0;'>{u.get('reason','-')}</td></tr>")
            sb.append("</table>")
    else:
        sb.append(f"<p {P}>本周无同步变更</p>")

    # 三、分析进展
    sb.append(f"<h3 {H3}>三、分析进展</h3>")
    sb.append(f"<p {P}><b>处置策略</b>：仅分析处置紧急（EMERGENCY）与严重（CRITICAL）；"
              f"高危（HIGH）待 2027 年 CRA 认证机构辅导后决定是否纳入。</p>")
    sb.append(f"<table {TBL}><tr><th {TH}>资产</th><th {TH}>范围内（E+C）</th><th {TH}>已分析</th>"
              f"<th {TH}>受影响</th><th {TH}>不受影响</th><th {TH}>误报</th><th {TH}>未分析</th><th {TH}>分析率</th></tr>")
    for asset, vs in d['range'].items():
        total = sum(vs.values())
        done = total - vs.get('NULL', 0)
        rate = f"{done * 100 // total}%" if total else '-'
        sb.append(f"<tr><td {TD}><b>{asset}</b></td><td {TD}>{total}</td><td {TD}>{done}</td>"
                  f"<td {TD}>{vs.get('AFFECTED',0)}</td><td {TD}>{vs.get('NOT_AFFECTED',0)}</td>"
                  f"<td {TD}>{vs.get('FALSE_POSITIVE',0)}</td><td {TD}>{vs.get('NULL',0)}</td>"
                  f"<td {TD} style='color:#16a34a;font-weight:bold;'>{rate}</td></tr>")
    sb.append("</table>")
    sb.append(f"<p {NOTE}>范围外（高/中/低危）按当前策略不在处置范围。</p>")

    # 四、重点风险
    sb.append(f"<h3 {H3}>四、重点风险（范围内未分析）</h3>")
    # 汇总：行式（与第二节风格一致）
    summary = {}
    for name, cve, sev, epss, kev in d['unanalyzed']:
        summary.setdefault(name, {}).setdefault(sev, 0)
        summary[name][sev] += 1
    if summary:
        for name in sorted(summary.keys()):
            e = summary[name].get('EMERGENCY', 0)
            c = summary[name].get('CRITICAL', 0)
            sb.append(f"<p {P}><b>{name}</b>　未分析 {e + c} 条"
                      + (f"（<span style='color:#dc2626;font-weight:bold;'>紧急 {e}</span>" if e else "（")
                      + (f" · <span style='color:#ea580c;'>严重 {c}</span>" if c else "")
                      + "）</p>")
    if d['unanalyzed']:
        sb.append(f"<table {TBL}><tr><th {TH}>资产</th><th {TH}>CVE</th><th {TH}>级别</th><th {TH}>EPSS</th><th {TH}>触发信号</th></tr>")
        for name, cve, sev, epss, kev in d['unanalyzed'][:20]:
            sig = []
            if kev == '1': sig.append('KEV')
            try:
                if epss and float(epss) > 0.5: sig.append(f"EPSS {float(epss):.2f}>0.5")
            except Exception: pass
            sev_cn = BAND_CN.get(sev, sev)
            color = '#dc2626' if sev == 'EMERGENCY' else '#ea580c'
            sb.append(f"<tr><td {TD}>{name}</td><td {TD}><b>{cve}</b></td>"
                      f"<td {TD} style='color:{color};font-weight:bold;'>{sev_cn}</td>"
                      f"<td {TD}>{epss or '-'}</td><td {TD} style='color:#1a6ee0;'>{' + '.join(sig) or '-'}</td></tr>")
        sb.append("</table>")
        if len(d['unanalyzed']) > 20:
            sb.append(f"<p {NOTE}>…共 {len(d['unanalyzed'])} 条，仅列前 20。</p>")
    else:
        sb.append(f"<p {P} style='color:#16a34a;'>✓ 范围内漏洞已全部完成分析</p>")

    sb.append("<hr style='border:none;border-top:1px solid #ddd;margin:20px 0;'>")
    sb.append("<p style='color:#888;font-size:12px;'>此周报由 VulnArk 漏洞管理平台数据自动生成</p>")
    sb.append("</body></html>")
    return ''.join(sb)


# ===== 发送 =====
def send(html, cfg, to_list, cc_list=None):
    host, port = cfg['smtp.host'], int(cfg.get('smtp.port', '465'))
    user, pwd = cfg['smtp.username'], cfg['smtp.password']
    frm = cfg.get('smtp.from') or user
    dn = cfg.get('smtp.display_name', '').strip()
    from_addr = f"{dn} <{frm}>" if dn else frm

    msg = MIMEText(html, 'html', 'utf-8')
    msg['Subject'] = Header(f'[VulnArk 周报] 安全漏洞管理概况 {datetime.now():%m/%d}', 'utf-8')
    msg['From'] = Header(from_addr, 'utf-8') if dn else frm
    msg['To'] = ', '.join(to_list)
    if cc_list:
        msg['Cc'] = ', '.join(cc_list)

    import ssl
    ctx = ssl.create_default_context()
    if port == 465:
        s = smtplib.SMTP_SSL(host, port, context=ctx, timeout=30)
    else:
        s = smtplib.SMTP(host, port, timeout=30)
        s.starttls(context=ctx)
    s.login(user, pwd)
    s.sendmail(frm, to_list + (cc_list or []), msg.as_string())
    s.close()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--preview', action='store_true', help='只生成不发送')
    ap.add_argument('--to', help='收件人 To（逗号分隔），缺省用平台配置')
    ap.add_argument('--cc', help='抄送人 Cc（逗号分隔）')
    args = ap.parse_args()

    d = collect()
    end = datetime.now()
    start = end - timedelta(days=7)
    html = build_html(d, start, end)

    if args.preview:
        out = os.path.join(os.path.dirname(os.path.dirname(os.path.abspath(__file__))), '..', 'weekly_report_preview.html')
        open(out, 'w').write(html)
        print(f'预览已生成: {out}')
        return

    to_list = ([t.strip() for t in args.to.split(',')] if args.to
               else [t.strip() for t in (d['cfg'].get('notify.recipients') or '').split(',') if t.strip()])
    cc_list = [t.strip() for t in args.cc.split(',')] if args.cc else []
    if not to_list:
        sys.exit('未指定收件人（--to 或平台 notify.recipients）')
    send(html, d['cfg'], to_list, cc_list)
    print(f'周报已发送 至: {", ".join(to_list)}' + (f' 抄送: {", ".join(cc_list)}' if cc_list else ''))


if __name__ == '__main__':
    main()
