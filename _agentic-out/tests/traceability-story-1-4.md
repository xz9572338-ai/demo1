# Story 1.4 追踪矩阵

| AC | 实现 | 验证 | 结果 |
|---|---|---|---|
| AC1 | 会话主体状态 API | 后端集成测试、OpenAPI lint | PASS |
| AC2 | 三状态页面 | Vitest 三状态断言 | PASS |
| AC3 | V4 字段、受控审核 SQL | 状态更新后下一请求与 DB 约束测试 | PASS |
| AC4 | SessionStatusRefreshFilter 与服务端授权 | 受限 API 403、通过后 dashboard | PASS |
| AC5 | 安全入口与统一错误 | 401、隔离、既有 503 故障映射测试 | PASS |
| AC6 | 线下支持边界 | 无重提入口断言与 runbook | PASS |
| AC7 | 语义/响应式基线 | 既有键盘、320/390px、200% 契约及构建 | PASS |
