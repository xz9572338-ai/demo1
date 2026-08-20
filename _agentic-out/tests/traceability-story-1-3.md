---
artifact_kind: traceability
source_story: _agentic-out/implementation/stories/1-3-客户建立并终止安全登录会话.md
status: complete
date: 2026-08-17
validated_at: 2026-08-17T14:17:00+08:00
gate: PASS
---

# Story 1.3 需求追踪矩阵

> 代码审查发现的 AC2、AC3、AC4、AC5、AC6、AC7、AC8 证据缺口已修复并重新验证。
> 2026-08-17T14:17:00+08:00 完成第二轮独立复审与全量回归，映射与质量门结论为 PASS。

| AC | 实现 | 测试 | 结果 |
|---|---|---|---|
| AC1 | 会话契约、认证用例、Redis Session、安全 Cookie、会话固定防护 | 三状态、Secure/HttpOnly/Strict、Session ID 轮换及旧 ID 失效 | PASS |
| AC2 | `SessionStatusRefreshFilter`、`SessionGuard`、默认拒绝授权规则 | PENDING/REJECTED 真实会话绕过前端返回 403，最新状态刷新 | PASS |
| AC3 | `DELETE /session`、Redis 会话失效 | 双会话、Cookie 清除、旧 Cookie 失效且另一会话有效 | PASS |
| AC4 | 账号滑动窗口、原子租约及锁定 | 精确 5 次、到期、成功清零及未知/已知账号不可枚举比较 | PASS |
| AC5 | IP 独立滑动窗口、可信代理与地址规范化 | 精确 20 次、到期、可信代理及 IPv4-mapped IPv6 回归 | PASS |
| AC6 | Redis 关键路径失败关闭、统一 503 | acquire/commit/abort；Spring Session create/read/delete 过滤器路径 | PASS |
| AC7 | 登录表单、会话守卫、焦点与响应式组件 | 20 个前端测试，320/390、200% reflow、Tab/Enter、typecheck/build | PASS |
| AC8 | OpenAPI、生成客户端、统一错误、规范 UUID 和 Request ID | Redocly lint、运行时校验、契约及集成断言 | PASS |

质量门结论：PASS。代码审查修复已形成可执行证据；因用户要求最终统一提交，本 Story 暂保持 `review`，待统一提交时关闭。
