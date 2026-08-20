# Story 3.1 自动化验证

## 结论

- Web Vitest：36 项通过，覆盖未申请、通过、驳回、提交后待审、500/501 Unicode 码点、POST 成功后刷新失败、Request ID、重试及键盘/无障碍。
- Web typecheck/build：通过。
- 后端 Maven：60 项通过；MySQL/Testcontainers 覆盖三项目录、原子/并发提交、null/Unicode/空白边界、驳回重提历史、默认拒绝、跨企业查询与提交隔离、受控批准/驳回/同人/空白/零行/投影失败回滚，以及 HTTP 400/401/403/409/503 与内部字段不泄漏。
- OpenAPI：`console-v1.yaml`、`openapi-v1.yaml`、`sandbox-v1.yaml` lint 通过。
- 模块边界与 `git diff --check`：通过。

## 执行命令

- `apps/api/mvnw.cmd test`
- `npm test`、`npm run typecheck`、`npm run build`（`apps/web`）
- `npm run validate:openapi`（仓库根目录）
- `git diff --check`

## 已知非阻断输出

- jsdom 对 Canvas `getContext()` 未实现的提示由 axe 触发，不影响 35 项断言。
- Redis 故障映射测试会制造预期的 Lettuce 重连警告，断言和构建结果均通过。
