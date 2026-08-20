import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import axe from "axe-core";
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { App } from "./App";

const responsiveCss = readFileSync(resolve(process.cwd(), "src/styles/globals.css"), "utf8");
const docsContract = JSON.parse(readFileSync(resolve(process.cwd(), "public/api-docs-contract.json"), "utf8"));
const docsExamples = JSON.parse(readFileSync(resolve(process.cwd(), "public/api-docs-examples.json"), "utf8"));
const docsVector = JSON.parse(readFileSync(resolve(process.cwd(), "public/api-docs-signing-vector.json"), "utf8"));

describe("App", () => {
  beforeEach(() => { vi.restoreAllMocks(); globalThis.history.replaceState({}, "", "/"); });
  afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

  const json = (body: unknown, status: number) => new Response(JSON.stringify(body), {
    status, headers: { "Content-Type": "application/json" },
  });
  const applicationCreated = () => ({ applicationId: crypto.randomUUID(), name: "订单同步", purpose: "沙箱联调",
    appId: "app_test_identifier_123456", appSecret: "secret-value-visible-once-123456789012345678",
    environment: "SANDBOX", status: "ACTIVE", secretShownOnce: true, createdAt: "2026-08-18T01:00:00Z",
    updatedAt: "2026-08-18T01:00:00Z", requestId: "req_create" });

  it("通过可访问字段完成注册并显示待审核结果", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ applicationId: crypto.randomUUID(), status: "PENDING_REVIEW", submittedAt: "2026-08-14T09:00:00Z", reviewRole: "商务专员", supportChannels: ["企业微信", "邮件"], nextAction: "等待审核", requestId: "req_test" }, 201));
    render(<App />);
    fireEvent.change(screen.getByLabelText("企业名称"), { target: { value: "示例供应链有限公司" } });
    fireEvent.change(screen.getByLabelText("联系人"), { target: { value: "张晓英" } });
    fireEvent.change(screen.getByLabelText("手机号"), { target: { value: "13812345678" } });
    fireEvent.change(screen.getByLabelText("账号"), { target: { value: "xiaoying" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "correct horse battery staple" } });
    fireEvent.click(screen.getByRole("button", { name: "提交入驻申请" }));
    expect(await screen.findByRole("heading", { name: "申请已提交" })).toHaveFocus();
    expect(screen.getByText("待审核")).toBeInTheDocument();
    expect(screen.getByText(/2026年8月14日 17:00/)).toBeInTheDocument();
  });

  it("字段错误就近显示、聚焦首个错误并保留非密码输入", async () => {
    vi.spyOn(globalThis, "requestAnimationFrame").mockImplementation(callback => { callback(0); return 1; });
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ code: "VALIDATION_FAILED", message: "请检查填写内容", requestId: "req_error", retryable: false, details: [{ field: "contactMobile", code: "PATTERN", message: "请输入有效手机号" }] }, 400));
    render(<App />);
    fireEvent.change(screen.getByLabelText("企业名称"), { target: { value: "示例供应链有限公司" } });
    fireEvent.change(screen.getByLabelText("手机号"), { target: { value: "123" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "correct horse battery staple" } });
    fireEvent.click(screen.getByRole("button", { name: "提交入驻申请" }));
    await waitFor(() => expect(screen.getByLabelText("手机号")).toHaveFocus());
    expect(screen.getByLabelText("企业名称")).toHaveValue("示例供应链有限公司");
    expect(screen.getByLabelText("密码")).toHaveValue("");
    expect(screen.getByText("请输入有效手机号")).toBeInTheDocument();
  });

  it("同步重复提交只发送一次请求且表单满足基础可访问性", async () => {
    let resolveCsrf!: (response: Response) => void;
    const pending = new Promise<Response>(resolve => { resolveCsrf = resolve; });
    const fetch = vi.spyOn(globalThis, "fetch")
      .mockReturnValueOnce(pending)
      .mockResolvedValueOnce(json({ applicationId: crypto.randomUUID(), status: "PENDING_REVIEW",
        submittedAt: "2026-08-14T09:00:00Z", reviewRole: "商务专员", supportChannels: ["企业微信"],
        nextAction: "等待审核", requestId: "req_once" }, 201));
    render(<App />);
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "correct horse battery staple" } });
    const form = screen.getByRole("button", { name: "提交入驻申请" }).closest("form")!;
    fireEvent.submit(form); fireEvent.submit(form);
    await waitFor(() => expect(fetch).toHaveBeenCalledTimes(1));
    expect(screen.getAllByRole("textbox").every(input => input.hasAttribute("required"))).toBe(true);
    expect((await axe.run(document.body)).violations).toHaveLength(0);
    resolveCsrf(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200));
    expect(await screen.findByRole("heading", { name: "申请已提交" })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(2);
  });

  it("按 Unicode code point 接受 128 个非 BMP 密码字符并拒绝第 129 个", async () => {
    vi.spyOn(globalThis, "requestAnimationFrame").mockImplementation(callback => { callback(0); return 1; });
    const fetch = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ applicationId: crypto.randomUUID(), status: "PENDING_REVIEW",
        submittedAt: "2026-08-14T09:00:00Z", reviewRole: "商务专员", supportChannels: ["邮件"],
        nextAction: "等待审核", requestId: "req_unicode" }, 201));
    render(<App />);
    const password = screen.getByLabelText("密码");
    fireEvent.change(password, { target: { value: "😀".repeat(129) } });
    fireEvent.click(screen.getByRole("button", { name: "提交入驻申请" }));
    expect(password).toHaveFocus();
    expect(screen.getByText("密码长度须为 12–128 个 Unicode 字符")).toBeInTheDocument();
    expect(fetch).not.toHaveBeenCalled();
    fireEvent.change(password, { target: { value: "😀".repeat(128) } });
    fireEvent.click(screen.getByRole("button", { name: "提交入驻申请" }));
    expect(await screen.findByRole("heading", { name: "申请已提交" })).toBeInTheDocument();
  });

  it("非 JSON 网络错误可恢复且密码恢复隐藏", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(new Response("gateway unavailable", { status: 502, headers: { "Content-Type": "text/plain" } }));
    render(<App />);
    fireEvent.click(screen.getByRole("button", { name: "显示密码" }));
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "超长安全密码用于网络失败测试123" } });
    fireEvent.click(screen.getByRole("button", { name: "提交入驻申请" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("申请暂时无法提交");
    expect(screen.getByLabelText("密码")).toHaveAttribute("type", "password");
  });

  it("忽略畸形字段错误明细，不让错误处理二次崩溃", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ code: "VALIDATION_FAILED", message: "请检查填写内容", requestId: "req_error", retryable: false,
        details: [null, { field: "contactMobile" }, { field: "username", code: "INVALID", message: "账号无效" }] }, 400));
    render(<App />);
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "correct horse battery staple" } });
    fireEvent.click(screen.getByRole("button", { name: "提交入驻申请" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("请检查填写内容");
    expect(screen.getByText("账号无效")).toBeInTheDocument();
  });

  it("拒绝字段不完整的 201 成功载荷", async () => {
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ applicationId: "not-a-uuid", status: "PENDING_REVIEW", submittedAt: "today",
        reviewRole: "商务专员", supportChannels: [], nextAction: "", requestId: "" }, 201));
    render(<App />);
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "correct horse battery staple" } });
    fireEvent.click(screen.getByRole("button", { name: "提交入驻申请" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("服务响应格式异常");
    expect(screen.queryByRole("heading", { name: "申请已提交" })).not.toBeInTheDocument();
  });

  it("请求超时后显示可重试提示", async () => {
    vi.useFakeTimers();
    vi.spyOn(globalThis, "fetch").mockImplementation((input, init) => new Promise((_resolve, reject) => {
      const signal = input instanceof Request ? input.signal : init?.signal;
      signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
    }));
    render(<App />);
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "correct horse battery staple" } });
    fireEvent.click(screen.getByRole("button", { name: "提交入驻申请" }));
    await vi.advanceTimersByTimeAsync(10_001);
    vi.useRealTimers();
    expect(await screen.findByRole("alert")).toHaveTextContent("请求超时");
  });

  it("待审核账号登录后进入受限审核状态路由且清空密码", async () => {
    globalThis.history.replaceState({}, "", "/login");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "PENDING_REVIEW",
        landingPath: "/onboarding/status", requestId: "req_login" }, 200))
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "PENDING_REVIEW",
        landingPath: "/onboarding/status", requestId: "req_session" }, 200))
      .mockResolvedValueOnce(json({ status: "PENDING_REVIEW", submittedAt: "2026-08-14T09:00:00Z",
        updatedAt: "2026-08-17T09:00:00Z", rejectionReason: null, reviewRole: "商务专员",
        supportChannels: ["企业微信", "邮件"], nextAction: "等待商务专员审核", requestId: "req_status" }, 200));
    render(<App />);
    fireEvent.change(screen.getByLabelText("账号或手机号"), { target: { value: "xiaoying" } });
    const password = screen.getByLabelText("密码");
    fireEvent.change(password, { target: { value: "correct horse battery staple" } });
    fireEvent.click(screen.getByRole("button", { name: "登录" }));
    expect(await screen.findByRole("heading", { name: "查看入驻审核状态" })).toBeInTheDocument();
    expect(globalThis.location.pathname).toBe("/onboarding/status");
  });

  it("凭据失败显示通用错误并清空密码", async () => {
    globalThis.history.replaceState({}, "", "/login");
    vi.spyOn(globalThis, "requestAnimationFrame").mockImplementation(callback => { callback(0); return 1; });
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ code: "INVALID_CREDENTIALS", message: "账号或密码不正确",
        requestId: "req_bad", retryable: false, details: [] }, 401));
    render(<App />);
    fireEvent.change(screen.getByLabelText("账号或手机号"), { target: { value: "xiaoying" } });
    const password = screen.getByLabelText("密码");
    fireEvent.change(password, { target: { value: "wrong password long enough" } });
    fireEvent.click(screen.getByRole("button", { name: "登录" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("账号或密码不正确");
    expect(password).toHaveValue("");
    expect(screen.getByRole("alert")).toHaveFocus();
  });

  it("审核通过账号登录后进入平台总览", async () => {
    globalThis.history.replaceState({}, "", "/login");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "APPROVED",
        landingPath: "/dashboard", requestId: "req_approved" }, 200))
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "APPROVED",
        landingPath: "/dashboard", requestId: "req_session" }, 200));
    render(<App />);
    fireEvent.change(screen.getByLabelText("账号或手机号"), { target: { value: "approved_user" } });
    fireEvent.change(screen.getByLabelText("密码"), { target: { value: "correct horse battery staple" } });
    fireEvent.click(screen.getByRole("button", { name: "登录" }));
    expect(await screen.findByRole("heading", { name: "平台总览" })).toBeInTheDocument();
    expect(globalThis.location.pathname).toBe("/dashboard");
  });

  it("匿名直达受保护路由时返回登录页且不渲染受保护内容", async () => {
    globalThis.history.replaceState({}, "", "/dashboard");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(json({ code: "AUTHENTICATION_REQUIRED", message: "请先登录",
      requestId: "req_anonymous", retryable: false, details: [] }, 401));
    render(<App />);
    expect(await screen.findByRole("heading", { name: "登录开放平台" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "平台总览" })).not.toBeInTheDocument();
    expect(globalThis.location.pathname).toBe("/login");
  });

  it("驳回账号由服务端会话状态守卫进入审核状态页", async () => {
    globalThis.history.replaceState({}, "", "/onboarding/status");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "REJECTED",
      landingPath: "/onboarding/status", requestId: "req_rejected" }, 200))
      .mockResolvedValueOnce(json({ status: "REJECTED", submittedAt: "2026-08-14T09:00:00Z",
        updatedAt: "2026-08-17T09:00:00Z", rejectionReason: "企业材料待补充", reviewRole: "商务专员",
        supportChannels: ["企业微信", "邮件"], nextAction: "联系技术对接负责人", requestId: "req_status" }, 200));
    render(<App />);
    expect(await screen.findByRole("heading", { name: "查看入驻审核状态" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("已驳回");
    expect(screen.getByText("企业材料待补充")).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "重新提交" })).not.toBeInTheDocument();
  });

  it("审核通过账号仍可直接查看最新审核结果并进入平台", async () => {
    globalThis.history.replaceState({}, "", "/onboarding/status");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "APPROVED",
        landingPath: "/dashboard", requestId: "req_approved_status" }, 200))
      .mockResolvedValueOnce(json({ status: "APPROVED", submittedAt: "2026-08-14T09:00:00Z",
        updatedAt: "2026-08-17T09:00:00Z", rejectionReason: null, reviewRole: "商务专员",
        supportChannels: ["企业微信", "邮件"], nextAction: "进入平台创建应用", requestId: "req_status" }, 200))
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "APPROVED",
        landingPath: "/dashboard", requestId: "req_dashboard" }, 200));
    render(<App />);
    expect(await screen.findByRole("status")).toHaveTextContent("已通过");
    const enter = screen.getByRole("button", { name: "进入平台" }); enter.focus(); expect(enter).toHaveFocus();
    enter.click(); expect(await screen.findByRole("heading", { name: "平台总览" })).toBeInTheDocument();
  });

  it("状态接口 503 时显示可访问错误并可重试成功", async () => {
    globalThis.history.replaceState({}, "", "/onboarding/status");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "PENDING_REVIEW",
        landingPath: "/onboarding/status", requestId: "req_session" }, 200))
      .mockResolvedValueOnce(json({ code: "AUTH_SERVICE_UNAVAILABLE", message: "认证服务暂时不可用，请稍后重试",
        requestId: "req_503", details: [], retryable: true }, 503))
      .mockResolvedValueOnce(json({ status: "PENDING_REVIEW", submittedAt: "2026-08-14T09:00:00Z",
        updatedAt: "2026-08-17T09:00:00Z", rejectionReason: null, reviewRole: "商务专员",
        supportChannels: ["企业微信", "邮件"], nextAction: "等待商务专员审核", requestId: "req_status" }, 200));
    render(<App />);
    expect(await screen.findByRole("alert")).toHaveTextContent("认证服务暂时不可用");
    fireEvent.click(screen.getByRole("button", { name: "重试" }));
    expect(await screen.findByRole("status")).toHaveTextContent("待审核");
  });

  it("状态接口返回 401 时跳回登录", async () => {
    globalThis.history.replaceState({}, "", "/onboarding/status");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "PENDING_REVIEW",
        landingPath: "/onboarding/status", requestId: "req_session" }, 200))
      .mockResolvedValueOnce(json({ code: "AUTHENTICATION_REQUIRED", message: "请先登录",
        requestId: "req_401", details: [], retryable: false }, 401));
    render(<App />);
    expect(await screen.findByRole("heading", { name: "登录开放平台" })).toBeInTheDocument();
    expect(globalThis.location.pathname).toBe("/login");
  });

  it.each([
    { updatedAt: "2026-02-31T09:00:00Z", rejectionReason: null, status: "PENDING_REVIEW" },
    { updatedAt: "2026-08-17T09:00:00Z", rejectionReason: "驳".repeat(501), status: "REJECTED" },
    { updatedAt: "2026-08-17T09:00:00Z", rejectionReason: "😀".repeat(501), status: "REJECTED" }
  ])("拒绝违反契约的审核状态响应 %#", async invalid => {
    globalThis.history.replaceState({}, "", "/onboarding/status");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: invalid.status,
        landingPath: "/onboarding/status", requestId: "req_session" }, 200))
      .mockResolvedValueOnce(json({ ...invalid, submittedAt: "2026-02-01T09:00:00Z", reviewRole: "商务专员",
        supportChannels: ["企业微信", "邮件"], nextAction: "等待处理", requestId: "req_invalid" }, 200));
    render(<App />);
    expect(await screen.findByRole("alert")).toHaveTextContent("服务响应格式异常");
  });

  it("按 Unicode 码点接受 500 字符的驳回原因", async () => {
    globalThis.history.replaceState({}, "", "/onboarding/status");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "REJECTED",
        landingPath: "/onboarding/status", requestId: "req_session" }, 200))
      .mockResolvedValueOnce(json({ status: "REJECTED", submittedAt: "2026-08-14T09:00:00Z",
        updatedAt: "2026-08-17T09:00:00Z", rejectionReason: "😀".repeat(500), reviewRole: "商务专员",
        supportChannels: ["企业微信", "邮件"], nextAction: "联系商务专员", requestId: "req_status" }, 200));
    render(<App />);
    expect(await screen.findByRole("heading", { name: "查看入驻审核状态" })).toBeInTheDocument();
    expect(screen.getByRole("status")).toHaveTextContent("已驳回");
  });

  it("状态页退出失败可见且按钮恢复可用", async () => {
    globalThis.history.replaceState({}, "", "/onboarding/status");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "PENDING_REVIEW",
        landingPath: "/onboarding/status", requestId: "req_session" }, 200))
      .mockResolvedValueOnce(json({ status: "PENDING_REVIEW", submittedAt: "2026-08-14T09:00:00Z",
        updatedAt: "2026-08-17T09:00:00Z", rejectionReason: null, reviewRole: "商务专员",
        supportChannels: ["企业微信", "邮件"], nextAction: "等待商务专员审核", requestId: "req_status" }, 200))
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ code: "AUTH_SERVICE_UNAVAILABLE", message: "退出暂时不可用",
        requestId: "req_logout", details: [], retryable: true }, 503));
    render(<App />); const button = await screen.findByRole("button", { name: "退出登录" });
    button.focus(); expect(button).toHaveFocus(); fireEvent.click(button);
    expect(await screen.findByRole("alert")).toHaveTextContent("退出暂时不可用");
    expect(screen.getByRole("button", { name: "退出登录" })).toBeEnabled();
  });

  it("状态页主操作保持 DOM 顺序且成功退出返回登录页", async () => {
    globalThis.history.replaceState({}, "", "/onboarding/status");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "APPROVED",
        landingPath: "/dashboard", requestId: "req_session" }, 200))
      .mockResolvedValueOnce(json({ status: "APPROVED", submittedAt: "2026-08-14T09:00:00Z",
        updatedAt: "2026-08-17T09:00:00Z", rejectionReason: null, reviewRole: "商务专员",
        supportChannels: ["企业微信", "邮件"], nextAction: "进入平台创建应用", requestId: "req_status" }, 200))
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(new Response(null, { status: 204 }));
    render(<App />); await screen.findByRole("status");
    const enter = screen.getByRole("button", { name: "进入平台" });
    const exit = screen.getByRole("button", { name: "退出登录" });
    expect(enter.compareDocumentPosition(exit) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    enter.focus(); fireEvent.keyDown(enter, { key: "Tab" }); expect(exit).toHaveFocus();
    fireEvent.keyDown(exit, { key: "Tab", shiftKey: true }); expect(enter).toHaveFocus();
    exit.focus(); fireEvent.keyDown(exit, { key: "Enter" });
    expect(await screen.findByRole("heading", { name: "登录开放平台" })).toBeInTheDocument();
  });

  it("服务端状态与落点不一致时拒绝渲染会话", async () => {
    globalThis.history.replaceState({}, "", "/dashboard");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "PENDING_REVIEW",
      landingPath: "/dashboard", requestId: "req_mismatch" }, 200));
    render(<App />);
    expect(await screen.findByRole("heading", { name: "会话校验未完成" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "平台总览" })).not.toBeInTheDocument();
  });

  it("拒绝非规范 UUID 的会话载荷", async () => {
    globalThis.history.replaceState({}, "", "/dashboard");
    vi.spyOn(globalThis, "fetch").mockResolvedValueOnce(json({ accountId: "12345678-1234-1234-1234-12345678901-",
      onboardingStatus: "APPROVED", landingPath: "/dashboard", requestId: "req_bad_uuid" }, 200));
    render(<App />);
    expect(await screen.findByRole("heading", { name: "会话校验未完成" })).toBeInTheDocument();
    expect(screen.queryByRole("heading", { name: "平台总览" })).not.toBeInTheDocument();
  });

  it.each([320, 390])("在 %ipx 窄屏使用单列且保留无横向溢出的 reflow 契约", width => {
    Object.defineProperty(globalThis, "innerWidth", { configurable: true, value: width });
    globalThis.dispatchEvent(new Event("resize"));
    const css = responsiveCss;
    expect(css).toMatch(/body\s*\{[^}]*min-width:\s*320px/);
    expect(css).toMatch(/\.registration-shell\s*\{[^}]*width:\s*min\(1080px,\s*calc\(100%\s*-\s*32px\)\)/);
    expect(css).toMatch(/@media\s*\(max-width:\s*760px\)[\s\S]*?\.registration-shell\s*\{[^}]*grid-template-columns:\s*1fr/);
  });

  it("200% 缩放契约使用流式宽度和可收缩网格列", () => {
    const css = responsiveCss;
    expect(css).toMatch(/grid-template-columns:\s*minmax\(0,\s*\.8fr\)\s+minmax\(320px,\s*1fr\)/);
    expect(css).toMatch(/\.form-field input\s*\{[^}]*width:\s*100%/);
    expect(css).not.toMatch(/\.registration-shell\s*\{[^}]*width:\s*\d+px/);
  });

  it("登录字段标签关联、Tab 顺序和 Enter 提交均可执行", async () => {
    globalThis.history.replaceState({}, "", "/login");
    const fetch = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "APPROVED",
        landingPath: "/dashboard", requestId: "req_keyboard" }, 200))
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "APPROVED",
        landingPath: "/dashboard", requestId: "req_keyboard_guard" }, 200));
    render(<App />);
    const identifier = screen.getByLabelText("账号或手机号");
    const password = screen.getByLabelText("密码");
    const submit = screen.getByRole("button", { name: "登录" });
    expect(identifier).toHaveAttribute("id", "login-identifier");
    expect(password).toHaveAttribute("id", "login-password");
    const controls = Array.from(document.querySelectorAll<HTMLElement>("input, button")).filter(item => !item.hasAttribute("disabled"));
    expect(controls.slice(0, 3)).toEqual([identifier, password, submit]);
    identifier.focus(); fireEvent.keyDown(identifier, { key: "Tab" }); password.focus();
    fireEvent.change(identifier, { target: { value: "approved_user" } });
    fireEvent.change(password, { target: { value: "correct horse battery staple" } });
    fireEvent.keyDown(password, { key: "Enter", code: "Enter" });
    fireEvent.submit(password.closest("form")!);
    expect(await screen.findByRole("heading", { name: "平台总览" })).toBeInTheDocument();
    expect(fetch).toHaveBeenCalledTimes(3);
  });

  it("退出期间阻止重复操作并在会话终止后返回登录页", async () => {
    globalThis.history.replaceState({}, "", "/dashboard");
    let finishLogout!: (response: Response) => void;
    const pendingLogout = new Promise<Response>(resolve => { finishLogout = resolve; });
    const fetch = vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({ accountId: crypto.randomUUID(), onboardingStatus: "APPROVED",
        landingPath: "/dashboard", requestId: "req_session" }, 200))
      .mockResolvedValueOnce(json({ headerName: "X-XSRF-TOKEN", token: "token" }, 200))
      .mockReturnValueOnce(pendingLogout);
    render(<App />);
    const button = await screen.findByRole("button", { name: "退出登录" });
    fireEvent.click(button); fireEvent.click(button);
    expect(await screen.findByRole("button", { name: "正在退出…" })).toBeDisabled();
    expect(fetch).toHaveBeenCalledTimes(3);
    finishLogout(new Response(null, { status: 204 }));
    expect(await screen.findByRole("heading", { name: "登录开放平台" })).toBeInTheDocument();
  });

  it("创建唯一应用后仅在当前页面展示并复制沙箱密钥", async () => {
    globalThis.history.replaceState({}, "", "/applications");
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => undefined);
    const writeText=vi.fn().mockResolvedValue(undefined);Object.defineProperty(navigator,"clipboard",{value:{writeText},configurable:true});
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_session"},200))
      .mockResolvedValueOnce(json([],200))
      .mockResolvedValueOnce(json({headerName:"X-XSRF-TOKEN",token:"token"},200))
      .mockResolvedValueOnce(json(applicationCreated(),201));
    render(<App/>);fireEvent.change(await screen.findByLabelText("应用名称"),{target:{value:"订单同步"}});fireEvent.change(screen.getByLabelText("应用用途"),{target:{value:"沙箱联调"}});fireEvent.click(screen.getByRole("button",{name:"创建应用并领取密钥"}));
    expect(await screen.findByRole("heading",{name:"请立即保存应用密钥"})).toBeInTheDocument();expect(screen.getByText("secret-value-visible-once-123456789012345678")).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:"复制 AppSecret"}));await waitFor(()=>expect(writeText).toHaveBeenCalledWith("secret-value-visible-once-123456789012345678"));expect(screen.getByText("AppSecret复制成功")).toBeInTheDocument();
    const finish=screen.getByRole("button",{name:"完成并继续"});expect(finish).toBeDisabled();fireEvent.click(screen.getByLabelText("我已安全保存密钥"));expect(finish).toBeEnabled();fireEvent.click(finish);expect(screen.queryByText("secret-value-visible-once-123456789012345678")).not.toBeInTheDocument();
    expect(consoleError.mock.calls.flat().join(" ")).not.toContain("secret-value-visible-once");
  });

  it("应用列表失败时保持只读错误态并可重试", async () => {
    globalThis.history.replaceState({}, "", "/applications");
    vi.spyOn(globalThis, "fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_session"},200))
      .mockResolvedValueOnce(json({code:"AUTH_SERVICE_UNAVAILABLE",message:"应用服务暂时不可用",requestId:"req_list",details:[],retryable:true},503))
      .mockResolvedValueOnce(json([],200));
    render(<App/>);
    expect(await screen.findByRole("heading", {name:"应用暂时无法加载"})).toBeInTheDocument();
    expect(screen.queryByRole("button", {name:"创建应用并领取密钥"})).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button", {name:"重新加载"}));
    expect(await screen.findByRole("heading", {name:"创建对接应用"})).toBeInTheDocument();
  });

  it("未确认密钥时拦截离开、播报复制失败并支持键盘主流程", async () => {
    globalThis.history.replaceState({}, "", "/applications");
    const confirm = vi.fn().mockReturnValue(false); vi.stubGlobal("confirm", confirm);
    const forward = vi.spyOn(globalThis.history, "forward").mockImplementation(() => undefined);
    Object.defineProperty(navigator,"clipboard",{value:{writeText:vi.fn().mockRejectedValue(new Error("denied"))},configurable:true});
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_session"},200))
      .mockResolvedValueOnce(json([],200))
      .mockResolvedValueOnce(json({headerName:"X-XSRF-TOKEN",token:"token"},200))
      .mockResolvedValueOnce(json(applicationCreated(),201));
    render(<App/>);
    const name = await screen.findByLabelText("应用名称"); const purpose = screen.getByLabelText("应用用途");
    fireEvent.change(name,{target:{value:"订单同步"}}); fireEvent.change(purpose,{target:{value:"沙箱联调"}});
    fireEvent.keyDown(purpose,{key:"Enter",code:"Enter"}); fireEvent.submit(purpose.closest("form")!);
    expect(await screen.findByRole("heading",{name:"请立即保存应用密钥"})).toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:"复制 AppSecret"}));
    expect(await screen.findByText("AppSecret复制失败，请手动复制")).toBeInTheDocument();
    const unload = new Event("beforeunload", {cancelable:true}); globalThis.dispatchEvent(unload); expect(unload.defaultPrevented).toBe(true);
    globalThis.dispatchEvent(new PopStateEvent("popstate")); expect(confirm).toHaveBeenCalledTimes(1); expect(forward).toHaveBeenCalledOnce();
    globalThis.dispatchEvent(new PopStateEvent("popstate")); expect(confirm).toHaveBeenCalledTimes(1);
    globalThis.history.pushState({}, "", "/dashboard"); expect(confirm).toHaveBeenCalledTimes(2); expect(globalThis.location.pathname).toBe("/applications");
    Object.defineProperty(globalThis,"innerWidth",{value:640,configurable:true}); document.documentElement.style.zoom="2";
    expect(screen.getByRole("button",{name:"复制 AppID"})).toBeVisible(); expect(screen.getByRole("button",{name:"复制 AppSecret"})).toBeVisible();
    expect(document.documentElement.scrollWidth).toBeLessThanOrEqual(document.documentElement.clientWidth);
    const checkbox = screen.getByLabelText("我已安全保存密钥"); checkbox.focus(); fireEvent.keyDown(checkbox,{key:" ",code:"Space"}); fireEvent.click(checkbox);
    const finish = screen.getByRole("button",{name:"完成并继续"}); finish.focus(); fireEvent.keyDown(finish,{key:"Enter",code:"Enter"}); fireEvent.click(finish);
    expect(screen.queryByText(applicationCreated().appSecret)).not.toBeInTheDocument();
    expect((await axe.run(document.body)).violations).toHaveLength(0);
    expect(responsiveCss).toContain("overflow-wrap: anywhere");
    expect(responsiveCss).toContain("@media (max-width: 760px)");
    document.documentElement.style.zoom="";
    vi.unstubAllGlobals();
  });

  it("创建请求进行中阻止刷新、后退和站内导航", async () => {
    globalThis.history.replaceState({}, "", "/applications");
    const confirm = vi.fn().mockReturnValue(false); vi.stubGlobal("confirm", confirm);
    let finishCreate!: (response: Response) => void; const pending = new Promise<Response>(resolve => { finishCreate=resolve; });
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_session"},200))
      .mockResolvedValueOnce(json([],200))
      .mockResolvedValueOnce(json({headerName:"X-XSRF-TOKEN",token:"token"},200))
      .mockReturnValueOnce(pending);
    render(<App/>); fireEvent.change(await screen.findByLabelText("应用名称"),{target:{value:"订单同步"}});
    fireEvent.change(screen.getByLabelText("应用用途"),{target:{value:"沙箱联调"}}); fireEvent.click(screen.getByRole("button",{name:"创建应用并领取密钥"}));
    expect(await screen.findByRole("button",{name:"正在创建…"})).toBeDisabled();
    const unload = new Event("beforeunload",{cancelable:true}); globalThis.dispatchEvent(unload); expect(unload.defaultPrevented).toBe(true);
    globalThis.dispatchEvent(new PopStateEvent("popstate")); globalThis.history.pushState({},"","/dashboard");
    expect(confirm).toHaveBeenCalledTimes(2); expect(globalThis.location.pathname).toBe("/applications");
    finishCreate(json(applicationCreated(),201)); expect(await screen.findByRole("heading",{name:"请立即保存应用密钥"})).toBeInTheDocument();
  });

  it("刷新后只恢复非敏感应用信息", async () => {
    globalThis.history.replaceState({}, "", "/applications");
    const existing = applicationCreated(); const {appSecret:_,secretShownOnce:__,...safe}=existing;
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_session"},200))
      .mockResolvedValueOnce(json([safe],200));
    render(<App/>);
    expect(await screen.findByRole("heading",{name:"订单同步"})).toBeInTheDocument();
    expect(screen.queryByText(existing.appSecret)).not.toBeInTheDocument();
    expect(screen.getByText(/不可再次查看/)).toBeInTheDocument();
    expect(screen.getByRole("link", {name:"申请接口权限"})).toHaveAttribute("href", "/permissions");
  });

  it("逐项申请权限并展示待审、通过和驳回", async () => {
    globalThis.history.replaceState({},"","/permissions");
    const existing=applicationCreated();const {appSecret:_,secretShownOnce:__,...safe}=existing;
    const permissions=[
      {code:"CUSTOMER_BASE_READ",name:"客户基础信息",purpose:"查询客户",dataScope:"当前客户",sensitiveNotice:"脱敏",status:"NOT_APPLIED",submittedAt:null,updatedAt:null,rejectionReason:null,requestId:"req_p"},
      {code:"ORDER_LIST_READ",name:"订单列表",purpose:"查询订单",dataScope:"当前客户",sensitiveNotice:"交易数据",status:"APPROVED",submittedAt:"2026-08-18T01:00:00Z",updatedAt:"2026-08-18T02:00:00Z",rejectionReason:null,requestId:"req_p"},
      {code:"ORDER_DETAIL_READ",name:"订单详情",purpose:"查询详情",dataScope:"当前客户",sensitiveNotice:"脱敏收货信息",status:"REJECTED",submittedAt:"2026-08-18T01:00:00Z",updatedAt:"2026-08-18T02:00:00Z",rejectionReason:"请说明业务场景",requestId:"req_p"}
    ];
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_session"},200))
      .mockResolvedValueOnce(json([safe],200)).mockResolvedValueOnce(json(permissions,200))
      .mockResolvedValueOnce(json({headerName:"X-XSRF-TOKEN",token:"token"},200))
      .mockResolvedValueOnce(json([{...permissions[0],status:"PENDING_REVIEW"}],200))
      .mockResolvedValueOnce(json([safe],200))
      .mockResolvedValueOnce(json([{...permissions[0],status:"PENDING_REVIEW"},permissions[1],permissions[2]],200));
    render(<App/>);expect(await screen.findByRole("heading",{name:"申请三项查询接口"})).toBeInTheDocument();
    expect(screen.getByText("已通过")).toBeInTheDocument();expect(screen.getByText(/请说明业务场景/)).toBeInTheDocument();
    expect(screen.getAllByText(/提交时间/).length).toBeGreaterThan(0);expect(screen.getAllByText(/更新时间/).length).toBeGreaterThan(0);
    const checkbox=screen.getByRole("checkbox",{name:/客户基础信息/});checkbox.focus();fireEvent.keyDown(checkbox,{key:" ",code:"Space"});fireEvent.click(checkbox);
    fireEvent.change(screen.getByLabelText("业务申请原因"),{target:{value:"供应链客户同步"}});fireEvent.click(screen.getByRole("button",{name:"提交权限申请"}));
    await waitFor(()=>expect(screen.getByText("待审核")).toBeInTheDocument());expect((await axe.run(document.body)).violations).toHaveLength(0);
  });

  it("按 Unicode 码点校验原因，并在提交成功后的刷新失败中保留待审状态和重试入口", async () => {
    globalThis.history.replaceState({},"","/permissions");
    const existing=applicationCreated();const {appSecret:_,secretShownOnce:__,...safe}=existing;
    const base={code:"CUSTOMER_BASE_READ",name:"客户基础信息",purpose:"查询客户",dataScope:"当前客户",sensitiveNotice:"脱敏",status:"NOT_APPLIED",submittedAt:null,updatedAt:null,rejectionReason:null,requestId:"req_initial"} as const;
    const initial=[base,{...base,code:"ORDER_LIST_READ",name:"订单列表"},{...base,code:"ORDER_DETAIL_READ",name:"订单详情"}];
    const pending={...base,status:"PENDING_REVIEW",submittedAt:"2026-08-18T03:00:00Z",updatedAt:"2026-08-18T03:00:00Z",requestId:"req_submit"} as const;
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_session"},200))
      .mockResolvedValueOnce(json([safe],200)).mockResolvedValueOnce(json(initial,200))
      .mockResolvedValueOnce(json({headerName:"X-XSRF-TOKEN",token:"token"},200))
      .mockResolvedValueOnce(json([pending],200))
      .mockResolvedValueOnce(json([safe],200))
      .mockResolvedValueOnce(json({code:"PERMISSION_SERVICE_UNAVAILABLE",message:"权限服务暂时不可用",requestId:"req_refresh",details:[],retryable:true},503))
      .mockResolvedValueOnce(json([safe],200)).mockResolvedValueOnce(json([pending,initial[1],initial[2]],200));
    render(<App/>);
    const checkbox=await screen.findByRole("checkbox",{name:/客户基础信息/});fireEvent.click(checkbox);
    const reason=screen.getByLabelText("业务申请原因");fireEvent.change(reason,{target:{value:"😀".repeat(501)}});
    expect(screen.getByRole("alert")).toHaveTextContent("不能超过 500");expect(screen.getByRole("button",{name:"提交权限申请"})).toBeDisabled();
    fireEvent.change(reason,{target:{value:"😀".repeat(500)}});expect(screen.getByText("最多 500 个 Unicode 字符（500/500）")).toBeInTheDocument();
    fireEvent.submit(reason.closest("form")!);
    expect(await screen.findByText("待审核")).toBeInTheDocument();
    expect(await screen.findByRole("alert")).toHaveTextContent("req_refresh");
    const retry=screen.getByRole("button",{name:"刷新权限状态"});retry.focus();fireEvent.keyDown(retry,{key:"Enter",code:"Enter"});fireEvent.click(retry);
    await waitFor(()=>expect(screen.queryByText(/req_refresh/)).not.toBeInTheDocument());
  });

  it("仅允许已通过会话查看静态 API 文档并复制示例", async () => {
    globalThis.history.replaceState({}, "", "/api-docs");
    const writeText=vi.fn().mockResolvedValue(undefined);Object.defineProperty(navigator,"clipboard",{value:{writeText},configurable:true});
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_docs"},200))
      .mockResolvedValueOnce(json(docsContract,200)).mockResolvedValueOnce(json(docsExamples,200)).mockResolvedValueOnce(json(docsVector,200));
    render(<App/>);
    expect(await screen.findByRole("heading",{name:"用同一份契约完成沙箱接入"})).toBeInTheDocument();
    expect(screen.getAllByText("GET")).toHaveLength(3);expect(screen.getByText("/orders/{orderId}")).toBeInTheDocument();
    expect(screen.getByText(docsVector.expectedSignature)).toBeInTheDocument();expect(screen.getByText(docsVector.appSecret)).toBeInTheDocument();expect(screen.getByText("公共模型")).toBeInTheDocument();
    const copy=screen.getByRole("button",{name:"复制 Python 示例"});copy.focus();fireEvent.keyDown(copy,{key:"Enter",code:"Enter"});fireEvent.click(copy);
    await waitFor(()=>expect(writeText).toHaveBeenCalledWith(expect.stringContaining("import hashlib")));
    expect(screen.getByRole("status")).toHaveTextContent("Python 示例已复制");
    expect((await axe.run(document.body)).violations).toHaveLength(0);
  });

  it("文档复制被拒绝时保留源码并播报错误，未通过会话不能绕过", async () => {
    globalThis.history.replaceState({}, "", "/api-docs");
    Object.defineProperty(navigator,"clipboard",{value:{writeText:vi.fn().mockRejectedValue(new Error("denied"))},configurable:true});
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_docs"},200))
      .mockResolvedValueOnce(json(docsContract,200)).mockResolvedValueOnce(json(docsExamples,200)).mockResolvedValueOnce(json(docsVector,200));
    render(<App/>);fireEvent.click(await screen.findByRole("button",{name:"复制 cURL 示例"}));
    expect(await screen.findByRole("alert")).toHaveTextContent("复制失败");
    expect(screen.getAllByText(/OPEN_PLATFORM_APP_SECRET/).length).toBeGreaterThan(0);
    expect(responsiveCss).toContain(".endpoint-grid { grid-template-columns: 1fr; }");
    cleanup();globalThis.history.replaceState({},"","/api-docs");
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"PENDING_REVIEW",landingPath:"/onboarding/status",requestId:"req_pending"},200))
      .mockResolvedValueOnce(json({status:"PENDING_REVIEW",submittedAt:"2026-08-18T01:00:00Z",updatedAt:"2026-08-18T01:00:00Z",rejectionReason:null,reviewRole:"商务专员",supportChannels:["企业微信","邮件"],nextAction:"等待商务专员审核",requestId:"req_status"},200));
    render(<App/>);expect(await screen.findByRole("heading",{name:"查看入驻审核状态"})).toBeInTheDocument();
    expect(screen.queryByText("HMAC-SHA256 签名")).not.toBeInTheDocument();
  });

  it("文档资源失败显示 Request ID 并可重试且不保留陈旧内容", async () => {
    globalThis.history.replaceState({},"","/api-docs");
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_docs"},200))
      .mockResolvedValueOnce(json({},503)).mockResolvedValueOnce(json(docsExamples,200)).mockResolvedValueOnce(json(docsVector,200))
      .mockResolvedValueOnce(json(docsContract,200)).mockResolvedValueOnce(json(docsExamples,200)).mockResolvedValueOnce(json(docsVector,200));
    render(<App/>);expect(await screen.findByRole("heading",{name:"API 文档暂时无法加载"})).toBeInTheDocument();
    expect(screen.getByText(/Request ID：/)).toBeInTheDocument();expect(screen.getByText(/企业微信 \/ 邮件/)).toBeInTheDocument();expect(screen.queryByText("首发接口")).not.toBeInTheDocument();
    fireEvent.click(screen.getByRole("button",{name:"重新加载"}));expect(await screen.findByText("首发接口")).toBeInTheDocument();
  });

  it("文档资源结构损坏时进入可恢复错误态而不渲染空目录", async () => {
    globalThis.history.replaceState({},"","/api-docs");
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_docs"},200))
      .mockResolvedValueOnce(json({...docsContract,paths:{}},200)).mockResolvedValueOnce(json(docsExamples,200)).mockResolvedValueOnce(json(docsVector,200));
    render(<App/>);expect(await screen.findByRole("heading",{name:"API 文档暂时无法加载"})).toBeInTheDocument();
    expect(screen.getByText("API 文档资源格式异常")).toBeInTheDocument();expect(screen.queryByText("首发接口")).not.toBeInTheDocument();
  });

  it("剪贴板权限请求不结束时在五秒后恢复手动复制", async () => {
    globalThis.history.replaceState({},"","/api-docs");
    Object.defineProperty(navigator,"clipboard",{value:{writeText:vi.fn(()=>new Promise(()=>undefined))},configurable:true});
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"APPROVED",landingPath:"/dashboard",requestId:"req_docs"},200))
      .mockResolvedValueOnce(json(docsContract,200)).mockResolvedValueOnce(json(docsExamples,200)).mockResolvedValueOnce(json(docsVector,200));
    render(<App/>);const button=await screen.findByRole("button",{name:"复制 Java 示例"});fireEvent.click(button);
    await new Promise(resolve=>setTimeout(resolve,5100));
    expect(await screen.findByRole("alert")).toHaveTextContent("手动选择代码");expect(button).toBeEnabled();
  },7000);

  it("匿名、过期与驳回会话均不能访问 API 文档", async () => {
    for(const code of ["AUTHENTICATION_REQUIRED","INVALID_CREDENTIALS"]){
      cleanup();globalThis.history.replaceState({},"","/api-docs");
      vi.spyOn(globalThis,"fetch").mockResolvedValueOnce(json({code,message:"请重新登录",requestId:"req_auth",details:[],retryable:false},401));
      render(<App/>);expect(await screen.findByRole("heading",{name:"登录开放平台"})).toBeInTheDocument();expect(screen.queryByText("首发接口")).not.toBeInTheDocument();
    }
    cleanup();globalThis.history.replaceState({},"","/api-docs");
    vi.spyOn(globalThis,"fetch")
      .mockResolvedValueOnce(json({accountId:crypto.randomUUID(),onboardingStatus:"REJECTED",landingPath:"/onboarding/status",requestId:"req_rejected"},200))
      .mockResolvedValueOnce(json({status:"REJECTED",submittedAt:"2026-08-18T01:00:00Z",updatedAt:"2026-08-18T02:00:00Z",rejectionReason:"资质信息不完整",reviewRole:"商务专员",supportChannels:["企业微信"],nextAction:"联系商务专员",requestId:"req_status"},200));
    render(<App/>);expect(await screen.findByText("资质信息不完整")).toBeInTheDocument();expect(screen.queryByText("首发接口")).not.toBeInTheDocument();
  });
});
