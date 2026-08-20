# Story 4.1 追踪矩阵

| AC | 实现 | 验证 | 状态 |
|---|---|---|---|
| AC1 OpenAPI v1 | 两个根契约复用 `components/public-api.yaml` 的三路径、模型、响应和安全头 | 三份 Redocly lint；生产/沙箱结构差异为 0 | PASS |
| AC2 确定签名 | 文档与 fixture 固定七行待签名串、RFC 3986 查询、空体摘要、±5 分钟和 nonce | Node 表驱动规范化与固定向量 | PASS |
| AC3 跨语言向量 | JSON fixture、Node/Python/Java verifier | 三种实现产生 `e087…4818`；篡改、尾斜杠、大小写、换行和 `*` 编码边界 | PASS |
| AC4 三语言示例 | 页面加载构建生成的完整 cURL、Java 21、Python 3 标准库源码 | Java 编译、Python/Java verifier、静态安全扫描 | PASS |
| AC5 受保护文档 | `/api-docs`、SessionGuard、应用/权限入口、OpenAPI bundle no-store 加载 | APPROVED/PENDING/REJECTED/匿名/过期路由及资源失败重试 | PASS |
| AC6 可用性 | 语义结构、焦点、代码容器、资源格式失败、复制状态/错误/超时、响应式 CSS | 42 项前端回归、键盘/clipboard/axe/320–760px CSS 契约测试 | PASS |
| AC7 范围边界 | 静态资产与客户端展示，无开放 API 调用 | `rg` 范围检查、后端 60 项无回归 | PASS |
