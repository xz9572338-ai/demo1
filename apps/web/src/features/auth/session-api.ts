import createClient from "openapi-fetch";
import type { components, paths } from "../../generated/api/console-v1";

export type SessionContext = components["schemas"]["SessionContext"];
export type ApiError = components["schemas"]["Error"];
const client = createClient<paths>({ baseUrl: `${globalThis.location?.origin ?? ""}/console/api/v1`,
  fetch: (request: Request) => globalThis.fetch(request) });

export async function login(login: string, password: string): Promise<SessionContext> {
  return withDeadline(async signal => {
  const csrf = await client.GET("/sessions/csrf", { signal });
  if (csrf.error || !csrf.data?.token) throw normalize(csrf.error, "安全令牌初始化失败，请重试");
  const response = await client.POST("/sessions", { signal, headers: { [csrf.data.headerName]: csrf.data.token },
    body: { login, password } });
  if (response.error) throw normalize(response.error, "登录暂时无法完成，请稍后重试");
  if (!validSession(response.data)) throw error("INVALID_RESPONSE", "服务响应格式异常，请稍后重试");
  return response.data;
  });
}

export async function currentSession(): Promise<SessionContext> {
  return withDeadline(async signal => {
    const response = await client.GET("/session", { signal });
    if (response.error) throw normalize(response.error, "会话校验暂时无法完成，请重试");
    if (!validSession(response.data)) throw error("INVALID_RESPONSE", "服务响应格式异常，请稍后重试");
    return response.data;
  });
}

export async function logout(): Promise<void> {
  return withDeadline(async signal => {
  const csrf = await client.GET("/sessions/csrf", { signal });
  if (csrf.error || !csrf.data?.token) throw normalize(csrf.error, "安全令牌初始化失败，请重试");
  const response = await client.DELETE("/session", { signal, headers: { [csrf.data.headerName]: csrf.data.token } });
  if (response.error && (response.error as ApiError).code !== "AUTHENTICATION_REQUIRED")
    throw normalize(response.error, "退出暂时无法完成，请重试");
  });
}

function validSession(value: unknown): value is SessionContext {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>;
  return typeof item.accountId === "string"
    && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(item.accountId)
    && ["PENDING_REVIEW", "REJECTED", "APPROVED"].includes(String(item.onboardingStatus))
    && ["/onboarding/status", "/dashboard"].includes(String(item.landingPath))
    && (item.onboardingStatus === "APPROVED" ? item.landingPath === "/dashboard" : item.landingPath === "/onboarding/status")
    && typeof item.requestId === "string" && item.requestId.length > 0;
}

function normalize(value: unknown, fallback: string): ApiError {
  if (value && typeof value === "object" && typeof (value as Record<string, unknown>).message === "string") return value as ApiError;
  return error("REQUEST_FAILED", fallback);
}
function error(code: string, message: string): ApiError { return { code, message, requestId: "client", details: [], retryable: true }; }

async function withDeadline<T>(operation: (signal: AbortSignal) => Promise<T>): Promise<T> {
  const controller = new AbortController();
  const timer = globalThis.setTimeout(() => controller.abort(), 10_000);
  try { return await operation(controller.signal); }
  catch (problem) {
    if (problem instanceof DOMException && problem.name === "AbortError") throw error("REQUEST_TIMEOUT", "请求超时，请重试");
    throw problem;
  } finally { globalThis.clearTimeout(timer); }
}
