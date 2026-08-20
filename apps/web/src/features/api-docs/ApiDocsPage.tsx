import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { loadDocs, type Docs, type Examples, type SigningVector } from "./api-docs-content";

export function ApiDocsPage() {
  const [notice, setNotice] = useState<{ text: string; error?: boolean }>();
  const [docs,setDocs]=useState<{contract:Docs;examples:Examples;vector:SigningVector}>();
  const [loadError,setLoadError]=useState<{message:string;requestId?:string}>();
  const [reload,setReload]=useState(0);
  useEffect(()=>{const controller=new AbortController();setLoadError(undefined);setDocs(undefined);loadDocs(controller.signal).then(setDocs).catch(problem=>{if(!controller.signal.aborted)setLoadError(problem)});return()=>controller.abort();},[reload]);
  async function copy(label: string, source: string) {
    let timer:number|undefined;
    try { await Promise.race([navigator.clipboard.writeText(source),new Promise((_,reject)=>{timer=window.setTimeout(()=>reject(new Error("timeout")),5000)})]); setNotice({ text: `${label} 示例已复制` }); }
    catch { setNotice({ text: "复制失败，请手动选择代码", error: true }); }
    finally { if(timer)window.clearTimeout(timer); }
  }
  if(loadError)return <main className="docs-shell"><section className="result-card" role="alert"><h1>API 文档暂时无法加载</h1><p>{loadError.message}</p>{loadError.requestId&&<p>Request ID：<code>{loadError.requestId}</code></p>}<button onClick={()=>setReload(value=>value+1)}>重新加载</button><p>问题反馈：企业微信 / 邮件</p></section></main>;
  if(!docs)return <main className="docs-shell" aria-busy="true"><h1>正在加载 API 文档</h1></main>;
  const endpoints=Object.entries(docs.contract.paths).map(([path,item])=>({path,...item.get}));
  const errors=docs.contract.components["x-error-catalog"];
  return <main className="docs-shell">
    <nav aria-label="控制台导航" className="docs-nav"><Link to="/applications">应用</Link><Link to="/permissions">接口权限</Link><strong aria-current="page">API 文档</strong></nav>
    <header className="docs-hero"><p className="eyebrow">OPEN API V1 · 静态文档</p><h1>用同一份契约完成沙箱接入</h1><p>生产与沙箱使用相同路径、参数、模型和签名规则。当前页面不会发起开放 API 请求。</p></header>
    {notice && <p className={notice.error ? "error-summary" : "copy-notice"} role={notice.error ? "alert" : "status"}>{notice.text}</p>}
    <section aria-labelledby="endpoint-title"><h2 id="endpoint-title">首发接口</h2><div className="endpoint-grid">{endpoints.map(item => <article className="endpoint-card" key={item.path}><span className="method">GET</span><h3><code>{item.path}</code></h3><p>{item.summary}</p><p>{item.description}</p>{item.parameters?.length?<details><summary>参数（{item.parameters.length}）</summary><ul>{item.parameters.map(parameter=><li key={`${parameter.in}-${parameter.name}`}><code>{parameter.name}</code> · {parameter.in} · {parameter.required?"必填":"可选"}<pre><code>{JSON.stringify(parameter.schema)}</code></pre></li>)}</ul></details>:null}<details><summary>响应与示例</summary><pre tabIndex={0}><code>{JSON.stringify(item.responses,null,2)}</code></pre></details></article>)}</div></section>
    <section aria-labelledby="sign-title"><h2 id="sign-title">HMAC-SHA256 签名</h2><p>{docs.contract.components["x-signing"].encoding}；输出为 {docs.contract.components["x-signing"].output}；时间窗口 ±{docs.contract.components["x-signing"].timestampWindowSeconds} 秒。</p><ul>{docs.contract.components["x-signing"].rules.map(rule=><li key={rule}>{rule}</li>)}</ul><pre tabIndex={0}><code>{docs.contract.components["x-signing"].canonicalTemplate}</code></pre><h3>固定测试向量</h3><dl><div><dt>AppID</dt><dd><code>{docs.vector.appId}</code></dd></div><div><dt>测试 AppSecret</dt><dd><code>{docs.vector.appSecret}</code></dd></div><div><dt>时间戳 / nonce</dt><dd><code>{docs.vector.timestamp} / {docs.vector.nonce}</code></dd></div><div><dt>规范查询</dt><dd><code>{docs.vector.query}</code></dd></div><div><dt>请求体摘要</dt><dd><code>{docs.vector.bodySha256}</code></dd></div><div><dt>待签名串</dt><dd><pre tabIndex={0}><code>{docs.vector.canonicalRequest}</code></pre></dd></div><div><dt>预期签名</dt><dd><code>{docs.vector.expectedSignature}</code></dd></div></dl><p className="support-note">以上 AppSecret 是公开测试值，禁止用于部署。真实 AppSecret 只保存在客户服务端安全配置中，不得写入 URL、浏览器存储、源码或日志。</p></section>
    <section aria-labelledby="model-title"><h2 id="model-title">公共模型</h2>{Object.entries(docs.contract.components.schemas).map(([name,schema])=><details key={name}><summary><code>{name}</code></summary><pre tabIndex={0}><code>{JSON.stringify(schema,null,2)}</code></pre></details>)}</section>
    <section aria-labelledby="sample-title"><h2 id="sample-title">可复制示例</h2>{Object.entries(docs.examples).map(([label, source]) => <article className="sample" key={label}><h3>{label}</h3><pre tabIndex={0}><code>{source}</code></pre><button type="button" className="copy-button" onClick={() => copy(label, source)}>复制 {label} 示例</button></article>)}</section>
    <section aria-labelledby="error-title"><h2 id="error-title">错误与重试</h2><dl className="error-list">{errors.map(item => <div key={item.code}><dt><code>{item.code}</code> · HTTP {item.httpStatus}</dt><dd>{item.action}（{item.retryable?"可重试":"不可重试"}）</dd></div>)}</dl><p>所有响应都携带 <code>X-Request-ID</code>。排错仍未解决时，请通过企业微信或邮件联系技术对接负责人。</p></section>
  </main>;
}
