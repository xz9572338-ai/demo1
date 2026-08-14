# 轻量化开放平台

## 先决条件

- Java 21
- Node.js 22 LTS（`>=22.12 <23`）
- pnpm 11.19.0（通过 Corepack 或已安装命令）
- Docker Engine 与 Docker Compose v2

## 首次安装

```powershell
Copy-Item .env.example .env
pnpm install --frozen-lockfile
```

Linux/macOS 使用 `cp .env.example .env`。Windows 使用 `mvnw.cmd`，Linux/macOS 使用 `./mvnw`；两者都会下载并校验固定的 Maven 3.9.11，不需要全局 Maven。

## 本地启动

1. 确认已将 `.env.example` 复制为 `.env`（仅使用虚构本地凭据）。
2. 启动依赖：`docker compose --env-file .env -f deploy/compose/compose.local.yml up -d --wait`
3. 启动 API（Windows）：`mvnw.cmd -pl apps/api spring-boot:run -Dspring-boot.run.profiles=local`
   Linux/macOS 使用：`./mvnw -pl apps/api spring-boot:run -Dspring-boot.run.profiles=local`
4. 启动 Web：`pnpm dev:web`

验证 API：`http://localhost:8080/actuator/health`；Web：`http://localhost:5173`；数据中台模拟器：`http://localhost:18080/health.json`。

## 停止

`docker compose --env-file .env -f deploy/compose/compose.local.yml down`

## 构建与测试

Windows：

```powershell
mvnw.cmd verify
pnpm typecheck
pnpm test:web
pnpm build:web
pnpm validate:openapi
docker compose --env-file .env.example -f deploy/compose/compose.local.yml config --quiet
```

Linux/macOS 将首条命令替换为 `./mvnw verify`，其余命令相同。

Flyway 基线由 API 集成测试在空 MySQL Testcontainers 实例上执行并校验。

## CI 必需检查

仓库管理员需将 `API CI / verify`、`Web CI / verify` 和 `Contract CI / verify` 设为分支保护必需检查。

## 故障排查

- Java 版本错误：确认 `java -version` 为 21，并重新执行 Wrapper。
- Node 版本错误：执行 `node --version`，应满足 `.nvmrc` 和 `engines`。
- 端口占用：在 `.env` 中调整本地端口，不要修改共享默认值。
- 容器未就绪：运行 `docker compose ... ps` 查看健康状态和日志。
- 修改 MySQL 数据库名或凭据后，已有卷不会重新初始化。确认本地数据可丢弃后，执行 `docker compose --env-file .env -f deploy/compose/compose.local.yml down -v`，再重新启动；该命令会永久删除本项目的本地 MySQL/Redis 卷。

## 数据安全

本地 fixture 均为虚构数据。禁止向 Git、镜像、日志或前端静态资源写入真实客户资料、生产标识、密钥或签名。
