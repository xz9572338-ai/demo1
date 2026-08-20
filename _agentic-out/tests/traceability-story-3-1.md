# Story 3.1 追踪矩阵

| AC | 实现 | 证据 | 结论 |
|---|---|---|---|
| AC1 | 固定三权限目录与企业应用归属 | 服务及 HTTP 测试断言固定顺序、三项状态、Request ID、无效/跨企业 applicationId | PASS |
| AC2 | 逐项申请、原因校验、原子写入与驳回重提 | 500/501 码点、null/空白、双项提交、驳回后新历史和零部分写入断言 | PASS |
| AC3 | 待审/已通过重复及并发拒绝 | MySQL 双事务首次并发、pending/approved 重复与 HTTP 409 code 断言 | PASS |
| AC4 | 当前状态、时间和客户可见驳回原因 | V10 批准/驳回后即时读取；Web 展示状态、提交/更新时间和驳回原因 | PASS |
| AC5 | 审批与当前投影同事务、审核记录及失败关闭 | 实际调用 V10 存储过程覆盖批准、驳回、同人、空白、零行、投影缺失回滚和终态约束 | PASS |
| AC6 | 默认拒绝与跨企业隔离 | approved false/true、A 对 B 查询/提交拒绝、B 数据不变及 HTTP 403 | PASS |
| AC7 | 状态文本、Unicode、键盘、错误恢复 | Vitest 36 项 + axe；POST 成功后 GET 失败仍保留待审状态、Request ID 与明确刷新入口 | PASS |
| AC8 | 无运营后台/撤权历史/字段审批/开放 API 越界 | 提交文件与 OpenAPI 仅新增权限控制台端点及受控审核过程 | PASS |
