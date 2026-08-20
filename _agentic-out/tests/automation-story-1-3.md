---
artifact_kind: automation
source_story: _agentic-out/implementation/stories/1-3-客户建立并终止安全登录会话.md
status: complete
date: 2026-08-17
validated_at: 2026-08-17T14:17:00+08:00
---

# Story 1.3 自动化验证报告

## 结果

代码审查发现的证据缺口已修复，并重新执行全部验证。

- 2026-08-17T14:17:00+08:00：第二轮独立复审修复后全量验证通过。

- 后端全量：47 tests，0 failures，0 errors；真实 MySQL 8.4 与 Redis 8.4 Testcontainers。
- 前端：20 tests，全部通过。
- TypeScript typecheck：通过。
- Vite production build：通过。
- OpenAPI lint：通过；登录 CSRF 端点的 4XX 响应已补齐。
- ArchUnit 模块边界：5 tests，全部通过。

## AC 追踪

| AC | 自动化证据 |
|---|---|
| AC1 | 三状态登录；SESSION `HttpOnly/Secure/SameSite=Strict`；重新认证轮换 Session ID 且旧 ID 失效 |
| AC2 | PENDING/REJECTED 真实会话直接访问非 allowlist API 返回 403；状态变化后下一请求刷新 |
| AC3 | 双独立 Redis 会话测试验证退出 A、清除 Cookie、旧 Cookie 失效且 B 仍有效 |
| AC4 | 精确 5 次、到期、成功清零；未知/已知账号稳定 code/message/details/retryable 一致 |
| AC5 | 精确 20 次、到期、可信代理与 IPv4-mapped IPv6 统一限流桶 |
| AC6 | 登录 acquire/commit/abort 故障；Spring Session create/read/delete 真实过滤器路径统一 503、Request ID 与避敏 |
| AC7 | 20 个前端测试覆盖焦点、Tab/Enter、标签关联、320/390px 与 200% reflow 契约 |
| AC8 | Redocly、生成客户端、规范 UUID 运行时校验、统一错误/Request ID 集成断言 |

## 命令

```text
cmd /c mvnw.cmd -pl apps/api test
corepack pnpm validate:openapi
corepack pnpm typecheck
corepack pnpm test:web
corepack pnpm build:web
```

## 已知非失败输出

- jsdom 报告未实现 Canvas `getContext()`，来自 axe 运行环境，不影响测试结果。
- Testcontainers 关闭 Redis 容器时 Lettuce 会输出短暂重连日志，测试结果仍为通过。
- Spring Boot 输出开发用默认密码提示，但应用业务登录完全由显式控制台会话端点和默认拒绝规则处理；不对外暴露默认登录页。
