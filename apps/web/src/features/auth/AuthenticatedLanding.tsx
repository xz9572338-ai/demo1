import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { logout } from "./session-api";

export function AuthenticatedLanding({ approved = false }: { approved?: boolean }) {
  const navigate = useNavigate(); const [error, setError] = useState(""); const [submitting, setSubmitting] = useState(false);
  const lock = useRef(false);
  async function signOut() {
    if (lock.current) return; lock.current = true; setSubmitting(true); setError("");
    try { await logout(); navigate("/login", { replace: true }); }
    catch (problem) { setError((problem as Error).message); }
    finally { lock.current = false; setSubmitting(false); }
  }
  return <main className="registration-shell"><section className="result-card">
    <p className="status-badge">{approved ? "已通过" : "审核状态"}</p>
    <h1>{approved ? "平台总览" : "查看入驻审核状态"}</h1>
    <p>{approved ? "企业已通过入驻审核，可以继续创建应用。" : "身份验证成功。审核详情与资料修正将在下一项功能中提供。"}</p>
    {approved&&<button type="button" onClick={()=>navigate("/applications")}>创建应用</button>}
    {error && <p role="alert">{error}</p>}<button className="secondary-action" type="button" disabled={submitting} onClick={signOut}>{submitting ? "正在退出…" : "退出登录"}</button>
  </section></main>;
}
