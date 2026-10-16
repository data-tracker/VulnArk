#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
CRA 分析表导入：Vulnerability Analysis CRA.xlsx / DAP847_CVE_Analysis_20260817
→ VulnArk 资产 DAP847-AC

直接生成 SQL 经 mysql 容器执行（绕过 API 的自动分级逻辑，
保留 Excel 中人工判定的 Band）。

列映射：
  Component/Version -> 标题/受影响系统/描述
  Band        -> severity      (Emergency/Critical/High/Medium/Low)
  CVSS        -> cvss_level    (Critical/High/Medium/Low)
  CVSS Score  -> cvss_score
  EPSS        -> epss_score
  KEV         -> kev           (Y/N)
  Owner       -> assignee_id   (Steven.CHEN->steven.chen, Juan.WANG->juan.wang)
  Affected?   -> verification_status (Yes->AFFECTED, Not->NOT_AFFECTED, 空->NULL)
  Analysis    -> solution（与 Fix plan 拼接）
  Fix plan    -> solution 补充
  Assess date -> discovered_date（空则用基准日 2026-08-17）

用法：python3 scripts/import_cra_analysis.py /path/to/Vulnerability\ Analysis\ CRA.xlsx
"""
import io
import contextlib
import subprocess
import sys

BAND_MAP = {'Emergency': 'EMERGENCY', 'Critical': 'CRITICAL', 'High': 'HIGH',
            'Medium': 'MEDIUM', 'Low': 'LOW'}
LEVEL_MAP = {'Critical': 'CRITICAL', 'High': 'HIGH', 'Medium': 'MEDIUM',
             'Low': 'LOW', 'None': 'NONE'}
AFFECTED_MAP = {'Yes': 'AFFECTED', 'Not': 'NOT_AFFECTED', 'No': 'NOT_AFFECTED'}
OWNER_MAP = {'Steven.CHEN': 15, 'Juan.WANG': 14}

import sys as _sys
SHEET_NAME = _sys.argv[2] if len(_sys.argv) > 2 else 'DAP847_CVE_Analysis_20260817'
ASSET_NAME = _sys.argv[3] if len(_sys.argv) > 3 else 'DAP847-A/C'
AFFECTED_COL = _sys.argv[4] if len(_sys.argv) > 4 else 'K'
ASSET_ID = None       # 运行时按名查询
PROJECT_ID = 5        # 默认项目
REPORTER_ID = 1       # admin
DEFAULT_DATE = '2026-08-17'   # 分析基准日（Assess date 为空时使用）

COMPOSE = ['docker', 'compose', 'exec', '-T', 'mysql', 'sh',
           '-c', 'mysql --default-character-set=utf8mb4 -uroot -p"$MYSQL_ROOT_PASSWORD" vulnark']


def esc(v):
    if v is None:
        return 'NULL'
    s = str(v).replace('\\', '\\\\').replace("'", "\\'").replace('\n', '\\n').replace('\r', '')
    return f"'{s}'"


def main(xlsx_path):
    # 解析 Excel：复用工作区的手工解析器源码（替换其内嵌的文件路径）
    with contextlib.redirect_stdout(io.StringIO()):
        code = open('../scripts/read_cra_xlsx.py').read().replace(
            "zipfile.ZipFile('Vulnerability Analysis CRA.xlsx')",
            f"zipfile.ZipFile({xlsx_path!r})")
        mod = {}
        exec(code, mod)
    read_sheet, sheets = mod['read_sheet'], mod['sheets']

    if SHEET_NAME not in sheets:
        raise SystemExit(f'sheet 不存在: {SHEET_NAME}，可选: {list(sheets)}')
    rows = read_sheet(sheets[SHEET_NAME])
    data = [r for r in rows[1:] if (r.get('D') or ('', 0))[0]]

    import subprocess as _sp
    r = _sp.run(COMPOSE, input=f"SELECT id FROM assets WHERE name='{ASSET_NAME}' AND deleted=0", capture_output=True, text=True)
    lines = [l.strip() for l in r.stdout.strip().split('\n') if l.strip() and l.strip() != 'id']; aid = lines[0] if lines else ''
    if not aid.isdigit():
        raise SystemExit(f'资产不存在: {ASSET_NAME}（请先在资产管理中创建）')
    ASSET_ID = int(aid)
    print(f'目标资产 {ASSET_NAME!r} -> id={ASSET_ID}')

    sqls = [
        "-- 仅清空目标资产的漏洞（其他资产数据保留！）",
        f"DELETE FROM vulnerabilities WHERE asset_id = {ASSET_ID};",
        f"UPDATE assets SET vulnerability_count = 0 WHERE id = {ASSET_ID};",
        # 定时同步保护：清空 sync_logs 后定时器会误判"从未同步"而在导入
        # 期间自动触发全量同步。写入 AUTO 占位日志（当前时间）阻止本次窗口触发
        "INSERT INTO sync_logs (sync_time, trigger_type, added, updated, resolved, failed, detail) VALUES (NOW(6), 'AUTO', 0, 0, 0, 0, 'Excel 重导保护占位：清空重导期间抑制自动同步');",
    ]
    skip = 0
    for r in data:
        g = lambda c: str((r.get(c) or ('',))[0] or '').strip()
        cve = g('D')
        if not cve.startswith('CVE-'):
            skip += 1
            continue
        comp, ver = g('A'), g('B')
        band = BAND_MAP.get(g('C'))
        level = LEVEL_MAP.get(g('E'))
        score = g('F')
        try:
            score = float(score) if score else None
        except (ValueError, OverflowError):
            score = None
        epss = g('G')
        try:
            epss = float(epss) if epss else None  # 支持科学计数法（如 3.14E-3）
        except (ValueError, OverflowError):
            epss = None
        kev = 1 if g('H').upper() == 'Y' else 0
        owner = OWNER_MAP.get(g('I'))
        affected = AFFECTED_MAP.get(g(AFFECTED_COL))
        analysis, fix = g('J'), g('L')
        adate = g('M')
        if adate.replace('.', '', 1).isdigit():
            # Excel 日期序列号（1900 纪元）转日期
            from datetime import date, timedelta
            adate = (date(1899, 12, 30) + timedelta(days=int(float(adate)))).isoformat()
        adate = (adate or DEFAULT_DATE)[:10]

        title = f"{cve} ({comp}@{ver})"[:255]
        desc = f"组件: {comp}@{ver}"
        if g('E'):
            desc += f"\nCVSS 级别: {g('E')}"
        if analysis and analysis not in ('-',):
            desc += "\n\n分析摘要: " + analysis
        desc += f"\n\n参考: https://nvd.nist.gov/vuln/detail/{cve}"

        solution_parts = []
        if analysis and analysis not in ('-',):
            solution_parts.append("Analysis: " + analysis)
        if fix and fix not in ('-', '---'):
            solution_parts.append("Fix plan: " + fix)
        solution = "\n\n".join(solution_parts) if solution_parts else None

        sqls.append(
            "INSERT INTO vulnerabilities "
            "(title, description, severity, status, cve_id, cvss_score, cvss_level, "
            "epss_score, kev, verification_status, affected_systems, solution, "
            "discovered_date, asset_id, project_id, reporter_id, assignee_id, "
            "created_time, updated_time, deleted) VALUES ("
            f"{esc(title)}, {esc(desc)}, {esc(band or 'MEDIUM')}, 'OPEN', {esc(cve)}, "
            f"{score if score is not None else 'NULL'}, {esc(level)}, "
            f"{epss if epss is not None else 'NULL'}, {kev}, "
            f"{esc(affected) if affected else 'NULL'}, "
            f"{esc(f'依赖组件 {comp}@{ver}')}, {esc(solution) if solution else 'NULL'}, "
            f"'{adate}', {ASSET_ID}, {PROJECT_ID}, {REPORTER_ID}, "
            f"{owner if owner else 'NULL'}, NOW(6), NOW(6), 0);")

    print(f"解析 {len(data)} 行，跳过 {skip} 行（非 CVE），生成 {len(sqls)-3} 条 INSERT")
    # 直插绕过了 API 的统计联动，末尾刷新资产漏洞计数
    sqls.append("UPDATE assets a SET vulnerability_count = "
                "(SELECT COUNT(*) FROM vulnerabilities v WHERE v.asset_id = a.id AND v.deleted = 0);")

    # 分批执行
    batch, total = [], 0
    for i, s in enumerate(sqls):
        batch.append(s)
        if len(batch) >= 500 or i == len(sqls) - 1:
            r = subprocess.run(COMPOSE, input='\n'.join(batch), capture_output=True, text=True)
            if r.returncode != 0 or 'ERROR' in r.stderr:
                print('SQL 错误:', r.stderr[:500])
                sys.exit(1)
            total += len(batch)
            batch = []
    print(f"执行完成，共 {total} 条语句")


if __name__ == '__main__':
    main(sys.argv[1] if len(sys.argv) > 1 else 'Vulnerability Analysis CRA.xlsx')
