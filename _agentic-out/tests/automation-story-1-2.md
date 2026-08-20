# Story 1.2 自动化验证证据

- 日期：2026-08-16
- Story：`1-2-客户提交企业入驻申请`
- 结论：通过

## 验证覆盖

| 验收范围 | 自动化证据 | 结果 |
|---|---|---|
| 原子创建企业、账号、待审核申请 | MySQL 8.4 Testcontainers；成功、重复、并发、回滚与无孤立记录断言 | 通过 |
| 输入与契约 | 先规范化后校验、Unicode code point 边界、空体、畸形 JSON、字段类型、手机号 OpenAPI 正反例 | 通过 |
| 密码与手机号保护 | Argon2 长 Unicode 密码 `matches`；AES-256-GCM；独立指纹密钥；通用环境密钥注入与失败关闭 | 通过 |
| 匿名安全边界 | CSRF、方法授权、Redis 双额度、可信代理、16 KiB 已知/未知长度请求、413/415/429/503 | 通过 |
| 错误与诊断 | 400/403/409/413/415/429/500/503 统一模型；平台生成唯一 Request ID；畸形 details 容错 | 通过 |
| Web 注册体验 | 成功/失败、输入恢复、重复提交、非 JSON、超时/取消、非法 201、RFC3339 与中国标准时间显示 | 通过 |
| 可访问性 | required/ARIA、错误摘要与字段链接、成功/错误焦点、axe-core 零违规；640×720 等效 200% 视口无横向溢出；Tab/Shift+Tab/Enter 人工验收 | 通过 |

## 执行记录

- Maven `verify`：通过；26 项测试零失败，使用 MySQL 8.4 与 Redis 8.4 Testcontainers，并完成 Flyway 与 ArchUnit 验证。
- Web `typecheck`：通过。
- Web Vitest：8 项通过。
- Web production build：通过。
- OpenAPI lint：3 份契约通过，零错误。
- OpenAPI 客户端生成：通过，生成文件为 `apps/web/src/generated/api/console-v1.ts`。

## 浏览器运行时证据

- 2026-08-16 使用本地页面 `http://127.0.0.1:5173/`，将 1280 CSS 宽度按 200% 等效为 640×720 视口；实测 `scrollWidth = clientWidth = 625`，无横向溢出。
- 企业名称、联系人、手机号、账号、密码、显示密码、提交按钮 7 个关键控件在等效视口均可见；DOM 焦点候选顺序与视觉顺序一致，且均未设置负 `tabindex`。
- 2026-08-16：用户确认物理 Tab、Shift+Tab 与 Enter 键盘验收通过。

## 发布前人工复核入口

- 在 Chrome/Edge 最近两个主版本把页面缩放至 200%，确认注册页单列重排，字段、错误摘要和提交按钮无水平遮挡。
- 仅使用 Tab、Shift+Tab 与 Enter，按企业名称→联系人→手机号→账号→密码→显示密码→提交的顺序完成操作；错误时确认焦点进入首个错误字段，成功时进入结果标题。
- 200% reflow 已有浏览器运行时证据；物理 Tab/Shift+Tab/Enter 已由用户人工验收通过。自动化同时覆盖程序化标签、焦点结果、ARIA 关联、重复提交和 axe-core。

## 产物同步

- 2026-08-16：在 Story 与 Sprint 语义复核后刷新本证据，供最终 artifact reconcile 使用。
- 2026-08-16：用户确认物理键盘验收通过；生命周期工具已完成 Story 1.2 `review → done` 原子迁移，本证据最终状态为 complete。
