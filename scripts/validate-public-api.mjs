import { createHmac, createHash } from "node:crypto";
import { readFileSync } from "node:fs";

const vector=JSON.parse(readFileSync("contracts/examples/signing-vector.json","utf8"));
const encode=value=>encodeURIComponent(value).replace(/[!'()*]/g,c=>`%${c.charCodeAt(0).toString(16).toUpperCase()}`);
const compare=(a,b)=>a<b?-1:a>b?1:0;
const canonicalQuery=entries=>entries.map(([key,value])=>[encode(key),encode(value)]).sort(([ak,av],[bk,bv])=>compare(ak,bk)||compare(av,bv)).map(([key,value])=>`${key}=${value}`).join("&");
const cases=[
  { input:[], expected:"" },
  { input:[["b","2"],["a","1"],["a","0"]], expected:"a=0&a=1&b=2" },
  { input:[["empty",""],["space","a b"],["中文","值"]], expected:"%E4%B8%AD%E6%96%87=%E5%80%BC&empty=&space=a%20b" },
  { input:[["star","*"]], expected:"star=%2A" },
];
for(const item of cases)if(canonicalQuery(item.input)!==item.expected)throw new Error(`canonical query mismatch: ${item.expected}`);
const signature=createHmac("sha256",vector.appSecret).update(vector.canonicalRequest,"utf8").digest("hex");
if(signature!==vector.expectedSignature)throw new Error("TypeScript/Node signing vector mismatch");
for(const invalid of [vector.canonicalRequest+"x",vector.canonicalRequest.replace("GET","get"),vector.canonicalRequest.replace("/orders","/orders/"),vector.canonicalRequest+"\n"])
  if(createHmac("sha256",vector.appSecret).update(invalid).digest("hex")===vector.expectedSignature)throw new Error("canonical boundary accepted");
if(createHash("sha256").update("").digest("hex")!==vector.bodySha256)throw new Error("empty body hash mismatch");
const production=readFileSync("contracts/openapi/openapi-v1.yaml","utf8").replace("Production API","ENV API").replace("/openapi/v1","/ENV/v1");
const sandbox=readFileSync("contracts/openapi/sandbox-v1.yaml","utf8").replace("Sandbox API","ENV API").replace("/sandbox/v1","/ENV/v1");
if(production!==sandbox)throw new Error("sandbox and production contracts drifted");
for(const path of ["/customers/{customerId}","/orders","/orders/{orderId}"])if(!production.includes(path))throw new Error(`missing ${path}`);
const bundle=JSON.parse(readFileSync("apps/web/public/api-docs-contract.json","utf8"));
if(JSON.stringify(Object.keys(bundle.paths))!==JSON.stringify(["/customers/{customerId}","/orders","/orders/{orderId}"]))throw new Error("bundled paths drifted");
console.log("Public API contract and Node signing vector: PASS");
