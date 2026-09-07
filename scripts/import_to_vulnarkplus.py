#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Dependency-Track → VulnArk+ (cloudoshk 版) 漏洞导入工具

与 SantaVp3 版的差异：
- 登录端点 /api/auth/login，token 在 data.token
- 漏洞必须挂在项目下（projectId 必填），affectedSystems 记录组件信息
- 严重度为 5 级枚举 INFO/LOW/MEDIUM/HIGH/CRITICAL，与 DTrack 一一对应
- 分页 page 从 0 开始，响应为 Spring 分页结构 data.content[]

用法:
  python3 import_to_vulnarkplus.py --dry-run
"""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from datetime import date

SEVERITY_MAP = {
    "CRITICAL": "CRITICAL",
    "HIGH": "HIGH",
    "MEDIUM": "MEDIUM",
    "MODERATE": "MEDIUM",
    "LOW": "LOW",
    "MINOR": "LOW",
    "INFO": "INFO",
    "UNASSIGNED": "MEDIUM",
}
DEFAULT_SEVERITY = "MEDIUM"
TODAY = date.today().isoformat()


def http_json(method, url, headers=None, body=None, timeout=60):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(url, data=data, method=method)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(req, timeout=timeout) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:300]
        raise RuntimeError(f"HTTP {e.code} {url}: {detail}") from e


class DTrack:
    def __init__(self, base, key):
        self.base = base.rstrip("/")
        self.h = {"X-Api-Key": key, "Accept": "application/json"}

    def find_project(self, name):
        projects, _ = http_json("GET", f"{self.base}/api/v1/project", self.h), None
        low = name.lower()
        return [p for p in projects if low in p.get("name", "").lower()]

    def components(self, uuid):
        comps, offset = [], 0
        while True:
            batch = http_json(
                "GET",
                f"{self.base}/api/v1/component/project/{uuid}?limit=500&offset={offset}",
                self.h,
            )
            if not batch:
                break
            comps.extend(batch)
            if len(batch) < 500:
                break
            offset += 500
        return comps

    def vulns(self, comp_uuid):
        return http_json(
            "GET", f"{self.base}/api/v1/vulnerability/component/{comp_uuid}", self.h
        )


class VulnArkPlus:
    def __init__(self, base, user, pw):
        self.base = base.rstrip("/")
        r = http_json(
            "POST",
            f"{self.base}/api/auth/login",
            body={"username": user, "password": pw},
        )
        if r.get("code") != 200:
            sys.exit(f"登录失败: {r}")
        self.token = r["data"]["token"]

    def _h(self):
        return {"Authorization": f"Bearer {self.token}"}

    def find_or_create_project(self, name):
        r = http_json("GET", f"{self.base}/api/projects?page=0&size=100", self._h())
        for p in (r.get("data") or {}).get("content", []):
            if p.get("name", "").strip().lower() == name.strip().lower():
                return p["id"]
        r = http_json(
            "POST",
            f"{self.base}/api/projects",
            self._h(),
            body={
                "name": name,
                "description": f"由 Dependency-Track 项目 {name} 同步",
                "status": "ACTIVE",
                "priority": "HIGH",
            },
        )
        if r.get("code") != 200:
            sys.exit(f"创建项目失败: {r}")
        return r["data"]["id"]

    def existing_keys(self, project_id):
        exist, page = set(), 0
        while True:
            r = http_json(
                "GET",
                f"{self.base}/api/vulnerabilities?page={page}&size=100&projectId={project_id}",
                self._h(),
            )
            items = (r.get("data") or {}).get("content", [])
            if not items:
                break
            for v in items:
                if v.get("cveId"):
                    exist.add(v["cveId"].strip())
                exist.add((v.get("title") or "").strip())
            total = (r.get("data") or {}).get("totalElements", len(items))
            if (page + 1) * 100 >= total:
                break
            page += 1
        return exist

    def create(self, req):
        return http_json(
            "POST", f"{self.base}/api/vulnerabilities", self._h(), body=req
        )


def build_req(v, comp, project_id):
    vid = (v.get("vulnId") or "").strip()
    source = (v.get("source") or "").strip()
    is_cve = vid.upper().startswith("CVE-")
    display = vid if is_cve else (f"{source}:{vid}" if vid else "UNKNOWN")
    name, ver = comp.get("name", "?"), comp.get("version", "?")
    sev = SEVERITY_MAP.get((v.get("severity") or "").upper(), DEFAULT_SEVERITY)
    cvss = v.get("cvssV3BaseScore") or v.get("cvssV2BaseScore")
    cvss = round(float(cvss), 1) if cvss is not None else None

    desc = f"组件: {name}@{ver}\n来源: {source}"
    if v.get("cwe"):
        desc += f"\nCWE: {v['cwe']}"
    if v.get("description"):
        desc += "\n\n" + v["description"].strip()
    if is_cve:
        desc += f"\n\n参考: https://nvd.nist.gov/vuln/detail/{vid}"

    fix = (v.get("recommendation") or "").strip() or f"升级 {name}（当前 {ver}）至已修复版本"

    return {
        "title": f"{display} ({name}@{ver})",
        "description": desc,
        "severity": sev,
        "status": "OPEN",
        "cveId": vid if is_cve else display[:100],
        "cvssScore": cvss,
        "projectId": project_id,
        "affectedSystems": f"依赖组件 {name}@{ver}",
        "solution": fix,
        "discoveredDate": TODAY,
    }


def main():
    ap = argparse.ArgumentParser(description="Dependency-Track → VulnArk+ 导入")
    ap.add_argument("--dtrack-url", default=os.environ.get("DTRACK_URL", ""))
    ap.add_argument("--dtrack-key", default=os.environ.get("DTRACK_API_KEY", ""))
    ap.add_argument("--dtrack-project", default=os.environ.get("DTRACK_PROJECT", ""))
    ap.add_argument("--vulnark-url", default=os.environ.get("VULNARK_URL", "http://localhost:18081"))
    ap.add_argument("--vulnark-user", default=os.environ.get("VULNARK_USER", "admin"))
    ap.add_argument("--vulnark-pass", default=os.environ.get("VULNARK_PASSWORD", ""))
    ap.add_argument("--project", default="", help="VulnArk+ 项目名（默认与 DTrack 项目同名）")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()
    for k, v in [
        ("--dtrack-url", args.dtrack_url),
        ("--dtrack-key", args.dtrack_key),
        ("--dtrack-project", args.dtrack_project),
        ("--vulnark-pass", args.vulnark_pass),
    ]:
        if not v:
            ap.error(f"缺少参数: {k}")

    dt = DTrack(args.dtrack_url, args.dtrack_key)
    projects = dt.find_project(args.dtrack_project)
    if not projects:
        sys.exit("DTrack 中找不到该项目")

    vp = VulnArkPlus(args.vulnark_url, args.vulnark_user, args.vulnark_pass)
    pname = args.project or args.dtrack_project
    pid = vp.find_or_create_project(pname)
    print(f"VulnArk+ 项目 “{pname}” → id={pid}")

    existing = vp.existing_keys(pid)
    created = skipped = errors = 0
    for proj in projects:
        comps = dt.components(proj["uuid"])
        print(f"DTrack 项目 {proj['name']}: {len(comps)} 个组件")
        for comp in comps:
            try:
                vulns = dt.vulns(comp["uuid"])
            except RuntimeError as e:
                print(f"  ✗ 组件 {comp.get('name')}: {e}")
                errors += 1
                continue
            seen = {}
            for v in vulns:
                key = v.get("vulnId", "")
                seen.setdefault(key, v)
            for v in seen.values():
                req = build_req(v, comp, pid)
                if req["cveId"] in existing or req["title"] in existing:
                    skipped += 1
                    continue
                if args.dry_run:
                    print(f"  [dry-run] {req['title']} ({req['severity']})")
                    created += 1
                    continue
                r = vp.create(req)
                if r.get("code") == 200:
                    created += 1
                    existing.add(req["cveId"])
                    existing.add(req["title"])
                    print(f"  ✓ {req['title']}")
                else:
                    errors += 1
                    print(f"  ✗ {req['title']}: {r.get('message')}")

    print(f"\n完成。新建 {created}，跳过 {skipped}，失败 {errors}")


if __name__ == "__main__":
    main()
