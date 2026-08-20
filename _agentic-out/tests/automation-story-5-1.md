# Story 5.1 自动化验证报告

日期：2026-08-18
结论：PASS

## 执行结果

- 后端全量：`apps/api/mvnw.cmd -q test`
- 结果：71 tests，0 failures，0 errors，0 skipped。
- 真实依赖：Testcontainers MySQL 8.4.7、Redis 8.4.0。

## 关键证据

- HMAC 规范化、常量时间比较、未知 AppID 等价错误语义。
- 时间戳边界、Redis nonce 原子占用及并发同 nonce 仅一次成功。
- 三个端点分别绑定获批权限及其客户范围，不合并不同权限范围。
- 固定令牌桶容量 20、每秒补充 1，连续第 21 次返回 429 与 `Retry-After`。
- 沙箱、控制台和默认拒绝安全链相互隔离；开放 API 依赖故障返回统一 `SERVICE_UNAVAILABLE`。
- ArchUnit 模块边界测试通过。

## 已知范围

- 本 Story 仅启用 `/sandbox/v1/**` 安全入口；不启用生产 `/openapi/v1/**`。
- 本 Story 不实现业务查询控制器，成功认证请求由测试端点验证可信上下文。
- 评审修复后的开放 API 聚焦单元与真实 MySQL/Redis 集成测试通过。
- Maven 后端全量：80 tests，0 failures，0 errors，0 skipped。
- 新增权限查询故障、凭证解密失败、Redis 脚本异常结果、安全日志单终态与 AC7 授权拒绝副作用证据。
