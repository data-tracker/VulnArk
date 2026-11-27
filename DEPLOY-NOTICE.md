# 生产机部署须知

> 本文件面向生产环境所在主机，主机地址与账号信息不写入仓库，
> 请向平台负责人索取。

> 本机运行的是**正式环境**。操作前请先读完本页。

## 发布方式：只走镜像导入，禁止在本机构建

本机**没有外网**，且缺少构建所需的基础镜像（maven / eclipse-temurin）。
因此 `docker compose build` 在本机**既跑不通、也不应该跑**。

正确的发布流程：

1. 在开发机上修改代码并构建镜像
2. `docker save` 导出 → `scp` 传到本机 → `docker load` 导入
3. 在本目录执行 `docker compose up -d`（**不带 `--build`**）
4. 核实部署确实生效：
   - `docker compose exec backend sh -c 'ls -la /app/app.jar'`（日期应为本次构建时间）
   - 前端：在 `assets/*.js` 中 grep 本次改动的新特征字符串

## 禁止的操作

| 命令 | 后果 |
|---|---|
| `docker compose up -d --build` | 用本机源码覆盖已导入的镜像 |
| `docker compose build` | 同上；且本机当前会因缺少基础镜像而失败 |
| `git checkout .` / `git reset --hard` / `git stash` | 丢弃本地改动；周报与导入脚本会受影响 |
| `git clean -fdx` | **会删除 `.env`**（含数据库密码），服务将无法启动 |

## 关于 Git

- 本机无法访问 GitHub，**不要**期待 `git pull` 能工作
- 仓库更新方式：开发机 `git bundle` → 传输 → 本机 `git fetch <bundle>`
- 开发机上的分支为 `dev`，合并到 `main` 后再同步到本机
- `.env` 被 Git 忽略，同步不会覆盖它，也不会入库

## 周报与导入脚本

- `scripts/weekly_report.py`：周报，**在本机执行**（数据在本机数据库）
- `scripts/import_cra_analysis.py`：Excel 导入
- 修改这两个脚本请走开发机 + Git 同步，不要在本机直接编辑

## 数据卷

MySQL 数据在卷 `vulnark-fork_vp-mysql-data`，上传文件在 `vulnark-fork_vp-uploads`。
容器最初由另一个已不存在的目录拉起，现由本目录的
`docker-compose.yml` 管理，**Compose 项目名同为 `vulnark-fork`**，因此共用上述数据卷与网络。
不要更改 compose 文件中的项目名或卷名，否则会挂到空的新卷上。
