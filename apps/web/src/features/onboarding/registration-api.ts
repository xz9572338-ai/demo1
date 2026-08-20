import createClient from "openapi-fetch";
import type { components, paths } from "../../generated/api/console-v1";

export type RegistrationInput = components["schemas"]["RegistrationApplicationRequest"];
export type RegistrationResult = components["schemas"]["RegistrationApplicationResponse"];
export type ApiError = components["schemas"]["Error"];

const client = createClient<paths>({
  baseUrl: `${globalThis.location?.origin ?? ""}/console/api/v1`,
  fetch: (request: Request) => globalThis.fetch(request),
});
const REQUEST_TIMEOUT_MS = 10_000;

export async function submitRegistration(input: RegistrationInput): Promise<RegistrationResult> {
  const controller = new AbortController();
  const timer = globalThis.setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  try {
    const csrf = await client.GET("/registration-applications/csrf", { signal: controller.signal });
    if (csrf.error || !isCsrf(csrf.data)) throw normalizeError(csrf.error, "安全令牌初始化失败，请重试");

    const response = await client.POST("/registration-applications", {
      signal: controller.signal,
      headers: { [csrf.data.headerName]: csrf.data.token },
      body: input,
    });
    if (response.error) throw normalizeError(response.error, "申请暂时无法提交，请稍后重试");
    if (!isRegistrationResult(response.data)) throw clientError("INVALID_RESPONSE", "服务响应格式异常，请稍后重试");
    return response.data;
  } catch (problem) {
    if (problem instanceof DOMException && problem.name === "AbortError") {
      throw clientError("REQUEST_TIMEOUT", "请求超时，请检查网络后重试", true);
    }
    throw normalizeError(problem, "申请暂时无法提交，请稍后重试");
  } finally {
    globalThis.clearTimeout(timer);
  }
}

function isCsrf(value: unknown): value is { headerName: "X-XSRF-TOKEN"; token: string } {
  if (!value || typeof value !== "object") return false;
  const candidate = value as Record<string, unknown>;
  return candidate.headerName === "X-XSRF-TOKEN" && typeof candidate.token === "string" && candidate.token.length > 0;
}

function isRegistrationResult(value: unknown): value is RegistrationResult {
  if (!value || typeof value !== "object") return false;
  const result = value as Record<string, unknown>;
  const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[1-8][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/i;
  const rfc3339 = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+-]\d{2}:\d{2})$/;
  return typeof result.applicationId === "string" && uuid.test(result.applicationId)
    && result.status === "PENDING_REVIEW"
    && typeof result.submittedAt === "string" && rfc3339.test(result.submittedAt)
    && !Number.isNaN(Date.parse(result.submittedAt))
    && result.reviewRole === "商务专员"
    && Array.isArray(result.supportChannels) && result.supportChannels.length > 0
    && result.supportChannels.every(channel => channel === "企业微信" || channel === "邮件")
    && typeof result.nextAction === "string" && result.nextAction.trim().length > 0
    && typeof result.requestId === "string" && result.requestId.trim().length > 0;
}

function normalizeError(value: unknown, fallback: string): ApiError {
  if (value && typeof value === "object") {
    const candidate = value as Partial<ApiError>;
    if (typeof candidate.code === "string" && typeof candidate.message === "string") {
      const details = Array.isArray(candidate.details) ? candidate.details.filter(item => {
        if (!item || typeof item !== "object") return false;
        const detail = item as Record<string, unknown>;
        return typeof detail.field === "string" && detail.field.length > 0
          && typeof detail.code === "string" && detail.code.length > 0
          && typeof detail.message === "string" && detail.message.length > 0;
      }) as ApiError["details"] : [];
      return { code: candidate.code, message: candidate.message,
        requestId: typeof candidate.requestId === "string" ? candidate.requestId : "",
        details, retryable: Boolean(candidate.retryable) };
    }
  }
  return clientError("NETWORK_ERROR", fallback, true);
}

function clientError(code: string, message: string, retryable = false): ApiError {
  return { code, message, requestId: "", details: [], retryable };
}
