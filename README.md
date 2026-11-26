# Landfall（见屿）

多媒体内容展示社区系统（Spring Boot 3 + Java 21 模块化单体后端；前端工作区规划见 [docs/specs/design-proposal.md](./docs/specs/design-proposal.md)）。

## 当前仓库结构

```text
landfall/
├── backend/                 # Java 21 + Spring Boot 3.4.5 后端（内置 Maven Wrapper、Flyway 迁移、MyBatis-Plus）
├── docs/                    # ARCHITECTURE.md、standards/、agents/、specs/
├── scripts/                 # 仓库校验脚本（check-docs.py、check-migrations.py）
├── docker-compose.yml       # 本地 PostgreSQL 16 + Redis 7（含健康检查）
├── .env.example             # 本地环境变量模板
├── Makefile                 # 本地与 CI 复用的统一任务入口
├── .pre-commit-config.yaml  # 提交前门禁（detect-secrets、格式、Checkstyle、文档与迁移防篡改校验）
└── .github/workflows/ci.yml # GitHub Actions: 快速检查 + 真实 PG 16 / Redis 7 集成测试
```

## 环境要求

- **Java 21**（通过 `backend/mvnw` 构建，无需全局安装 Maven）
- **Docker Compose**（本地 PostgreSQL 16 与 Redis 7）
- **Python 3**、**`pre-commit` >= 3.2.0** 与 **GNU `make`**（Windows 环境建议使用 WSL 或安装 `make`）

## 快速启动

```bash
cp .env.example .env
make setup-hooks      # 安装本地 pre-commit 钩子
make up               # 启动本地 PostgreSQL 16 与 Redis 7 并等待健康检查就绪
make backend-run      # 启动后端服务（默认端口 8080，可通过 SERVER_PORT 覆盖）
```

启动后验证：
- 健康检查：`GET http://localhost:8080/api/v1/public/health`
- OpenAPI 3 文档：`GET http://localhost:8080/v3/api-docs` / `http://localhost:8080/swagger-ui.html`

## 常用校验命令

```bash
make check               # 文档校验 + Flyway 迁移防篡改 + 后端免容器全量 verify（单测、ArchUnit、打包、Spotless、Checkstyle）
make check-migrations    # 单独执行 Flyway 命名与历史迁移防篡改检查
make backend-it          # 连接本地 Docker PostgreSQL 16 + Redis 7 跑真库集成测试（动态临时库 + Redis DB 15，不污染 landfall 库）
make backend-fmt         # 自动执行 Spotless Java 格式化
make backend-style-check # 仅执行 Spotless 检查 + Checkstyle 静态检查
make backend-verify      # 仅运行后端免容器全量 verify
make down                # 停止本地 Docker 容器
pre-commit run --all-files
```

## 关键文档

- 当前代码树架构：[docs/ARCHITECTURE.md](./docs/ARCHITECTURE.md)
- 全局编码规范：[CODING_STANDARDS.md](./CODING_STANDARDS.md)
- 领域统一词表：[GLOSSARY.md](./GLOSSARY.md)
- 后端规范与契约：[docs/standards/java/code.md](./docs/standards/java/code.md)、[docs/standards/java/testing.md](./docs/standards/java/testing.md)、[docs/standards/api-contract.md](./docs/standards/api-contract.md)
- 总体规划纲要：[docs/specs/design-proposal.md](./docs/specs/design-proposal.md)
