---
artifact_kind: story
status: "done"
delivery_profile: standard
source_story: ''
created_at: '2026-08-18'
blocking_condition: ""
followup_review_recommended: false
---

# Story 4.1：发布 OpenAPI 文档和固定签名示例

Status: done

## Story

作为已通过审核并获得沙箱应用凭证的客户开发者，
我希望查看三个只读接口的静态 OpenAPI 文档并复制经过验证的 cURL、Java、Python 签名示例，
以便在网关交付前完成客户端开发准备，并在后续沙箱开放时按同一契约快速联调。

## Acceptance Criteria

1. **冻结首发 OpenAPI v1 契约**
   - **Given** 首发范围仅包含客户基础信息、订单列表和订单详情查询
   - **When** 校验 `contracts/openapi/openapi-v1.yaml` 与 `contracts/openapi/sandbox-v1.yaml`
   - **Then** 两份契约均定义 `GET /customers/{customerId}`、`GET /orders`、`GET /orders/{orderId}`，并分别声明 `CUSTOMER_BASE_READ`、`ORDER_LIST_READ`、`ORDER_DETAIL_READ` 权限。
   - **And** 契约包含用途、参数、必填/可空、枚举、分页与时间范围、Customer/OrderSummary/OrderDetail 字段、手机号和地址脱敏示例、通用成功包络、统一错误、Request ID、限流及重试说明。
   - **And** 沙箱与生产除服务器地址和模拟数据说明外，路径、参数、模型、枚举、错误与安全契约完全一致；三份 OpenAPI lint 均通过。

2. **唯一且确定的签名规范**
   - **Given** 客户准备一个开放 API 请求
   - **When** 阅读鉴权章节
   - **Then** 文档精确定义 AppID、Unix 秒时间戳、nonce、HMAC-SHA256 签名的请求头名称、字符编码、大小写和格式，并规定时间窗口为服务器时间 `±5` 分钟、nonce 在窗口内唯一。
   - **And** 待签名串固定覆盖 HTTP 方法、规范化路径、按编码后键和值排序的查询串、请求体 SHA-256 小写十六进制摘要、AppID、时间戳和 nonce；空查询和 GET 空请求体也有唯一表示。
   - **And** URL 编码、重复查询参数、空值、非 ASCII 字符、路径尾斜杠、换行符与十六进制大小写均有封闭规则，任何语言实现对同一输入产生相同字节序列和签名。

3. **固定跨语言测试向量**
   - **Given** 仓库中的公开假 AppID、假 AppSecret、固定时间戳、nonce、请求方法、路径、查询和空请求体
   - **When** 运行签名向量验证
   - **Then** 权威 fixture 明示规范化查询、请求体摘要、完整待签名串和预期签名，且不包含任何真实或可部署凭证。
   - **And** Java 21 `javax.crypto.Mac`、Python 3 标准库 `hmac/hashlib` 与文档展示结果均匹配同一预期值；任一规范化规则或示例漂移使测试失败。
   - **And** 验证至少覆盖无查询参数、乱序/重复参数、空值、空格与中文编码、篡改一个字符后签名不同。

4. **可复制的 cURL、Java 与 Python 示例**
   - **Given** 客户选择任一首发接口
   - **When** 查看或复制对应语言示例
   - **Then** 示例包含生成时间戳与 nonce、规范化请求、请求体摘要、HMAC-SHA256、必需请求头和 sandbox URL，替换 AppID/AppSecret 后即可运行。
   - **And** Java 示例仅使用 JDK 21 标准 API，Python 示例仅使用 Python 3 标准库；cURL 示例不得把 AppSecret 放入 URL、命令参数或输出，必要的签名计算以独立安全步骤呈现。
   - **And** 页面明确提示 AppSecret 只能保存在客户服务端安全配置中，不得提交仓库、写入浏览器存储、日志或前端代码。

5. **受持续授权保护的静态文档页**
   - **Given** 客户拥有有效控制台会话
   - **When** 访问 `/api-docs`
   - **Then** 仅 `APPROVED` 账号可以查看文档，待审核/驳回/会话失效沿用既有 `SessionGuard` 规则；应用页和权限页提供稳定文档入口。
   - **And** 页面可浏览三接口、公共模型、签名步骤、错误码、60 RPM 固定限流、突发 20、`429`/`Retry-After` 与排错建议，并能复制示例且给出可访问的成功/失败反馈。
   - **And** 文档展示来自仓库内受版本控制的契约/fixture 或其构建期派生内容，不在 React 组件中维护第二套字段、错误码或签名常量。

6. **文档可用性与错误恢复**
   - **Given** 客户使用键盘、320/390px 窄视口、常见桌面宽度或 200% 缩放
   - **When** 浏览目录、代码块和复制按钮
   - **Then** Tab 顺序与 DOM 顺序一致，主操作可由键盘完成，焦点可见，标题层级与代码标签可被读屏理解；页面不产生整页水平滚动，长代码仅在自身容器滚动或换行。
   - **And** 剪贴板不可用或复制失败时显示 `role=alert` 的可理解提示并保留可手动选择的源码；成功反馈不只依赖颜色。
   - **And** 契约资源加载失败时显示 Request ID（若存在）、重试入口及企业微信/邮件支持渠道，不显示空白页或过期缓存冒充成功。

7. **首发范围边界**
   - 本 Story 只交付静态文档、OpenAPI 契约、固定签名向量和可复制示例。
   - 不实现开放 API 网关鉴权、nonce/限流运行逻辑、三个查询接口、在线发送请求、生产环境调试、Swagger/Redoc 自研工作台、SDK 下载或接口版本管理；这些运行能力由 Story 5.1–5.4 承接。

## Tasks / Subtasks

- [x] **Task 1：冻结公共模型与三个查询契约** (AC: 1, 7)
  - [x] 在 `contracts/openapi/components/` 中提取可复用的开放 API 安全头、通用包络、分页、错误和 Customer/Order 模型，禁止复制两套生产/沙箱定义。
  - [x] 完成 `openapi-v1.yaml` 与 `sandbox-v1.yaml` 的三条路径、operationId、权限说明、参数、响应、示例、错误和服务器说明。
  - [x] 保持 OpenAPI 3.1.0 与现有 lint 工具兼容；不得手改生成文件或提前生成未使用的服务端桩代码。

- [x] **Task 2：建立签名规范与唯一测试向量** (AC: 2, 3)
  - [x] 新增受版本控制的机器可读 fixture，封闭规范化算法、请求头、编码和固定预期值。
  - [x] 为 Java、Python 和 TypeScript/构建侧验证同一 fixture；测试覆盖重复参数、空值、Unicode、空请求体与篡改输入。
  - [x] 所有 fixture 使用明显不可部署的假凭证；测试与失败输出不得打印 AppSecret。

- [x] **Task 3：提供三种最小依赖调用示例** (AC: 3, 4)
  - [x] 在 `contracts/examples/` 或等价契约资产目录维护 cURL、Java 21、Python 3 示例，并从同一 fixture 校验关键输出。
  - [x] 示例明确替换项、服务器时间同步、nonce 唯一性、签名失败排查、429 退避和密钥保护。
  - [x] 不新增 SDK 工程、包发布、第三方加密库或生产请求能力。

- [x] **Task 4：实现受保护的静态 API 文档页** (AC: 4–6)
  - [x] 在 `apps/web/src/features/api-docs/` 实现 `/api-docs`，复用现有 `SessionGuard`、请求错误、按钮、状态反馈和导航模式。
  - [x] 从单一契约资产的构建期派生模块或受测试约束的静态资产渲染接口、模型、错误、限流及代码示例；禁止在 JSX 中另写一套契约。
  - [x] 实现复制成功/失败、资源失败重试、键盘顺序、焦点、窄屏与代码溢出处理。

- [x] **Task 5：验证契约、示例和首发边界** (AC: 1–7)
  - [x] 运行 console、production、sandbox 三份 OpenAPI lint，并增加生产/沙箱语义一致性检查。
  - [x] 增加固定签名向量自动化测试、示例可执行性测试，以及前端路由守卫、内容、复制失败、键盘与 reflow 测试。
  - [x] 生成 automation 与 traceability 证据；只对真实执行并通过的场景标 PASS，确认页面无发送请求控件、无真实密钥、无网关实现。

### Review Findings

- [x] [Review][Patch] 为本次新增公共契约、文档路由与签名行为补齐 append-only Requirement Change Log 闭环。 [`_agentic-out/implementation/stories/4-1-发布-openapi-文档和固定签名示例.md`:197]
- [x] [Review][Patch] 修复 `OrderDetail allOf` 与 `additionalProperties: false` 冲突，确保详情响应可通过 JSON Schema。 [`contracts/openapi/components/public-api.yaml`:58]
- [x] [Review][Patch] 修复金额正则的 YAML 双反斜杠并限制整数位长度。 [`contracts/openapi/components/public-api.yaml`:55]
- [x] [Review][Patch] 为 orderId 固化非空且不含路径保留字符的格式。 [`contracts/openapi/components/public-api.yaml`:45]
- [x] [Review][Patch] Java RFC 3986 编码补齐 `* -> %2A`，并加入跨语言边界向量。 [`contracts/examples/java/SignedRequest.java`:13]
- [x] [Review][Patch] 将规范化查询排序改为与区域无关的编码 ASCII 顺序。 [`scripts/validate-public-api.mjs`:5]
- [x] [Review][Patch] Python 输出 cURL 时采用安全 shell quoting，防止环境值形成命令注入。 [`contracts/examples/python/signed_request.py`:24]
- [x] [Review][Patch] 页面直接提供完整可运行的 cURL、Java、Python 源码，不再复制仅仓库内可用的路径命令或占位签名。 [`apps/web/src/features/api-docs/api-docs-content.ts`:8]
- [x] [Review][Patch] 从 OpenAPI/fixture 构建派生文档资产并渲染参数、模型、响应示例与完整错误目录，消除手工字符串事实源。 [`apps/web/src/features/api-docs/api-docs-content.ts`:1]
- [x] [Review][Patch] 为文档资产加载失败实现 Request ID、重试和禁止陈旧内容路径，并补真实测试。 [`apps/web/src/features/api-docs/ApiDocsPage.tsx`:1]
- [x] [Review][Patch] 补齐尾斜杠、换行、方法/十六进制大小写、单字符篡改及 Java 直接消费 fixture 的签名证据。 [`scripts/validate-public-api.mjs`:7]
- [x] [Review][Patch] 补齐 `/api-docs` 的 REJECTED、匿名与过期会话授权测试。 [`apps/web/src/app/App.test.tsx`:549]
- [x] [Review][Patch] 在契约中封闭统一错误码及其 HTTP/重试建议，并让文档由该目录派生。 [`contracts/openapi/components/public-api.yaml`:64]
- [x] [Review][Patch] 为 Clipboard Promise 永不结束设置超时并恢复可操作反馈。 [`apps/web/src/features/api-docs/ApiDocsPage.tsx`:7]
- [x] [Review][Patch] 将契约/文档一致性检查从字符串包含升级为结构化或构建派生校验。 [`scripts/validate-public-api.mjs`:16]
- [x] [Review][Patch] 对生成资产执行字段级运行时校验，空路径、错误容器和畸形嵌套统一进入可恢复错误态。 [`apps/web/src/features/api-docs/api-docs-content.ts`:6]
- [x] [Review][Patch] 在页面展示公开测试 AppSecret，使固定签名向量可独立复现。 [`apps/web/src/features/api-docs/ApiDocsPage.tsx`:24]
- [x] [Review][Patch] 规范化示例 Base URL 尾斜杠，并为 Python 生成的 cURL 增加连接与总超时。 [`contracts/examples/python/signed_request.py`:7]

## Tests Required

- AC1：三份 OpenAPI lint；生产/沙箱路径、参数、模型、错误、安全声明的结构化差异为 0；三个权限代码映射准确。
- AC2：规范化表驱动测试覆盖查询排序、重复值、空值、RFC 3986 百分号编码、Unicode、空体、尾斜杠和大小写边界。
- AC3：同一 fixture 的 Java/Python/TypeScript 结果一致；固定正向签名匹配，任一输入篡改不匹配，测试日志不含假 AppSecret 原文。
- AC4：三个语言示例至少各执行或编译一次；示例只依赖声明的标准工具，AppSecret 不出现在 URL、输出和仓库外的派生日志。
- AC5：`APPROVED`、`PENDING_REVIEW`、`REJECTED`、匿名和过期会话路由测试；导航入口与页面内容均可达且不重复维护契约值。
- AC6：复制成功/拒绝/异常、资源失败后重试、Tab/Enter、焦点、标题语义、320/390px、桌面宽度和 200% reflow 的可执行证据。
- AC7：静态扫描或组件断言证明不存在 `fetch` 到 `/sandbox`/`/openapi` 的调试请求、生产环境切换、SDK 下载和新增网关运行代码。

## Dev Notes

### 实现边界与决策

- `epics.md` 顶部“首发试点执行 Story”是当前批准基线，覆盖并取代同文件后部旧版 Epic 4 对 4.1–4.4 的细分：本 Story 合并静态契约、文档和三语言示例，但继续排除页面调试器。
- 当前 `openapi-v1.yaml` 与 `sandbox-v1.yaml` 只有空 `paths` 骨架；不能把空契约包装成文档页面后宣称完成。
- Story 5.1 才实现 HMAC 校验、时间窗口、nonce 和限流。这里定义可验证的外部契约与向量，运行实现必须在后续 Story 复用同一规范。
- 对 HMAC 的 OpenAPI 表达可使用多个 header `apiKey` security scheme，并在说明中规定组合要求；OpenAPI 安全对象必须与请求头参数和示例一致。
- Java 21 平台保证支持 `HmacSHA256` 的 `javax.crypto.Mac`；Python 使用 `hmac.digest`/`hashlib.sha256`。服务端后续比较签名应使用常量时间比较，但本 Story 不实现服务端验证。

### 架构与仓库约束

- OpenAPI 是路径、字段、错误和示例的唯一契约源；共享定义放 `contracts/openapi/components/`，语言示例和 fixture 放 `contracts/examples/`，前端实现放 `apps/web/src/features/api-docs/`。
- 延续 React + TypeScript + Vite、Vitest/Testing Library 和现有 fetch/组件模式；不要为静态首发页新增 Swagger UI、Redoc、状态库或设计系统依赖。
- 所有成功/错误响应延续统一 Request ID；JSON 使用 UTF-8，时间使用 RFC 3339/UTC，金额使用十进制定长字符串，枚举值稳定大写。
- 不提交任何真实 AppID/AppSecret，不在 URL、浏览器存储、日志、截图或错误消息中展示秘密；文档中的凭证必须标记为公开测试值。

### 既有实现经验

- Story 2.1 已建立应用凭证的一次展示和秘密安全边界；Story 3.1 已建立三个固定权限代码。优先引用现有枚举/生成类型或契约组件，禁止第三套字符串常量。
- 前序 Story 的评审多次发现“报告高报”问题。本 Story 的 automation/traceability 必须逐条指向真实测试，手工说明不能替代键盘、reflow、跨语言签名或契约一致性证据。
- 最近提交采用“契约先行 → 生成/实现 → 集成测试 → 报告 → review”顺序；继续使用该顺序，并忽略 `_agentic-out/.archive/` 未跟踪快照。

### Project Structure Notes

- 预期新增/修改集中于：
  - `contracts/openapi/openapi-v1.yaml`
  - `contracts/openapi/sandbox-v1.yaml`
  - `contracts/openapi/components/`
  - `contracts/examples/`
  - `apps/web/src/features/api-docs/`
  - `apps/web/src/app/App.tsx` 及对应测试/导航
  - 根级契约验证脚本与测试报告
- 若签名共享实现必须供 Story 5.1 使用，应放在明确的契约/安全模块并保持纯函数；本 Story 不创建控制器、Redis nonce 存储或限流器。

### References

- [首发 Story 基线](/D:/work-flow/agentic-workflow-main/demo/_agentic-out/planning/epics.md)
- [PRD API 规范与 FR28–FR35](/D:/work-flow/agentic-workflow-main/demo/_agentic-out/planning/prd.md)
- [架构签名与 OpenAPI 决策](/D:/work-flow/agentic-workflow-main/demo/_agentic-out/planning/architecture.md)
- [首发 UX 行为基线](/D:/work-flow/agentic-workflow-main/demo/_agentic-out/planning/ux/EXPERIENCE.md)
- [Oracle Java 21 `Mac` 文档](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Mac.html)
- [Python `hmac` 官方文档](https://docs.python.org/3/library/hmac.html)
- [OpenAPI 3.1.0 规范](https://spec.openapis.org/oas/v3.1.0.html)

## Dev Agent Record

### Agent Model Used

待开发代理填写。

### Debug Log References

- `_agentic-out/tests/automation-story-4-1.md`
- `_agentic-out/tests/traceability-story-4-1.md`

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- 完成生产/沙箱共享 OpenAPI 契约、三项只读接口模型、错误与安全头。
- 完成固定 HMAC-SHA256 向量及 cURL、Java 21、Python 3 示例。
- 完成受 SessionGuard 保护的静态 `/api-docs` 页面、复制反馈、导航与响应式样式。
- 验证通过：前端 42/42、后端 60/60、三份 OpenAPI lint、三语言向量、类型检查与构建。

### File List

- `_agentic-out/artifacts.yaml`
- `_agentic-out/implementation/sprint-status.yaml`
- `_agentic-out/implementation/stories/4-1-发布-openapi-文档和固定签名示例.md`
- `_agentic-out/tests/automation-story-4-1.md`
- `_agentic-out/tests/traceability-story-4-1.md`
- `apps/web/src/app/App.test.tsx`
- `apps/web/src/app/App.tsx`
- `apps/web/src/features/api-docs/ApiDocsPage.tsx`
- `apps/web/src/features/api-docs/api-docs-content.ts`
- `apps/web/src/features/applications/ApplicationsPage.tsx`
- `apps/web/src/features/auth/SessionGuard.tsx`
- `apps/web/src/features/permissions/PermissionsPage.tsx`
- `apps/web/public/api-docs-contract.json`
- `apps/web/public/api-docs-examples.json`
- `apps/web/public/api-docs-signing-vector.json`
- `apps/web/src/styles/globals.css`
- `contracts/examples/curl/orders.sh`
- `contracts/examples/curl/orders.ps1`
- `contracts/examples/java/SignedRequest.java`
- `contracts/examples/java/SigningVectorVerifier.java`
- `contracts/examples/python/signed_request.py`
- `contracts/examples/python/verify_vector.py`
- `contracts/examples/signing-vector.json`
- `contracts/openapi/components/public-api.yaml`
- `contracts/openapi/openapi-v1.yaml`
- `contracts/openapi/sandbox-v1.yaml`
- `package.json`
- `scripts/validate-public-api.mjs`
- `scripts/generate-public-api-docs.mjs`

## Requirement Change Log

<!-- Append-only. 每个行为或范围变更记录：Trigger、Classification、Previous behavior、New behavior、Acceptance Criteria affected、Tasks affected、Upstream artifacts affected、Tests required、Approval evidence、Status。 -->

- **Trigger：** 用户确认继续开发已批准的 Story 4.1，首次交付该 Story 的全部客户可见行为。
  **Classification：** Story Amendment（首次实现记录，用于 changed-behavior 追踪闭环，不改变已批准范围）。
  **Previous behavior：** 生产与沙箱 OpenAPI 的 `paths` 为空；控制台没有 `/api-docs` 路由、静态接口文档、签名规范、固定向量或三语言示例。
  **New behavior：** 新增三项查询契约、共享模型/错误/安全规则、固定签名向量、cURL/Java/Python 示例、受持续授权保护的静态文档页及首发范围门禁。
  **Acceptance Criteria affected：** AC1–AC7。
  **Tasks affected：** Task 1–Task 5。
  **Upstream artifacts affected：** 无；直接实现已批准的 PRD、架构、UX 与 Epic 4.1 基线。
  **Tests required：** Tests Required 中 AC1–AC7 全部证据，以及前后端完整回归。
  **Approval evidence：** 用户在 Story 创建完成后回复“继续”，明确批准按 Story 4.1 开发。
  **Status：** applied。
- **Trigger：** Story 4.1 代码评审发现契约组合、跨语言规范化、静态文档事实源、资源失败、复制示例和验收证据共 15 项缺口。
  **Classification：** Implementation Correction。
  **Previous behavior：** 文档页维护第二套摘要常量并只复制仓库命令；契约资产无运行时失败分支；部分签名边界与授权状态未验证；OrderDetail/金额 Schema 存在缺陷。
  **New behavior：** 页面运行时加载由 OpenAPI 打包生成的 no-store 契约资产和完整示例源码，提供 Request ID/重试与复制超时；契约封闭错误目录和字段边界；Node/Python/Java 对固定 fixture 与规范化边界保持一致；授权矩阵及报告按真实证据更新。
  **Acceptance Criteria affected：** AC1–AC6。
  **Tasks affected：** Task 1–Task 5；Review Findings 1–15。
  **Upstream artifacts affected：** 无；保持 PRD、架构、UX 和首发范围不变。
  **Tests required：** OpenAPI lint/打包一致性、Schema 边界、三语言签名向量与篡改、完整示例编译/安全扫描、文档资源失败重试、剪贴板超时、APPROVED/PENDING/REJECTED/匿名/过期会话、键盘/axe/reflow、前后端回归。
  **Approval evidence：** 用户在 2026-08-18 代码评审处理选项中选择 `0`，批准批量应用全部非争议修复。
  **Status：** applied。
- **Trigger：** Story 4.1 最终复审发现生成资产畸形输入、固定向量复现信息及示例 Base URL/超时三个边界缺口。
  **Classification：** Implementation Correction。
  **Previous behavior：** 资源校验只检查部分真值，页面未显示公开测试 AppSecret，尾斜杠 Base URL 可能产生双斜杠且 Python 生成命令无网络超时。
  **New behavior：** 对页面消费的契约、错误、签名、示例和向量字段执行封闭类型校验；展示明确标注的测试 AppSecret；三语言示例规范化 Base URL，Python 生成命令带 10 秒连接及 30 秒总超时。
  **Acceptance Criteria affected：** AC2、AC3、AC4、AC6。
  **Tasks affected：** Task 2、Task 3、Task 5；Review Findings 16–18。
  **Upstream artifacts affected：** 无；仅收紧已批准行为的安全性与可恢复性。
  **Tests required：** 畸形资产进入格式错误态、固定向量完整显示、前端完整回归、类型检查、构建和契约校验。
  **Approval evidence：** 用户此前选择 `0` 批准批量修复全部非争议评审项；本轮属于同一评审批次的追加边界修复。
  **Status：** applied。

## Auto Run Result

- Status: done
- Summary: lifecycle transition to done
