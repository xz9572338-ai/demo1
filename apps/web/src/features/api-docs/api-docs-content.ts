export type Docs = { paths: Record<string,{get:{summary:string;description:string;parameters?:Array<{name:string;in:string;required?:boolean;schema?:Record<string,unknown>}>;responses:Record<string,{description?:string}>}}>;
  components:{schemas:Record<string,unknown>;"x-error-catalog":Array<{code:string;httpStatus:number;retryable:boolean;action:string}>;"x-signing":{algorithm:string;encoding:string;output:string;timestampWindowSeconds:number;canonicalTemplate:string;rules:string[]}} };
export type Examples = Record<"cURL"|"Java"|"Python",string>;
export type SigningVector={appId:string;appSecret:string;timestamp:string;nonce:string;method:string;path:string;query:string;bodySha256:string;canonicalRequest:string;expectedSignature:string};

const object=(value:unknown):value is Record<string,unknown>=>Boolean(value)&&typeof value==="object"&&!Array.isArray(value);
const textField=(value:unknown)=>typeof value==="string"&&value.length>0;
const requiredPaths=["/customers/{customerId}","/orders","/orders/{orderId}"];
function validContract(value:unknown):value is Docs {
  if(!object(value)||!object(value.paths)||!object(value.components))return false;
  const paths=value.paths, components=value.components;
  if(Object.keys(paths).length!==requiredPaths.length||!requiredPaths.every(path=>path in paths))return false;
  const schemas=components.schemas, errors=components["x-error-catalog"], signing=components["x-signing"];
  const pathsOk=requiredPaths.every(path=>{const entry=paths[path];if(!object(entry)||!object(entry.get))return false;const get=entry.get;
    const parameters=get.parameters===undefined||(Array.isArray(get.parameters)&&get.parameters.every(parameter=>object(parameter)&&textField(parameter.name)&&textField(parameter.in)&&object(parameter.schema)));
    return textField(get.summary)&&textField(get.description)&&parameters&&object(get.responses)&&Object.keys(get.responses).length>0&&Object.values(get.responses).every(response=>object(response)&&(textField(response.description)||textField(response.$ref)));});
  return pathsOk&&object(schemas)&&Object.keys(schemas).length>0&&Array.isArray(errors)&&errors.length>0&&errors.every(item=>object(item)&&textField(item.code)&&typeof item.httpStatus==="number"&&typeof item.retryable==="boolean"&&textField(item.action))&&object(signing)&&textField(signing.algorithm)&&textField(signing.encoding)&&textField(signing.output)&&typeof signing.timestampWindowSeconds==="number"&&textField(signing.canonicalTemplate)&&Array.isArray(signing.rules)&&signing.rules.length>0&&signing.rules.every(textField);
}
function validExamples(value:unknown):value is Examples {return object(value)&&["cURL","Java","Python"].every(key=>textField(value[key]));}
function validVector(value:unknown):value is SigningVector {return object(value)&&["appId","appSecret","timestamp","nonce","method","path","query","bodySha256","canonicalRequest","expectedSignature"].every(key=>textField(value[key]));}

export async function loadDocs(signal?:AbortSignal):Promise<{contract:Docs;examples:Examples;vector:SigningVector;requestId:string}>{
  const requestId=`docs_${crypto.randomUUID()}`;
  try {
    const responses=await Promise.all(["contract","examples","signing-vector"].map(name=>fetch(`/api-docs-${name}.json`,{signal,cache:"no-store",headers:{"X-Request-ID":requestId}})));
    if(responses.some(item=>!item.ok))throw new Error("HTTP");
    const [contract,examples,vector]=await Promise.all(responses.map(item=>item.json()));
    if(!validContract(contract)||!validExamples(examples)||!validVector(vector))throw new Error("FORMAT");
    return {contract,examples,vector,requestId};
  } catch(problem) { if(problem instanceof DOMException&&problem.name==="AbortError")throw problem; throw {message:problem instanceof Error&&problem.message==="FORMAT"?"API 文档资源格式异常":"API 文档资源暂时无法加载",requestId}; }
}
