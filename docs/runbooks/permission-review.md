# 接口权限受控审核

业务产品提供申请 ID、`APPROVED|REJECTED`、客户可见原因/内部客户范围、操作人与独立复核人、UTC 审批时间。使用支持绑定参数的受控数据库客户端执行 `permission-review.sql`；不得绕过存储过程直接改表。

Flyway V10 安装的存储过程仅允许待审申请进入终态，并分别断言申请与当前权限投影各更新一行。任何参数、状态、行数或数据库约束不满足时都会 `SIGNAL`，异常处理器回滚整个事务。`permission_requests` 的每次重提都是新记录，其终态字段保存该次审核人、复核人、审批时间、结果与范围/原因，作为审核记录。调用成功时返回 `request_rows=1`、`permission_rows=1`；调用失败不得重试为直接 UPDATE，应先按 Request ID 排查。
