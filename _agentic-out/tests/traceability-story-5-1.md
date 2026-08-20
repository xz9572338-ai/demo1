# Story 5.1 追踪矩阵

日期：2026-08-18
门禁：PASS

| 验收标准 | 实现证据 | 测试证据 | 结果 |
|---|---|---|---|
| AC1–AC2 凭证、签名与规范化 | `SandboxCredentialVerifier`、`OpenApiCanonicalRequest` | canonical/service unit + integration | PASS |
| AC3 时间窗与 nonce | `OpenApiAuthenticationService`、`OpenApiRedisGuard` | 边界、重放、真实 Redis 并发 | PASS |
| AC4 非枚举与持续授权 | dummy HMAC、`ApprovedPermissionScope` | 未知/错误签名等价、权限拒绝 | PASS |
| AC5 统一错误与 Request ID | `OpenApiSecurityFilter`、`RequestIdFilter` | DB 故障穿透外层过滤器 | PASS |
| AC6 固定限流 | Redis Lua 令牌桶 | 前 20 成功、第 21 次 429 | PASS |
| AC7 执行顺序与副作用 | 固定签名→时间→nonce→授权→限流顺序 | 签名/时间不占 nonce，授权拒绝占 nonce 不占配额 | PASS |
| AC8 安全链与错误隔离 | 三条有序 `SecurityFilterChain` | Cookie 不代替签名、未知路径拒绝 | PASS |
| AC9 首发范围 | 仅 SANDBOX 路径匹配 | `/openapi/v1/**` 未启用并拒绝 | PASS |

全量回归：71/71 通过；无失败、错误或跳过。
