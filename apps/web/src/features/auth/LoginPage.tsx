import { FormEvent, useLayoutEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { login } from "./session-api";

export function LoginPage() {
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const lock = useRef(false);
  const errorRef = useRef<HTMLDivElement>(null);
  useLayoutEffect(() => { if (message) errorRef.current?.focus(); }, [message]);
  async function submit(event: FormEvent) {
    event.preventDefault(); if (lock.current) return;
    if (Array.from(password).length < 12 || Array.from(password).length > 128) {
      setMessage("密码长度须为 12–128 个 Unicode 字符"); document.getElementById("login-password")?.focus(); return;
    }
    lock.current = true; setSubmitting(true); setMessage("");
    try { const session = await login(identifier, password); setPassword(""); navigate(session.landingPath); }
    catch (problem) { setPassword(""); setMessage((problem as { message?: string }).message || "登录暂时无法完成，请稍后重试"); }
    finally { lock.current = false; setSubmitting(false); }
  }
  return <main className="registration-shell" aria-labelledby="login-title">
    <section className="registration-copy"><p className="eyebrow">供应链 API 接入</p><h1 id="login-title">登录开放平台</h1>
      <p>登录后将根据企业入驻状态进入审核状态页或平台总览。</p></section>
    <form className="registration-card" onSubmit={submit} noValidate>
      {message && <div id="login-error" ref={errorRef} role="alert" tabIndex={-1} className="error-summary"><strong>登录未完成</strong><p>{message}</p></div>}
      <div className="form-field"><label htmlFor="login-identifier">账号或手机号</label><input id="login-identifier" required autoComplete="username"
        value={identifier} onChange={event => setIdentifier(event.target.value)} /></div>
      <div className="form-field"><label htmlFor="login-password">密码</label><input id="login-password" type="password" required autoComplete="current-password"
        value={password} onChange={event => setPassword(event.target.value)} /></div>
      <button type="submit" disabled={submitting}>{submitting ? "正在登录…" : "登录"}</button>
      <p className="support-note">账号受限或忘记密码，请通过企业微信或邮件联系技术对接负责人。</p>
    </form>
  </main>;
}
