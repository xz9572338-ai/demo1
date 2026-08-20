# 沙箱 AppSecret 人工重置

仅由技术对接负责人在受控终端执行。先取得客户企业微信/邮件凭据和独立复核人；操作人与复核人不得相同。

先单独配置 `APP_SECRET_ENCRYPTION_KEY`（256 bit Base64）与 `APP_SECRET_ENCRYPTION_KEY_ID`，不得复用手机号加密密钥。以 `web-application-type=none` 启动一次性维护命令，并通过受保护的环境变量/启动参数提供：`application-id`、`reason`、`operator`、`checker`、`evidence`、`request-id`。每个工单使用唯一且稳定的 `request-id`；不确定命令是否成功时不得更换 ID 重试。设置 `open-platform.maintenance.secret-reset.enabled=true` 后执行。命令成功时仅向当前受控终端输出一次 `SANDBOX_APP_SECRET`；立即安全交付客户并清屏，不重定向到文件、不粘贴到工单正文、不保留终端录屏。

命令通过数据库行锁串行处理同一应用，并拒绝已处理的 `request-id`。对非 ACTIVE 应用、同人复核、缺失/超长元数据、零条或多条沙箱凭证、审计写入失败均失败并回滚。生产凭证签发属于 Story 5.3，禁止使用本命令替代生产准入。
