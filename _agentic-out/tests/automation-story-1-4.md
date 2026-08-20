# Story 1.4 自动化验证

- 日期：2026-08-17
- 状态：PASS
- 后端：`mvnw.cmd test`，53 项通过；聚焦 `ConsoleSessionIntegrationTest` 15 项通过。
- 前端：Vitest 29 项通过；TypeScript 类型检查与 Vite 生产构建通过。
- 契约：console/openapi/sandbox 三份 OpenAPI lint 通过。
- 覆盖：本人状态查询、三状态、审核后下一请求生效、匿名/账号缺失、跨账号隔离、数据库故障 503、持续授权、终态审计约束、事务回滚、状态页错误重试/退出与首发范围边界。
