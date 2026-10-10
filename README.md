# VulnArk 漏洞管理平台

VulnArk 是一个专注于漏洞管理和资产安全的综合平台，旨在帮助企业和组织有效地管理、跟踪和修复安全漏洞。

## 技术栈

- **前端**：Vue 3 + TypeScript + Vite + Arco Design UI + Pinia + Echarts
- **后端**：Java 17 + Spring Boot 3.2 + Spring Security + Spring Data JPA
- **数据库**：MySQL 8.0
- **数据库迁移**：Flyway（schema 与初始数据版本化管理，见 `backend/src/main/resources/db/migration/`）
- **认证**：JWT Token（HS512，密钥经环境变量注入并启动校验）
- **API文档**：SpringDoc OpenAPI
- **部署**：Docker Compose（MySQL + 后端 + Nginx 前端三容器）

## 已实现功能

### 用户认证与管理
- 用户登录与注册
- 基于JWT的认证授权
- 用户权限管理（管理员、项目经理、安全分析师、普通用户）
- 用户信息管理与密码重置

### 资产管理
- 资产创建、编辑、删除和查询
- 资产分类（服务器、工作站、网络设备、数据库等）
- 资产重要性评级（低、中、高、关键）
- 资产状态管理（活跃、非活跃、维护中、已退役）
- 资产详情查看与批量操作

### 漏洞管理
- 漏洞创建、编辑、删除和查询
- 漏洞严重程度分级（信息、低危、中危、高危、严重）
- 漏洞状态跟踪（待处理、处理中、已解决、已关闭、重新打开）
- 验证状态管理（待验证、已验证、误报）
- 漏洞分配与责任人管理（分配后自动进入"处理中"）
- 漏洞批量操作（批量分配、批量删除）
- 漏洞详情与CVE关联
- 漏洞-资产关联：按资产筛选、资产页下钻查看该资产漏洞（`assetId` 渐进式关联）
- Dependency-Track 漏洞批量导入（见 `scripts/import_to_vulnarkplus.py`）

### 项目管理
- 项目创建与管理
- 项目成员分配
- 项目资产关联

### 数据统计与可视化
- 仪表盘概览
- 漏洞统计与趋势分析
- 资产安全状态统计

## 扩展功能模块（代码已实现，未经生产验证）

以下模块前后端代码均已就绪（Controller + 页面），但尚未在真实环境充分验证，使用前建议先行测试：

### 扫描管理
- 安全扫描任务创建与执行
- 扫描结果分析与导入
- 扫描模板管理

### 资产发现
- 网络资产自动发现
- 资产指纹识别
- 资产变更监控

### 资产依赖分析
- 资产依赖关系可视化
- 依赖风险分析
- 影响路径分析

### 基线检查
- 安全基线合规检查
- 基线检查项管理
- 合规报告生成

## 安装与使用

### 方式一：Docker 部署（推荐）

一条 compose 拉起完整服务（MySQL + Spring Boot 后端 + nginx 前端），首次启动自动建表并初始化默认账号。

```bash
git clone https://github.com/data-tracker/VulnArk.git
cd VulnArk
cp .env.example .env
vi .env        # 必填两项，见下表
docker compose up -d --build
```

访问 `http://<主机IP>:8080`（端口由 `.env` 中 `VP_PORT` 决定）。

**环境变量（.env）**

| 变量 | 必填 | 说明 |
|---|---|---|
| `JWT_SECRET` | ✅ | JWT 签名密钥，≥64 字节，生成：`openssl rand -base64 64`。未设置或过短将拒绝启动 |
| `MYSQL_ROOT_PASSWORD` | ✅ | MySQL root 密码，≥6 位 |
| `VP_PORT` | 可选 | 前端对外端口，默认 8080 |
| `TZ` | 可选 | 时区，默认 Asia/Shanghai |

**默认账号**（由 Flyway 迁移自动创建，密码均为 `Admin123456`，**首次登录后请立即修改**）

| 用户名 | 角色 |
|---|---|
| admin | ADMIN（管理员） |
| manager | MANAGER |
| analyst | ANALYST |
| viewer | VIEWER（只读） |

**数据库迁移说明**：schema 与初始数据由 [Flyway](backend/src/main/resources/db/migration/) 版本化管理——全新库自动执行全部迁移；从旧版（`ddl-auto=update` 时代）升级时自动 baseline，存量数据零丢失。日常升级只需 `git pull && docker compose up -d --build`。

**常用运维命令**

```bash
docker compose logs -f backend     # 跟踪后端日志
docker compose down                # 停止（保留数据）
docker compose down -v             # 停止并清空所有数据（慎用）
```

### 方式二：本地开发运行

环境要求：Java 17+、Node.js 16+、MySQL 8.0+

```bash
# 后端（需自行准备 MySQL，并通过环境变量注入连接信息与 JWT_SECRET）
cd backend
JWT_SECRET=$(openssl rand -base64 64) SPRING_DATASOURCE_URL=jdbc:mysql://localhost:3306/vulnark ./mvnw spring-boot:run

# 前端
cd frontend
npm install
npm run dev
```

### 登录

见 Docker 部署一节的默认账号表（密码 `Admin123456`，登录后请修改）。

## 项目截图

<img width="1473" alt="dashboard" src="https://github.com/user-attachments/assets/e21add73-679d-42f0-93b1-d193c695e899" />
<img width="1474" alt="vulnerabilities" src="https://github.com/user-attachments/assets/bd3a862b-a523-4a5a-9432-bcf435027e3b" />
<img width="1467" alt="assets" src="https://github.com/user-attachments/assets/039099ca-ca75-41e7-9717-a765187186d1" />

## 项目结构

```
VulnArk/
├── docker-compose.yml      # 三容器编排（MySQL + 后端 + Nginx 前端）
├── .env.example            # 环境变量模板（JWT_SECRET / 数据库密码等）
├── scripts/                # 辅助脚本（Dependency-Track 漏洞导入等）
├── backend/                # 后端代码
│   ├── Dockerfile          # Maven 多阶段构建
│   ├── src/main/java/com/vulnark/
│   │   ├── controller/     # REST 控制器
│   │   ├── service/        # 业务逻辑（impl 实现、detection 检测引擎）
│   │   ├── repository/     # Spring Data JPA 数据访问
│   │   ├── entity/         # JPA 实体类
│   │   ├── dto/            # 数据传输对象
│   │   ├── config/         # 配置类（SecurityConfig 等）
│   │   ├── security/       # JWT 认证（密钥启动校验）
│   │   ├── baseline/       # 基线核查引擎
│   │   ├── common/         # 统一响应结构
│   │   ├── exception/      # 业务异常
│   │   └── util/           # 工具类
│   ├── src/main/resources/
│   │   ├── application.yml # 应用配置（环境变量可覆盖）
│   │   └── db/migration/   # Flyway 版本化迁移（V1 基线 / V2 初始数据）
│   └── pom.xml             # Maven 配置
└── frontend/               # 前端代码
    ├── Dockerfile          # Node 构建 + Nginx 运行时
    ├── nginx.conf          # SPA 回退 + /api 反代
    ├── src/
    │   ├── api/            # API 调用封装
    │   ├── views/          # 页面组件
    │   ├── stores/         # Pinia 状态管理
    │   ├── router/         # 路由配置
    │   ├── types/          # TypeScript 类型定义
    │   └── styles/         # 样式
    ├── package.json
    └── vite.config.ts
```

## 关于本 Fork

本仓库 fork 自 [cloudoshk/VulnArk](https://github.com/cloudoshk/VulnArk)（上游已停止维护），在此基础上进行了二次开发与工程化改造，主要包括：

- Docker Compose 容器化部署（含国内镜像源加速与构建缓存）
- 安全加固：JWT 密钥环境变量注入与启动校验，移除调试端点
- 引入 Flyway 版本化数据库迁移，替代 `ddl-auto=update`
- 漏洞-资产关联（`assetId` 渐进式）与按资产筛选
- 修复多项缺陷（分页/筛选空数据、中文乱码、资产下钻过滤、文档失实等）
- Dependency-Track 漏洞导入脚本

变更明细见 [提交历史](https://github.com/data-tracker/VulnArk/commits/dev)。

## 开发团队

VulnArk 由安全开发团队开发和维护。

## 联系作者
 
data-tracker@outlook.com（本 Fork 维护者；原作者：vpsanta3@gmail.com）

## 许可证

[MIT License](LICENSE)
