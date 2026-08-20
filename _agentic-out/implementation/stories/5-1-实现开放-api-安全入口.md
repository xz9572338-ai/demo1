---
artifact_kind: story
status: "review"
delivery_profile: standard
source_story: ''
created_at: '2026-08-18'
blocking_condition: ""
followup_review_recommended: true
---

# Story 5.1：实现开放 API 安全入口

Status: review

## Story

作为已获准应用的客户开发者，
我希望平台验证 AppID、请求签名、时间戳、nonce、应用状态和接口权限，
以便只有真实、未重放、处于正确环境且获得授权的请求进入后续供应链查询处理。

## Acceptance Criteria

1. **生产等价的安全入口与请求上下文**
   - **Given** 请求到达 `/sandbox/v1/**`
   - **When** AppID、凭证、HMAC-SHA256 签名、时间戳、nonce、应用状态、环境和接口权限全部有效
   - **Then** 按 Story 4.1 的唯一签名规范校验完整规范化请求，并建立不可变的 `OpenApiRequestContext`，至少包含 `enterpriseId`、`applicationId`、`environment`、当前端点唯一 `permissionCode`、该权限记录自己的 `internalCustomerScope`、`requestId` 和 `traceId`。
   - **And** 业务模块只接收该可信上下文，不接收 AppSecret、签名原文或可逆凭证；不得从客户传入的 customerId 推导授权范围。
   - **And** 首发只允许 `SANDBOX` 凭证、沙箱数据与沙箱 Redis 命名空间；生产或未知环境一律拒绝，任何真实生产客户数据访问保持禁用。

2. **签名验证完全复用已发布契约**
   - **Given** 请求携带 `X-App-ID`、`X-Timestamp`、`X-Nonce`、`X-Signature`
   - **When** 服务端构造待签名串
   - **Then** 严格复用 OpenAPI `x-signing` 和 `contracts/examples/signing-vector.json`：大写 HTTP 方法、原始规范化路径、RFC 3986 编码后按键和值 ASCII 排序且保留重复参数、请求体 SHA-256 小写十六进制、AppID、Unix 秒时间戳和 nonce，以 LF 连接。
   - **And** 使用解密后的环境凭证计算 HMAC-SHA256，并以常量时间比较期望值和客户端小写十六进制签名；缺失、格式错误、大小写错误、正文摘要变化、路径或查询变化均返回 `401 SIGNATURE_INVALID`。
   - **And** 请求正文只能读取为受大小限制的可重复读取字节，签名校验不得改变后续处理看到的正文。
   - **And** 首发三个 GET 端点只接受零字节正文；四个安全头必须为单值 ASCII，AppID 精确匹配 `app_[A-Za-z0-9_-]{32}`，时间戳匹配 1–12 位十进制 Unix 秒，nonce 匹配 `[A-Za-z0-9_-]{16,64}`，签名匹配 64 位小写十六进制；仅接受三条已知路径且原始查询串不超过 4096 UTF-8 字节。格式或大小异常在数据库、解密和 Redis 前返回 `400 VALIDATION_FAILED`。

3. **时间窗口与 nonce 防重放**
   - **Given** 签名正确但时间戳超出服务器当前时间 `±300` 秒
   - **When** 验证请求
   - **Then** 返回 `401 TIMESTAMP_EXPIRED`、统一 Error 与 Request ID，且不占用 nonce、不进入 Controller/业务模块或数据中台。
   - **Given** 时间戳有效且签名正确
   - **When** 首次使用该环境、AppID 与 nonce
   - **Then** 使用 Redis 单个原子 `set-if-absent + TTL` 接受请求，TTL 计算为 `timestamp + 300秒 - 当前服务器时间`（最小 1 秒，最大 600 秒）；键使用 environment、applicationId、nonce 的长度前缀编码或摘要，不直接拼接不可信 header；任意并发重复只有一个请求成功。
   - **And** 重复 nonce 返回 `401 NONCE_REPLAYED`；不同应用或不同环境使用相同 nonce 互不冲突；Redis 异常返回 `503 SERVICE_UNAVAILABLE` 并失败关闭。

4. **应用、企业、环境、权限和客户范围默认拒绝**
   - **Given** AppID 不存在、凭证不可解密、企业未通过、应用非 `ACTIVE`、环境不匹配、权限未批准或客户范围为空/不合法
   - **When** 请求到达安全入口
   - **Then** 不建立安全上下文、不进入业务或数据中台，并按外部契约返回稳定的 `401 SIGNATURE_INVALID` 或 `403 APPLICATION_INACTIVE`、`ENVIRONMENT_MISMATCH`、`PERMISSION_DENIED`；未知 AppID 与错误签名不得形成可用于枚举应用的差异。
   - **And** 权限代码从 operationId/端点的服务端封闭映射获取；只把当前端点权限对应的 scope 放入上下文，禁止合并、回退或串用另一权限的 scope。
   - **And** 状态变更后一分钟内对新请求生效；首发允许每次读取 MySQL 权威投影，不新增权限缓存。

5. **错误、Request ID 与安全日志**
   - **Given** 任一安全校验拒绝或依赖故障
   - **When** 返回响应
   - **Then** 使用 Story 4.1 OpenAPI 的统一 Error 结构与正确 HTTP 状态，响应头和正文使用同一 Request ID，`retryable` 与错误目录一致；鉴权失败不得返回业务数据或部分成功。
   - **And** 安全日志只记录时间、环境、Request ID、Trace ID、经批准的 application/enterprise 标识、端点和结果分类；禁止记录 AppSecret、密文、IV、完整签名、完整 nonce、规范串、请求/响应业务体或未经处理的客户输入。
   - **And** 同一拒绝只记录一次终态，不在多层重复输出堆栈；Redis/数据库故障记录平台错误但响应不泄露内部异常。

6. **固定应用/环境/接口限流**
   - **Given** 已通过身份、状态和权限校验的请求未超过固定额度
   - **When** 按 applicationId、environment、permission/endpoint 计算流量
   - **Then** 使用 Redis 原子令牌桶：容量 20、初始 20、每 1000ms 补充 1 个令牌且最多 20；同一时刻前 20 个请求通过，第 21 个返回 429，经过完整 1000ms 后恢复 1 个名额。不同应用、环境和接口互不混用；实现使用注入时钟，`Retry-After = ceil(距离下一个令牌的毫秒数 / 1000)` 且最小为 1。
   - **Given** 任一桶超过额度
   - **When** 新请求到达
   - **Then** 原子拒绝并返回 `429 RATE_LIMITED`、统一 Error、Request ID 和非负整数秒 `Retry-After`，且不进入业务或数据中台。
   - **And** Redis 无法执行限流判断时返回 `503 SERVICE_UNAVAILABLE` 并失败关闭；不得退化为 JVM 计数或无限流。

7. **固定安全执行顺序与资源消耗**
   - 所有请求按以下顺序执行：Request ID/Trace ID → 路径、方法、头、查询和空正文的格式/大小校验 → AppID 查询（未知 AppID 使用部署时固定且独立的 dummy key 完成同一 HMAC 计算）与常量时间签名比较 → 时间窗口 → 原子占用 nonce → 当前企业/应用/环境/端点权限/该权限 scope 持续授权 → 原子限流 → Controller。
   - 格式错误、未知 AppID、坏签名和过期时间不占 nonce 或配额；签名和时间有效后，即使状态/权限拒绝或限流，nonce 也已占用，重试必须使用新 nonce；状态/权限拒绝不消费令牌；429 不把令牌扣为负数。
   - 未知 AppID 与坏签名的响应结构、code/message/details/retryable 及终态日志分类/级别一致，并都执行一次 HMAC。本 Story 不承诺网络级严格等时，只验收外部语义与结构路径不可枚举。

8. **过滤链、外层错误与边界隔离**
   - **Given** 控制台 Cookie 会话和开放 API HMAC 请求共存于同一部署单元
   - **When** Spring Security 选择过滤链
   - **Then** `/console/api/v1/**` 保持现有 Cookie/CSRF 行为；`/sandbox/v1/**` 和后续 `/openapi/v1/**` 使用无状态 HMAC 安全链，不创建或读取控制台会话，不接受 Cookie 作为开放 API 身份。
   - **And** HMAC 认证在授权与 Controller 之前完成；拒绝路径由安全入口直接终止，不依赖前端或业务 Controller 补救。
   - **And** 使用有序且互斥的 `securityMatcher` 链：开放 API 链只匹配 `/sandbox/v1/**`；控制台链只匹配既有 console/health 路径；最后配置 deny-all fallback。未知 sandbox/openapi 路径不得落入控制台会话授权。
   - **And** 最外层 Request ID/异常映射按路径选择错误目录：控制台保留 `AUTH_SERVICE_UNAVAILABLE`，开放 API 的数据库、Redis、解密或认证基础设施故障返回 `SERVICE_UNAVAILABLE`；异常跨出安全链也不得误用控制台错误。
   - **And** 现有注册、登录、应用、权限和静态文档测试无回归。

9. **首发范围边界**
   - 本 Story 实现安全请求上下文、签名、时间窗口、防重放、状态/环境/权限默认拒绝、固定限流及安全日志，不实现三个业务查询的数据中台调用。
   - 不得为动态配额、统计聚合、密钥轮换、生产自助开通或完整审计平台扩张范围；Story 5.4 只复验本 Story 的固定限流，不补做运行逻辑。
   - 不新增预生产环境，不生成真实生产凭证，不启用真实生产客户数据，不新增 JWT、API Gateway 产品、OAuth、SDK 或前端调试器。

## Tasks / Subtasks

- [x] **Task 1：固化开放 API 安全入口契约与端点权限映射** (AC: 1, 2, 4, 5, 9)
  - [x] 复核现有 `public-api.yaml` 的安全头、错误码和 `x-signing`；只在发现运行时必需但契约缺失的字段时先改 OpenAPI，再改实现。
  - [x] 建立三条 operation/路径到 `CUSTOMER_BASE_READ`、`ORDER_LIST_READ`、`ORDER_DETAIL_READ` 的封闭服务端映射；未知端点不得落入默认权限。
  - [x] 保持生产/沙箱契约语义一致，不创建第二套签名常量或错误目录。

- [x] **Task 2：实现凭证解析和 HMAC 验证** (AC: 1, 2, 4)
  - [x] 在 `credential` 模块提供只返回校验所需秘密材料的窄接口；复用 `application_credentials` 的 AES-GCM、keyId 和 AAD 规则，不让调用方访问 Repository/JDBC 或记录明文。
  - [x] 在开放 API 安全边界实现规范请求构造、请求体缓存上限、HMAC-SHA256 与常量时间比较；使用 Story 4.1 fixture 做服务端兼容测试。
  - [x] 对未知 AppID 和错误签名提供不可枚举的相同外部语义；所有秘密字节在最短作用域内使用，不写入异常、日志或响应。
  - [x] 配置独立 dummy HMAC key；未知 AppID 仍构造规范串并执行一次 HMAC，但 dummy key 永远不能成为有效凭证。
  - [x] 在进入 DB/HMAC 前执行路径、单值头格式、4096 字节查询与 GET 空正文校验；不把原始 nonce 直接拼接为 Redis 键。

- [x] **Task 3：实现 Redis nonce 原子防重放** (AC: 3)
  - [x] 使用独立 `openapi:nonce` 键前缀和 environment/applicationId/nonce 摘要或长度前缀编码形成不可歧义键；避免把原始不可信 header 或秘密写入键。
  - [x] 原子执行首次写入与 TTL，覆盖并发首用、重复、窗口边界和 TTL 到期；不得用进程内集合或“先查后写”。
  - [x] 将 Redis 连接、超时和命令异常统一映射为可重试 `503 SERVICE_UNAVAILABLE`，且不得继续过滤链。

- [x] **Task 4：建立可信请求上下文并执行持续授权** (AC: 1, 4, 7, 8)
  - [x] 从 MySQL 权威关系解析 enterprise/application/environment/approved permission/internal customer scope；任何缺失、重复或非法状态均失败关闭。
  - [x] 按当前端点唯一 permissionCode 解析其专属 scope；用三个不同 scope 的批准权限证明上下文不合并、不回退、不串用。
  - [x] 定义不含凭证的不可变 Authentication principal/context，并在请求结束后由框架清理；后续 supplychain 只能读取该上下文。
  - [x] 配置明确 `@Order` 与 `securityMatcher` 的 sandbox、console 和 deny-all fallback 链；sandbox 无状态且不使用 Session/CSRF 身份，console 行为不变。

- [x] **Task 5：实现固定限流、统一错误与安全可观测性** (AC: 3–8)
  - [x] 使用独立 Redis 键前缀按 application/environment/endpoint 原子执行容量20、初始20、每1000ms补1的令牌桶；用注入时钟计算最小1秒的 `Retry-After`，故障失败关闭。
  - [x] 使用现有 `ApiError`、`RequestIdFilter` 与共享错误写出能力；验证响应头/正文 Request ID 一致、响应提交前后故障均不泄露半响应。
  - [x] 增加结构化安全结果分类并验证敏感字段黑名单；不得打印完整 nonce/签名/规范串或业务体。
  - [x] 对数据库、Redis、解密与未知运行时错误分别给出稳定、安全且与契约一致的结果。
  - [x] 将最外层 `RequestIdFilter` 的异常映射改为路径感知策略；开放 API DB/Redis 异常跨出安全链时仍返回 `SERVICE_UNAVAILABLE`，控制台既有结果不变。
  - [x] 用单一编排测试冻结 AC7 顺序及 nonce/配额副作用，禁止依靠偶然的 Filter 注册顺序。

- [x] **Task 6：完成安全、并发和回归验证** (AC: 1–9)
  - [x] Testcontainers MySQL + Redis 集成测试覆盖合法请求、固定向量、正文/路径/查询篡改、缺头/坏格式、时间 `-301/-300/+300/+301` 秒边界。
  - [x] 覆盖同 AppID/环境/nonce 并发竞争只有一个成功、TTL 到期、跨应用与跨环境独立、Redis 各关键操作失败关闭。
  - [x] 覆盖 60 RPM、突发 20、超额第一个请求、窗口恢复、应用/环境/接口桶隔离、`Retry-After` 和 Redis 限流故障；超额请求下游调用为 0。
  - [x] 覆盖未知 AppID与错误签名同语义、非 ACTIVE/环境错/三权限分别缺失/空客户范围、数据库与解密故障、状态变更一分钟内生效。
  - [x] 覆盖安全头和请求目标的长度/格式上限、非空 GET 正文、畸形或重复 header；这些请求不访问 DB、解密或 Redis。
  - [x] 覆盖三权限不同客户 scope、未知/未映射路径、Cookie 尝试、外层 DB/Redis 异常映射和 deny-all fallback。
  - [x] 使用探针 Controller 或 mock downstream 证明所有拒绝路径调用次数为 0，合法请求只传递无秘密上下文；日志捕获断言秘密/签名/正文不存在。
  - [x] 运行 OpenAPI 校验、模块边界、后端完整回归并生成 automation/traceability；未真实执行的故障与并发场景不得标 PASS。

### Review Findings

- [x] [Review][Patch] 服务端签名路径包含 `/sandbox/v1`，与已发布示例及固定向量使用的 `/orders` 不兼容 [`OpenApiCanonicalRequest.java`:20]
- [x] [Review][Patch] 查询参数解码会把畸形 UTF-8 替换为 U+FFFD，造成不同原始请求折叠为同一规范串 [`OpenApiCanonicalRequest.java`:26]
- [x] [Review][Patch] 未知 AppID 路径跳过 AES-GCM 工作，仍存在基于处理成本的枚举差异 [`SandboxCredentialVerifier.java`:31]
- [x] [Review][Patch] `/sandbox/v1` 根路径在安全链与自定义过滤器中的匹配语义不一致 [`OpenApiSecurityFilter.java`:28]
- [x] [Review][Patch] 无 Content-Length/Transfer-Encoding 的非空 GET 正文可能绕过空正文约束 [`OpenApiSecurityFilter.java`:48]
- [x] [Review][Patch] 令牌桶依赖应用节点本地时钟，节点时钟漂移会破坏全局限流一致性 [`OpenApiRedisGuard.java`:15]
- [x] [Review][Patch] Story frontmatter、正文与 Sprint 状态不一致 [`5-1-实现开放-api-安全入口.md`:3]
- [x] [Review][Patch] 父任务已完成但所有子任务未勾选，完成记录无法验证 [`5-1-实现开放-api-安全入口.md`:89]
- [x] [Review][Patch] 自动化报告将未实际覆盖的强制安全场景标记为 AC1–AC9 全部 PASS [`automation-story-5-1.md`:1]
- [x] [Review][Patch] 追踪矩阵错误映射 AC7/AC8，且未体现 nonce/配额副作用矩阵 [`traceability-story-5-1.md`:1]
- [x] [Review][Patch] 安全日志缺少敏感字段排除、单终态记录及真实/虚拟凭证同分类证据 [`OpenApiSecurityFilter.java`:56]
- [x] [Review][Patch] Redis 限流、权限查询、解密及异常脚本结果等依赖故障证据不足 [`OpenApiRedisGuardTest.java`:1]
- [x] [Review][Patch] Requirement Change Log 使用了契约外分类且在证据不足时高报 Applied [`5-1-实现开放-api-安全入口.md`:226]
- [x] [Review][Patch] dummy HMAC key 使用仓库公共默认值，不满足部署独立和缺失即失败约束 [`application.yml`:1]

## Tests Required

- AC1：合法沙箱请求建立绑定当前端点权限及其专属 scope 的完整无秘密上下文；生产真实数据身份始终拒绝；业务探针仅接收可信范围。
- AC2：服务端消费 Story 4.1 固定 fixture；表驱动覆盖 RFC 3986、重复参数、空值、Unicode、正文、尾斜杠、方法和十六进制大小写；常量时间比较路径有直接测试或静态证据。
- AC3：时间边界、nonce 原子并发、TTL、跨应用/环境和 Redis 故障测试；所有拒绝均证明下游调用为 0。
- AC4：每种状态/环境/权限/范围异常的闭集测试；三权限使用不同 scope 且无串用；未知 AppID 与坏签名的 HTTP、code、message、details、retryable 和日志分类一致，并都执行 dummy/real HMAC 结构路径。
- AC5：统一 Error 和 Request ID 一致性；捕获日志不含测试 AppSecret、密文、IV、完整签名、完整 nonce、规范串和业务体。
- AC6：令牌桶前20/第21、每秒恢复1、桶隔离、429/Retry-After、Redis 故障关闭与下游零调用。
- AC7：完整顺序和副作用矩阵：坏格式/签名/过期不占 nonce/配额，授权拒绝占 nonce 不占配额，429 占 nonce且配额不为负。
- AC8：sandbox/console/fallback 三链隔离；开放 API 不创建 SESSION，Cookie 不能认证；未知路径 deny；外层 DB/Redis 故障使用正确目录；控制台回归通过。
- AC9：代码与依赖差异证明未新增业务查询、真实生产启用、预生产环境、动态配额、统计、JWT/OAuth、网关产品或页面调试能力。

## Dev Notes

### 实现决策与复用边界

- 项目配置显式选择 `standard`，安全/high-NFR 风险会推荐 `assured`；本 Story 依据已批准的首发瘦身继续使用 standard，但将真实 Redis/MySQL、安全并发、失败关闭、traceability 和完整回归列为 `done` 前阻断证据。这是有意 profile 覆盖，不是降低安全验收。
- Story 4.1 已冻结签名规则、错误目录、三条路径和固定向量；运行时实现必须消费/测试这些资产，不得在 Java 中悄悄定义不同的编码、排序、时间窗口或错误文本。
- Story 2.1 已创建 `applications` 和 `application_credentials`，凭证只允许 `SANDBOX`，并实现 AES-GCM、独立 keyId、应用/环境 AAD、乐观版本和不记录明文的约束。扩展窄读取端口，禁止复制解密逻辑或让安全过滤器直接 JDBC。
- Story 3.1 已创建 `application_permissions` 权威投影，批准记录包含 `internal_customer_scope`。逐接口授权必须基于该投影的 `APPROVED` 状态，不依赖控制台提交记录或客户端声明。
- `RequestIdFilter` 已处理统一 Request ID，但当前把所有 `DataAccessException` 写成控制台专用 `AUTH_SERVICE_UNAVAILABLE`；必须保留一个 Request ID 生成器，同时按请求边界选择错误目录。
- Redis 已用于 Session、登录和注册保护；nonce 必须使用独立前缀、TTL 与失败策略，不能共享或扫描其他用途的键。

### 技术与结构约束

- 后端保持 Java 21 + Spring Boot 4.1 模块化单体；使用现有 Spring Security、Spring Data Redis/JDBC、JUnit、Testcontainers，不引入 JWT、第三方 HMAC 库或外部 API Gateway。
- 建议新增 `credential/{application,domain,infrastructure}` 的凭证验证端口，以及开放 API 安全边界放在 `shared/security/openapi` 或架构明确的独立包；业务状态仍由 application/permission 模块公开应用接口提供，禁止跨模块访问其 Repository/JPA/JDBC。
- Servlet Filter 的顺序是安全行为：认证过滤器必须在 Controller/授权之前，拒绝时不得调用 `chain.doFilter`。Spring Security 官方文档确认过滤器可以阻止下游且顺序决定已完成的安全阶段。
- Redis nonce 使用原子 set-if-absent 与 expiration；Spring Data Redis 当前 API提供该原子语义。签名字节比较使用 Java 21 `MessageDigest.isEqual` 或经证明等价的常量时间比较。
- 首发三个 GET 明确要求零字节正文，因此在缓存前拒绝任何非空正文；不要虚构契约中不存在的正文大小上限。

### 环境、数据与状态语义

- `/sandbox/v1/**` 只能使用 SANDBOX 凭证、模拟数据和独立 Redis 命名空间；当前数据库约束尚无 PREPRODUCTION/PRODUCTION 凭证，这是正确的首发边界，不得在本 Story 放宽或发明第三环境。
- 入驻企业有效性以账号/企业权威状态为准；应用当前约束仅允许 ACTIVE，但实现仍须显式默认拒绝未知状态，避免未来迁移后自动放行。
- 权限/客户范围一分钟生效要求首发直接读取权威 MySQL 投影；不要提前增加权限缓存、定时刷新或消息总线。
- 对外拒绝顺序以 AC7 为唯一基线；只有已认证应用才返回可操作的状态、环境和权限错误。未知 AppID 使用 dummy HMAC 保持结构路径一致，但不宣称网络严格等时。

### 既有评审经验与防灾提示

- 前序 Story 多次出现“报告 PASS 超过真实测试”的阻断问题。每个 Tests Required 场景必须能定位到执行过的测试方法、命令输出或明确的人工证据。
- Redis 安全路径必须覆盖每个关键操作故障，而不是用一个 mock `DataAccessException` 代表全部 create/read/write 行为。
- 并发防重放不可用数据库唯一约束或 JVM 锁替代 Redis 原子命令；测试必须制造真正并发，而非顺序调用两次。
- 未知 AppID不能通过响应时间、code/message/details 或日志级别产生明显枚举差异；不要在错误 details 返回 header、AppID、签名片段或内部主键。
- `_agentic-out/.archive/` 是工作流快照，不纳入 Story 提交；保留用户已有未跟踪归档。

### Git Intelligence

- `b0f6f5a`/`78c97f9` 建立 OpenAPI 生成资产、签名 fixture、三语言验证和严格文档资源校验；本 Story 以此为唯一外部契约基线。
- `1b61f22`/`4ba71ad` 建立逐接口权限投影和审核约束；复用 `PermissionCode` 与批准范围语义。
- `2527ca6` 及 Story 2.1 建立应用凭证、安全重置、AES-GCM AAD 和并发版本控制模式；凭证读取继续遵循同一密钥边界。

### References

- [Spring Security Servlet filter architecture](https://docs.spring.io/spring-security/reference/servlet/architecture.html)
- [Spring Data Redis ValueOperations](https://docs.spring.io/spring-data/redis/docs/current/api/org/springframework/data/redis/core/ValueOperations.html)
- [Java 21 MessageDigest](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/java/security/MessageDigest.html)
- `_agentic-out/planning/epics.md` Story 5.1
- `_agentic-out/planning/architecture.md` 首发实施剖面、认证与安全、过程模式
- `contracts/openapi/components/public-api.yaml`
- `contracts/examples/signing-vector.json`

## Dev Agent Record

### Agent Model Used

GPT-5 Codex

### Debug Log References

- `apps/api/mvnw.cmd -q test`：71 tests，0 failures，0 errors，0 skipped。
- `OpenApiSecurityIntegrationTest`：真实 MySQL 8.4.7 与 Redis 8.4，覆盖并发 nonce、端点级 scope、限流边界和外层故障映射。

### Completion Notes List

- Ultimate context engine analysis completed - comprehensive developer guide created.
- 实现沙箱开放 API 独立安全过滤链、默认拒绝兜底链及控制台链隔离。
- 实现凭证解密、HMAC-SHA256、时间窗、nonce 原子防重放、端点权限与客户范围绑定。
- 实现 Redis 原子令牌桶（容量 20、每秒补充 1）及统一错误、Request ID 与安全日志。
- 全量后端回归 71/71 通过，Story 已具备代码评审条件。

### File List

- `apps/api/src/main/java/com/company/openplatform/credential/application/SandboxCredentialVerifier.java`
- `apps/api/src/main/java/com/company/openplatform/gateway/security/*`
- `apps/api/src/main/java/com/company/openplatform/permission/application/ApprovedPermissionScope.java`
- `apps/api/src/main/java/com/company/openplatform/identity/infrastructure/IdentitySecurityConfiguration.java`
- `apps/api/src/main/java/com/company/openplatform/shared/observability/RequestIdFilter.java`
- `apps/api/src/main/resources/application.yml`
- `apps/api/src/test/java/com/company/openplatform/gateway/**/*`
- `_agentic-out/tests/automation-story-5-1.md`
- `_agentic-out/tests/traceability-story-5-1.md`

## Requirement Change Log

<!-- Append-only. 每个行为或范围变更记录：Trigger、Classification、Previous behavior、New behavior、Acceptance Criteria affected、Tasks affected、Upstream artifacts affected、Tests required、Approval evidence、Status。 -->

- **2026-08-18 — Story 首次实现**
  - Trigger：用户指令“继续”。
  - Classification：Story Amendment（首次实现追踪记录，不改变既定上游范围）。
  - Previous behavior：沙箱开放 API 尚无运行时鉴权、重放保护、端点授权与限流入口。
  - New behavior：按 AC1–AC9 建立沙箱安全入口、可信上下文、持续授权、原子 nonce 与固定令牌桶；生产入口仍未启用。
  - Acceptance Criteria affected：AC1–AC9。
  - Tasks affected：Task 1–6。
  - Upstream artifacts affected：无；实现遵循既有 PRD、架构和 Story。
  - Tests required：单元测试、真实 MySQL/Redis 集成测试、并发 nonce、限流边界、模块边界及全量后端回归。
  - Approval evidence：用户明确要求继续开发；Story 上下文评审为 CLEAN。
  - Status：Applied。
