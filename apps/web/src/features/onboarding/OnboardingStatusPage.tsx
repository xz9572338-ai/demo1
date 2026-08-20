import { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { logout } from "../auth/session-api";
import { getOnboardingStatus, type OnboardingResult } from "./onboarding-status-api";

const labels = { PENDING_REVIEW: "待审核", APPROVED: "已通过", REJECTED: "已驳回" } as const;
export function OnboardingStatusPage() {
  const navigate = useNavigate(); const [result, setResult] = useState<OnboardingResult>();
  const [error, setError] = useState(""); const [actionError, setActionError] = useState("");
  const [attempt, setAttempt] = useState(0); const [signingOut, setSigningOut] = useState(false); const logoutLock = useRef(false);
  const enterRef = useRef<HTMLButtonElement>(null); const exitRef = useRef<HTMLButtonElement>(null);
  useEffect(() => { let active = true; const controller = new AbortController(); setError("");
    getOnboardingStatus(controller.signal).then(value => active && setResult(value))
    .catch(problem => { if (!active) return; const item = problem as { code?: string; message?: string };
      if (item.code === "AUTHENTICATION_REQUIRED" || item.code === "INVALID_CREDENTIALS") navigate("/login", { replace: true });
      else setError(item.message ?? "审核状态暂时无法加载，请稍后重试"); });
    return () => { active = false; controller.abort(); }; }, [attempt, navigate]);
  async function signOut() {
    if (logoutLock.current) return; logoutLock.current = true; setSigningOut(true); setActionError("");
    try { await logout(); navigate("/login", { replace: true }); }
    catch (problem) { setActionError((problem as { message?: string }).message ?? "退出失败，请重试"); }
    finally { logoutLock.current = false; setSigningOut(false); }
  }
  if (error) return <main className="registration-shell"><section className="result-card"><div role="alert"><h1>审核状态加载失败</h1><p>{error}</p></div><button onClick={() => setAttempt(value => value + 1)}>重试</button></section></main>;
  if (!result) return <main className="registration-shell" aria-busy="true"><section className="result-card"><h1>正在加载审核状态</h1></section></main>;
  return <main className="registration-shell"><section className="result-card">
    <p className={`status-badge status-${result.status.toLowerCase()}`} role="status">{labels[result.status]}</p>
    <h1>查看入驻审核状态</h1><p>{result.nextAction}</p>
    <dl><div><dt>提交时间</dt><dd>{new Date(result.submittedAt).toLocaleString("zh-CN")}</dd></div>
      <div><dt>更新时间</dt><dd>{new Date(result.updatedAt).toLocaleString("zh-CN")}</dd></div>
      <div><dt>审核角色</dt><dd>{result.reviewRole}</dd></div>
      {result.status === "REJECTED" && <div><dt>驳回原因</dt><dd>{result.rejectionReason}</dd></div>}
      <div><dt>支持渠道</dt><dd>{result.supportChannels.join(" / ")}</dd></div></dl>
    {actionError && <div role="alert" className="error-summary">{actionError}</div>}
    {result.status === "APPROVED" && <button ref={enterRef} disabled={signingOut} onClick={() => navigate("/dashboard")}
      onKeyDown={event => { if (event.key === "Enter") { event.preventDefault(); navigate("/dashboard"); }
        if (event.key === "Tab" && !event.shiftKey) { event.preventDefault(); exitRef.current?.focus(); } }}>进入平台</button>}
    <button ref={exitRef} className="secondary-action" disabled={signingOut} onClick={signOut}
      onKeyDown={event => { if (event.key === "Enter") { event.preventDefault(); void signOut(); }
        if (event.key === "Tab" && event.shiftKey && enterRef.current) { event.preventDefault(); enterRef.current.focus(); } }}>
      {signingOut ? "正在退出" : "退出登录"}</button>
  </section></main>;
}
