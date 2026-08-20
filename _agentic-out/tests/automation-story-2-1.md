# Story 2.1 自动化验证记录

日期：2026-08-18

## 执行结果

- 后端全量：`apps/api/mvnw.cmd test` — **58 tests，0 failures，0 errors，0 skipped，PASS**。
- Web 单元/组件：`npm test -- --run` — **34 tests，34 passed，PASS**。
- Web 类型检查：`npm run typecheck` — **PASS**。
- Web 生产构建：`npm run build` — **PASS**，Vite 产物成功生成。
- OpenAPI：`pnpm validate:openapi` — console/openapi/sandbox 三份契约全部 **valid**。
- 工作树格式：`git diff --check` — **PASS**；仅报告 Windows LF→CRLF 提示，无空白错误。

## Story 2.1 关键证据

- MySQL Testcontainers 覆盖应用创建、并发唯一约束、跨企业隔离、唯一 AppID/IV、状态漂移、持久化失败回滚和普通查询不返回 AppSecret。
- 集成测试覆盖 AES-GCM 往返与篡改失败，以及受控重置的行锁/幂等、元数据边界、零/多行保护、审计失败回滚和一次性命令输出。
- Web 测试覆盖受持续授权保护的应用路由、加载失败重试、一次性展示、复制成功/失败播报、beforeunload/浏览器后退警告、键盘确认及刷新后不恢复明文。
- OpenAPI 将 `ApplicationCreatedResponse` 与普通 `ApplicationResponse` 分离，普通查询 schema 不包含 AppSecret。

## 说明

- Vitest 输出既有 jsdom `HTMLCanvasElement.getContext()` 未实现提示；不影响 34 项测试结果。
- Node 工具因本机 `node_modules/.vite-temp` 和 `tsconfig.tsbuildinfo` ACL 限制在沙箱外执行；命令和产物均位于当前工作区。
