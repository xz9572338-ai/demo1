import createClient from "openapi-fetch";
import type { components, paths } from "../../generated/api/console-v1";

export type OnboardingResult = components["schemas"]["OnboardingStatusResponse"];
type ApiError = components["schemas"]["Error"];
const client = createClient<paths>({ baseUrl: `${globalThis.location?.origin ?? ""}/console/api/v1`,
  fetch: (request: Request) => globalThis.fetch(request) });

export async function getOnboardingStatus(parentSignal?: AbortSignal): Promise<OnboardingResult> {
  const controller = new AbortController(); const abort = () => controller.abort();
  parentSignal?.addEventListener("abort", abort, { once: true });
  const timer = globalThis.setTimeout(abort, 10_000);
  try {
    const response = await client.GET("/onboarding/status", { signal: controller.signal });
    if (response.error) throw response.error;
    if (!valid(response.data)) throw apiError("INVALID_RESPONSE", "服务响应格式异常，请稍后重试");
    return response.data;
  } catch (problem) {
    if (controller.signal.aborted && !parentSignal?.aborted) throw apiError("REQUEST_TIMEOUT", "请求超时，请重试");
    throw problem;
  } finally {
    globalThis.clearTimeout(timer); parentSignal?.removeEventListener("abort", abort);
  }
}

function valid(value: unknown): value is OnboardingResult {
  if (!value || typeof value !== "object") return false;
  const item = value as Record<string, unknown>; const status = item.status;
  const validDate = (input: unknown) => {
    if (typeof input !== "string") return false;
    const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d{1,9})?(Z|[+-]\d{2}:\d{2})$/.exec(input);
    if (!match) return false;
    const [, year, month, day, hour, minute, second, zone] = match;
    const values = [year, month, day, hour, minute, second].map(Number);
    const calendar = new Date(Date.UTC(values[0], values[1] - 1, values[2], values[3], values[4], values[5]));
    const calendarValid = calendar.getUTCFullYear() === values[0] && calendar.getUTCMonth() === values[1] - 1
      && calendar.getUTCDate() === values[2] && calendar.getUTCHours() === values[3]
      && calendar.getUTCMinutes() === values[4] && calendar.getUTCSeconds() === values[5];
    const offsetValid = zone === "Z" || (Number(zone.slice(1, 3)) <= 23 && Number(zone.slice(4, 6)) <= 59);
    return calendarValid && offsetValid && Number.isFinite(Date.parse(input));
  };
  const channels = item.supportChannels;
  return ["PENDING_REVIEW", "APPROVED", "REJECTED"].includes(String(status))
    && validDate(item.submittedAt) && validDate(item.updatedAt)
    && Date.parse(String(item.updatedAt)) >= Date.parse(String(item.submittedAt))
    && item.reviewRole === "商务专员" && typeof item.nextAction === "string" && item.nextAction.length > 0
    && typeof item.requestId === "string" && item.requestId.length > 0
    && Array.isArray(channels) && channels.length > 0
    && channels.every(channel => channel === "企业微信" || channel === "邮件")
    && (status === "REJECTED" ? typeof item.rejectionReason === "string" && item.rejectionReason.trim().length > 0
      && Array.from(item.rejectionReason).length <= 500
      : item.rejectionReason === null);
}
function apiError(code: string, message: string): ApiError {
  return { code, message, requestId: "client", details: [], retryable: true };
}
