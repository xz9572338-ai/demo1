import { useEffect, useRef, useState } from "react";
import { listApplications } from "../applications/application-api";
import { listPermissions, submitPermissions, type Permission, type PermissionCode } from "./permission-api";
import { Link } from "react-router-dom";

export function PermissionsPage() {
  const [applicationId, setApplicationId] = useState("");
  const [items, setItems] = useState<Permission[]>();
  const [reason, setReason] = useState("");
  const [selected, setSelected] = useState<PermissionCode[]>([]);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const loadSequence = useRef(0);
  const reasonLength = Array.from(reason).length;

  async function load() {
    const sequence = ++loadSequence.current;
    setError("");
    try {
      const apps = await listApplications();
      if (!apps[0]) throw { message: "请先创建应用" };
      const permissions = await listPermissions(apps[0].applicationId);
      if (sequence !== loadSequence.current) return;
      setApplicationId(apps[0].applicationId);
      setItems(permissions);
    } catch (problem) {
      if (sequence === loadSequence.current) setError(message(problem));
    }
  }

  useEffect(() => { void load(); }, []);

  async function submit(event: React.FormEvent) {
    event.preventDefault();
    if (busy || reasonLength < 1 || reasonLength > 500) return;
    setBusy(true);
    setError("");
    try {
      const submitted = await submitPermissions(applicationId, selected, reason);
      setItems(current => merge(current ?? [], submitted));
      setSelected([]);
      setReason("");
      void load();
    } catch (problem) {
      setError(message(problem));
    } finally {
      setBusy(false);
    }
  }

  if (!items && !error)
    return <main className="registration-shell" aria-busy="true"><section className="result-card"><h1>正在加载接口权限</h1></section></main>;
  if (!items)
    return <main className="registration-shell"><section className="result-card"><h1>权限暂时无法加载</h1><p role="alert">{error}</p><button onClick={() => void load()}>重新加载</button></section></main>;

  return <main className="registration-shell"><section className="registration-card">
    <p className="eyebrow">接口权限</p><h1>申请三项查询接口</h1>
    <p>权限由业务产品线下审核，等待期间不显示预计时间。</p>
    {error && <div><p role="alert">{error}</p><button type="button" className="secondary-action" onClick={() => void load()}>刷新权限状态</button></div>}
    <form onSubmit={submit}>
      <fieldset><legend>选择需要的接口</legend>{items.map(item => <div className="permission-row" key={item.code}>
        <label><input type="checkbox" disabled={item.status === "PENDING_REVIEW" || item.status === "APPROVED"} checked={selected.includes(item.code)} onChange={event => setSelected(event.target.checked ? [...selected, item.code] : selected.filter(code => code !== item.code))}/><strong>{item.name}</strong></label>
        <span className={`status-badge status-${item.status.toLowerCase()}`}>{label(item.status)}</span>
        <p>{item.purpose}；{item.dataScope}</p><p>{item.sensitiveNotice}</p>
        {item.submittedAt && <p>提交时间：<time dateTime={item.submittedAt}>{formatTime(item.submittedAt)}</time></p>}
        {item.updatedAt && <p>更新时间：<time dateTime={item.updatedAt}>{formatTime(item.updatedAt)}</time></p>}
        {item.rejectionReason && <p>驳回原因：{item.rejectionReason}</p>}
      </div>)}</fieldset>
      <div className="form-field"><label htmlFor="permission-reason">业务申请原因</label><textarea id="permission-reason" value={reason} required aria-describedby="permission-reason-help permission-reason-error" onChange={event => setReason(event.target.value)}/><p id="permission-reason-help">最多 500 个 Unicode 字符（{reasonLength}/500）</p>{reasonLength > 500 && <p id="permission-reason-error" role="alert">业务申请原因不能超过 500 个字符</p>}</div>
      <button disabled={busy || selected.length === 0 || reasonLength < 1 || reasonLength > 500}>{busy ? "正在提交…" : "提交权限申请"}</button>
    </form><p><Link to="/api-docs">查看 API 文档与签名示例</Link></p><p>问题反馈：企业微信 / 邮件</p>
  </section></main>;
}

function merge(current: Permission[], submitted: Permission[]) {
  const byCode = new Map(current.map(item => [item.code, item]));
  submitted.forEach(item => byCode.set(item.code, item));
  return current.map(item => byCode.get(item.code) ?? item);
}
function label(status: Permission["status"]) { return ({ NOT_APPLIED: "未申请", PENDING_REVIEW: "待审核", APPROVED: "已通过", REJECTED: "已驳回" } as const)[status]; }
function formatTime(value: string) { const date = new Date(value); return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("zh-CN", { dateStyle: "medium", timeStyle: "short" }).format(date); }
function message(problem: unknown) { if (problem && typeof problem === "object") { const value = problem as { message?: string; requestId?: string }; return `${value.message ?? "请稍后重试"}${value.requestId ? ` (Request ID: ${value.requestId})` : ""}`; } return "请稍后重试"; }
