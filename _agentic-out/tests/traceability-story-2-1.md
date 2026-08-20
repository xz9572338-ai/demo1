# Story 2.1 需求追踪矩阵

日期：2026-08-18

| AC | 实现证据 | 验证证据 | 状态 |
|---|---|---|---|
| AC1 创建唯一应用与沙箱凭证 | `ApplicationController`、`ApplicationService`、V7 migration | `approvedCustomerCreatesOnlyOneApplicationAndSecretIsReturnedOnce`；OpenAPI lint | PASS |
| AC2 阻止重复创建 | `uk_applications_enterprise_id`、冲突异常映射、AppID 碰撞重试 | 顺序 409 与双线程并发创建仅一项成功 | PASS |
| AC3 未通过账号拒绝 | `SessionStatusRefreshFilter`、服务层实时 scope 校验 | API 状态漂移 403 与既有持续授权全量测试 | PASS |
| AC4 本企业查询且不回显明文 | account→enterprise 作用域查询；普通响应无 secret；空列表响应头含 Request ID | 双企业隔离、非空/空查询及 `X-Request-ID` 断言 | PASS |
| AC5 不信任客户端企业范围 | 请求仅允许 name/purpose；查询范围来自会话 accountId | 双企业应用列表隔离、OpenAPI lint、DTO 与模块边界测试 | PASS |
| AC6 一次性前端闭环 | 局部状态、beforeunload/popstate、复制 live region、确认剥离 | 创建/复制、复制失败、离开警告、确认清除、刷新只恢复安全视图 | PASS |
| AC7 失败回滚与无泄密 | 事务创建、统一 Request ID/错误、独立 AES-GCM 配置 | 凭证表不可用回滚、密文不含明文、AES 往返/篡改失败 | PASS |
| AC8 受控人工重置 | 行锁、乐观版本条件、requestId 唯一、非 HTTP runner、双人复核与审计 | 正常/重放/并发不同请求/元数据/零多行/审计失败回滚/单行输出测试 | PASS |
| AC9 键盘/响应式/敏感值边界 | 语义表单与按钮、focus/reflow、长密钥换行 | Web 34 项回归、应用键盘/200% 有效视口/axe/敏感 console 边界、typecheck、生产构建 | PASS |

结论：Story 2.1 的 AC1–AC9 均具备实现和可执行验证证据，可进入对抗性代码评审；代码评审 Clean 前不得置为 done。
