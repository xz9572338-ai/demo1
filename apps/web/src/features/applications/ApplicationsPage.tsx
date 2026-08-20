import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { createApplication, listApplications, type CreatedApp } from "./application-api";

type ApplicationList = Awaited<ReturnType<typeof listApplications>>;

export function ApplicationsPage() {
  const [apps, setApps] = useState<ApplicationList>();
  const [created, setCreated] = useState<CreatedApp>();
  const [loadError, setLoadError] = useState("");
  const [submitError, setSubmitError] = useState("");
  const [busy, setBusy] = useState(false);
  const [confirmed, setConfirmed] = useState(false);
  const live = useRef<HTMLParagraphElement>(null);
  const loadSequence = useRef(0);
  const guardActive = useRef(false);
  const secretActive = useRef(false);
  const suppressNextPop = useRef(false);
  guardActive.current = busy || Boolean(created && !confirmed);
  secretActive.current = Boolean(created && !confirmed);

  async function load() {
    const sequence = ++loadSequence.current;
    setLoadError("");
    try { const result = await listApplications(); if (sequence === loadSequence.current) setApps(result); }
    catch (problem) { if (sequence === loadSequence.current) setLoadError(message(problem, "应用加载失败，请重试")); }
  }

  useEffect(() => { void load(); }, []);
  useEffect(() => {
    const warning = () => secretActive.current
      ? "AppSecret 仅展示一次，离开后无法再次查看。确认离开吗？"
      : "应用仍在创建，离开可能永久丢失首次密钥。确认离开吗？";
    const beforeUnload = (event: BeforeUnloadEvent) => {
      if (guardActive.current) { event.preventDefault(); event.returnValue = ""; }
    };
    const popState = () => {
      if (suppressNextPop.current) { suppressNextPop.current = false; return; }
      if (guardActive.current && !globalThis.confirm(warning())) {
        suppressNextPop.current = true;
        globalThis.history.forward();
      }
    };
    const originalPushState = globalThis.history.pushState.bind(globalThis.history);
    const originalReplaceState = globalThis.history.replaceState.bind(globalThis.history);
    globalThis.history.pushState = ((...args: Parameters<History["pushState"]>) => {
      if (!guardActive.current || globalThis.confirm(warning())) originalPushState(...args);
    }) as History["pushState"];
    globalThis.history.replaceState = ((...args: Parameters<History["replaceState"]>) => {
      if (!guardActive.current || globalThis.confirm(warning())) originalReplaceState(...args);
    }) as History["replaceState"];
    globalThis.addEventListener("beforeunload", beforeUnload);
    globalThis.addEventListener("popstate", popState);
    return () => {
      globalThis.removeEventListener("beforeunload", beforeUnload);
      globalThis.removeEventListener("popstate", popState);
      globalThis.history.pushState = originalPushState;
      globalThis.history.replaceState = originalReplaceState;
    };
  }, []);

  async function submit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setSubmitError("");
    const form = new FormData(event.currentTarget);
    try {
      setCreated(await createApplication(String(form.get("name")), String(form.get("purpose"))));
      setApps([]);
    } catch (problem) {
      setSubmitError(message(problem, "创建失败，请重试"));
    } finally {
      setBusy(false);
    }
  }

  async function copy(label: string, value: string) {
    try {
      await navigator.clipboard.writeText(value);
      if (live.current) live.current.textContent = `${label}复制成功`;
    } catch {
      if (live.current) live.current.textContent = `${label}复制失败，请手动复制`;
    }
  }

  if (!apps && !loadError)
    return <main className="registration-shell" aria-busy="true"><section className="result-card"><h1>正在加载应用</h1></section></main>;
  if (loadError)
    return <main className="registration-shell"><section className="result-card"><h1>应用暂时无法加载</h1><p role="alert">{loadError}</p><button onClick={() => void load()}>重新加载</button></section></main>;
  if (created)
    return <main className="registration-shell"><section className="result-card"><p className="status-badge">沙箱环境</p><h1>请立即保存应用密钥</h1><p role="alert">AppSecret 仅展示一次，离开或刷新后无法再次查看。</p><dl><div><dt>AppID</dt><dd><code>{created.appId}</code><button className="secondary-action" onClick={() => copy("AppID", created.appId)}>复制 AppID</button></dd></div><div><dt>AppSecret</dt><dd><code>{created.appSecret}</code><button className="secondary-action" onClick={() => copy("AppSecret", created.appSecret)}>复制 AppSecret</button></dd></div></dl><p ref={live} aria-live="polite"/><label><input type="checkbox" checked={confirmed} onChange={event => setConfirmed(event.target.checked)}/> 我已安全保存密钥</label><button disabled={!confirmed} onClick={() => { const { appSecret: _, secretShownOnce: __, ...safe } = created; setCreated(undefined); setApps([safe]); }}>完成并继续</button></section></main>;
  if (apps?.length)
    return <main className="registration-shell"><section className="result-card"><p className="status-badge status-approved">运行中</p><h1>{apps[0].name}</h1><dl><div><dt>AppID</dt><dd><code>{apps[0].appId}</code></dd></div><div><dt>环境</dt><dd>沙箱环境</dd></div></dl><p>AppSecret 已创建，不可再次查看。如遗失，请联系技术对接负责人。</p><Link className="primary-action" to="/permissions">申请接口权限</Link><Link className="secondary-link" to="/api-docs">查看 API 文档</Link></section></main>;
  return <main className="registration-shell"><section className="registration-card"><p className="eyebrow">应用管理</p><h1>创建对接应用</h1>{submitError && <p role="alert">{submitError}</p>}<form onSubmit={submit}><div className="form-field"><label htmlFor="app-name">应用名称</label><input id="app-name" name="name" maxLength={100} required/></div><div className="form-field"><label htmlFor="app-purpose">应用用途</label><input id="app-purpose" name="purpose" maxLength={500} required/></div><button disabled={busy}>{busy ? "正在创建…" : "创建应用并领取密钥"}</button></form></section></main>;
}

function message(problem: unknown, fallback: string) {
  return problem && typeof problem === "object" && typeof (problem as { message?: unknown }).message === "string"
    ? (problem as { message: string }).message : fallback;
}
