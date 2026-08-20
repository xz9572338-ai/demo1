import { FormEvent, useLayoutEffect, useRef, useState } from "react";
import { ApiError, RegistrationInput, RegistrationResult, submitRegistration } from "./registration-api";

const initial: RegistrationInput = { enterpriseName: "", contactName: "", contactMobile: "", username: "", password: "" };
const fields: Array<{ key: keyof RegistrationInput; label: string; type?: string; autoComplete?: string }> = [
  { key: "enterpriseName", label: "企业名称", autoComplete: "organization" },
  { key: "contactName", label: "联系人", autoComplete: "name" },
  { key: "contactMobile", label: "手机号", type: "tel", autoComplete: "tel" },
  { key: "username", label: "账号", autoComplete: "username" },
  { key: "password", label: "密码", type: "password", autoComplete: "new-password" },
];

export function RegistrationPage() {
  const [values, setValues] = useState(initial);
  const [errors, setErrors] = useState<Partial<Record<keyof RegistrationInput, string>>>({});
  const [submitting, setSubmitting] = useState(false);
  const [result, setResult] = useState<RegistrationResult>();
  const [globalError, setGlobalError] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const resultRef = useRef<HTMLHeadingElement>(null);
  const submitLock = useRef(false);

  useLayoutEffect(() => { if (result) resultRef.current?.focus(); }, [result]);

  async function onSubmit(event: FormEvent) {
    event.preventDefault();
    if (submitLock.current) return;
    const passwordLength = Array.from(values.password).length;
    if (passwordLength < 12 || passwordLength > 128) {
      setErrors(current => ({ ...current, password: "密码长度须为 12–128 个 Unicode 字符" }));
      setGlobalError("请检查填写内容");
      requestAnimationFrame(() => document.getElementById("password")?.focus());
      return;
    }
    submitLock.current = true;
    setSubmitting(true); setErrors({}); setGlobalError("");
    try {
      setResult(await submitRegistration(values));
      setValues(current => ({ ...current, password: "" }));
    } catch (problem) {
      const api = problem as ApiError;
      if (Array.isArray(api.details)) {
        const validDetails = api.details.filter(item => fields.some(field => field.key === item.field));
        const next = Object.fromEntries(validDetails.map(item => [item.field, item.message]));
        setErrors(next);
        const first = fields.find(field => validDetails.some(item => item.field === field.key));
        requestAnimationFrame(() => document.getElementById(first?.key ?? "error-summary")?.focus());
      }
      setValues(current => ({ ...current, password: "" }));
      setShowPassword(false);
      setGlobalError(api.message || "申请暂时无法提交，请稍后重试");
    } finally { submitLock.current = false; setSubmitting(false); }
  }

  if (result) return (
    <main className="registration-shell" aria-labelledby="submission-title">
      <section className="result-card" role="status">
        <p className="status-badge">待审核</p>
        <h1 id="submission-title" ref={resultRef} tabIndex={-1}>申请已提交</h1>
        <dl><div><dt>提交时间</dt><dd>{new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short", timeZone: "Asia/Shanghai" }).format(new Date(result.submittedAt))}</dd></div>
          <div><dt>处理角色</dt><dd>{result.reviewRole}</dd></div><div><dt>反馈渠道</dt><dd>{result.supportChannels.join(" / ")}</dd></div></dl>
        <p>{result.nextAction}</p><p className="muted">问题编号：{result.requestId}</p>
      </section>
    </main>
  );

  return (
    <main className="registration-shell" aria-labelledby="page-title">
      <section className="registration-copy"><p className="eyebrow">供应链 API 接入</p><h1 id="page-title">申请企业入驻</h1>
        <p>提交后由商务专员线下审核。审核通过前，账号不能使用平台受保护功能。</p></section>
      <form className="registration-card" onSubmit={onSubmit} noValidate>
        <p className="required-note">所有字段均为必填项</p>
        {globalError && <div id="error-summary" className="error-summary" role="alert" tabIndex={-1}><strong>提交未完成</strong><p>{globalError}</p>
          {fields.some(field => errors[field.key]) && <ul>{fields.filter(field => errors[field.key]).map(field =>
            <li key={field.key}><a href={`#${field.key}`}>{field.label}：{errors[field.key]}</a></li>)}</ul>}
        </div>}
        {fields.map(field => <div className="form-field" key={field.key}>
          <label htmlFor={field.key}>{field.label}</label>
          <input id={field.key} name={field.key} type={field.key === "password" && showPassword ? "text" : field.type || "text"} autoComplete={field.autoComplete}
            required aria-required="true"
            inputMode={field.key === "contactMobile" ? "tel" : undefined}
            minLength={field.key === "password" ? 12 : undefined}
            value={values[field.key]} aria-invalid={Boolean(errors[field.key])}
            aria-describedby={errors[field.key] ? `${field.key}-error error-summary` : undefined}
            onChange={event => { setValues(current => ({ ...current, [field.key]: event.target.value }));
              setErrors(current => ({ ...current, [field.key]: undefined })); }} />
          {errors[field.key] && <p id={`${field.key}-error`} className="field-error"><a href="#error-summary">{errors[field.key]}</a></p>}
          {field.key === "password" && <button className="password-toggle" type="button" aria-pressed={showPassword} onClick={() => setShowPassword(value => !value)}>{showPassword ? "隐藏密码" : "显示密码"}</button>}
        </div>)}
        <button type="submit" disabled={submitting}>{submitting ? "正在提交…" : "提交入驻申请"}</button>
        <p className="support-note">如需帮助，请通过企业微信或邮件联系技术对接负责人。</p>
      </form>
    </main>
  );
}
