# Story 4.1 自动化验证报告

- 日期：2026-08-18
- 结论：PASS

## 执行结果

| 验证 | 结果 | 证据 |
|---|---|---|
| OpenAPI 契约 | PASS | `pnpm validate:openapi`，console/production/sandbox 三份规范有效 |
| 生产/沙箱一致性与 Node 向量 | PASS | `pnpm validate:public-api` |
| Python 签名向量 | PASS | `python contracts/examples/python/verify_vector.py` |
| Java 21 签名向量/示例 | PASS | `javac` 后执行 `SigningVectorVerifier`；`SignedRequest` 使用 `OPEN_PLATFORM_DRY_RUN=true` 实际构建请求 |
| Python/cURL 示例 | PASS | Python 示例实际生成安全引用的 cURL 命令且不泄漏测试密钥；PowerShell cURL 示例使用 `OPEN_PLATFORM_DRY_RUN=1` 实际完成签名与参数组装 |
| 前端组件与无障碍回归 | PASS | Vitest 42/42；完整授权矩阵、资源格式校验/重试、复制成功/失败/超时、键盘与 axe 覆盖 |
| TypeScript | PASS | `pnpm typecheck` |
| Web 生产构建 | PASS | `pnpm build:web` |
| 后端完整回归 | PASS | Maven/Testcontainers 60/60，MySQL 8.4 + Redis 8.4 |
| Diff 质量 | PASS | `git diff --check` |

## 安全与范围检查

- 固定向量只含明确标注的不可部署测试凭证；验证器不打印 AppSecret。
- 文档功能目录不存在开放 API `fetch`；页面明确声明不会发送请求。
- 未新增网关、nonce 存储、限流运行逻辑、业务查询控制器、SDK 或在线调试器。
- cURL 示例从环境变量读取 AppSecret 并在本地计算签名；AppSecret 不进入 URL、命令参数或输出。
- 文档资产由 OpenAPI bundle 和三个示例源文件构建生成，页面使用 `no-store` 加载；失败时无陈旧内容并提供 Request ID 与重试。

## 已知非阻断信息

- jsdom 在 axe 运行时输出既有 Canvas 未实现提示，不影响 42 项测试通过。
- Maven 默认缓存路径在本机被 Wrapper 错解为 `C:\.m2`；使用显式用户缓存目录后 60 项回归全部通过。
