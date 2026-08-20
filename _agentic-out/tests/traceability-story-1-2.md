# Story 1.2 验收追踪

| AC | 实现 | Task | 测试/证据 | 需求变更日志 |
|---|---|---|---|---|
| AC1 有效申请原子创建 | Controller、UseCase、三表事务、待审核结果页 | 1–5 | MySQL Testcontainers 成功事务；Web 成功与中国标准时间显示 | — |
| AC2 字段校验与输入恢复 | 先规范化后校验、Unicode code point 长度、统一字段错误、输入恢复 | 1、3–6 | 空体/畸形/类型/字段测试；Web 字段焦点与畸形 details 测试 | 2026-08-14 长密码哈希修正 |
| AC3 重复与并发提交安全 | 数据库唯一约束、稳定冲突确认与事务回滚 | 2、3、6 | 重复/并发 Testcontainers 测试、三表计数 | — |
| AC4 敏感数据保护 | Argon2、AES-256-GCM、独立指纹密钥、通用环境配置失败关闭 | 2、3、6 | password matches、密文/日志无明文、非法密钥测试 | 2026-08-14 长密码哈希修正 |
| AC5 键盘与 200% 缩放可用 | 标签/ARIA、错误摘要双向链接、视觉顺序焦点、单列响应布局 | 5、6 | Web 焦点/重复提交/axe-core；640×720 等效 200% 视口实测；用户确认物理 Tab/Shift+Tab/Enter 验收通过 | 2026-08-16 最终复审实施修正 |
| AC6 契约、时间与请求标识一致 | OpenAPI 生成客户端、Request ID、Redis 双额度、可信代理、413/415/429/503 | 1、4、6 | 契约正反例/lint；Redis Testcontainers；分块体、代理、额度分流、失败关闭测试；前端超时/非法响应/CST 测试 | 2026-08-16 匿名注册分布式防滥用 |

结论：AC1–AC6 均具备完整 AC→Task→测试/人工验收证据链；所有行为变更与实施修正均映射至 Story 需求变更日志。

2026-08-16 产物同步复核：本矩阵已在 Epics/Readiness、Sprint、Story 与 Automation 更新后刷新，用于最终 artifact reconcile。

2026-08-16 完成复核：Story 1.2 已经生命周期工具转为 done；AC1–AC6 无未关闭证据缺口，本矩阵最终状态为 complete。
