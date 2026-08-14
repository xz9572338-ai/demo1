---
artifact_kind: test_automation
source_story: ../implementation/stories/1-1-初始化可持续交付的项目基线.md
status: complete
date: 2026-08-14
---

# Story 1.1 自动化验证证据

## 验收标准映射

| AC | 自动化证据 | 结果 |
|---|---|---|
| AC1 应用骨架 | Maven reactor、TypeScript 类型检查、Vite 构建、ArchUnit 5 项规则 | 通过 |
| AC2 本地可运行性 | Testcontainers MySQL、真实 `/actuator/health` HTTP 断言、Compose `up --wait` 三服务健康检查 | 通过 |
| AC3 可重复构建 | 官方 Maven Wrapper 3.3.4 + Maven 3.9.11 SHA-256、`pnpm-lock.yaml`、Maven verify、前端 typecheck/test/build | 通过 |
| AC4 契约与迁移 | 三份 OpenAPI lint；MySQL 空库迁移、基线表/schema history 断言、重复 validate/migrate/validate | 通过 |
| AC5 CI 质量门槛 | API/Web/Contract workflow 均以失败退出；API/Web workflow 增加 Docker 镜像构建；`main` 严格 required checks | 通过 |

## 2026-08-14 执行结果

- `.\mvnw.cmd verify`：通过；6 tests，0 failures，0 errors。
- `pnpm typecheck`：通过。
- `pnpm test:web`：通过；1 test。
- `pnpm build:web`：通过。
- `pnpm validate:openapi`：通过；3 contracts，0 warnings/errors。
- `docker compose --env-file .env -f deploy/compose/compose.local.yml config --quiet`：通过。
- `docker compose --env-file .env -f deploy/compose/compose.local.yml up -d --wait`：通过；MySQL、Redis、数据中台模拟器均 healthy，端口仅绑定 `127.0.0.1`。
- `docker build`：当前开发机访问 Docker Hub token 端点超时，且本机没有 `eclipse-temurin:21-jre`、`node:22.23.0-alpine` 缓存，未能本地完成；对应构建已进入 API/Web CI，联网 runner 将作为失败门槛执行。

## GitHub 验收证据

- 仓库：[xz9572338-ai/demo1](https://github.com/xz9572338-ai/demo1)，基线提交 `afddd868b1277536021b5092a4da0f3432adf1d0`。
- `main` 已启用严格状态检查，要求 `API CI / verify`、`Web CI / verify`、`Contract CI / verify`，管理员不可绕过，禁止强推和删除，并要求会话已解决。
- 三项 Actions 在基线提交上均为 `success`：API run `31784835523`、Web run `31784835474`、Contract run `31784835487`。
- PR #1 首次合并验证发现默认 Job 名均为 `verify`，已将三个 Job 显式命名为保护规则要求的唯一上下文；以 PR 更新后的三项检查与实际合并结果作为最终门禁证据。
- PR #1 在三个唯一 required checks 成功后合并；PR #2 同样通过三项检查并删除 API 编码产生的 Windows 非法路径，本地 `main` 已可正常快进检出。

代码评审的本地修复及对应 Story 需求变更日志已于 2026-08-14 同步完成。

Story、Sprint 与 artifact manifest 已通过生命周期工具原子迁移为 `done` / `complete`。
