import createClient from "openapi-fetch";
import type { components, paths } from "../../generated/api/console-v1";
type App = components["schemas"]["ApplicationResponse"];
export type CreatedApp = components["schemas"]["ApplicationCreatedResponse"];
const client=createClient<paths>({baseUrl:`${globalThis.location?.origin??""}/console/api/v1`,fetch:(r:Request)=>globalThis.fetch(r)});
export async function listApplications():Promise<App[]>{const r=await client.GET("/applications");if(r.error)throw r.error;const value=r.data??[];if(!Array.isArray(value)||value.length>1||!value.every(item=>validApp(item)))throw invalidResponse();return value;}
export async function createApplication(name:string,purpose:string):Promise<CreatedApp>{
 const csrf=await client.GET("/sessions/csrf");if(csrf.error||!csrf.data)throw csrf.error;
 const r=await client.POST("/applications",{headers:{[csrf.data.headerName]:csrf.data.token},body:{name,purpose}});
 if(r.error)throw r.error;if(!validCreatedApp(r.data))throw invalidResponse();return r.data;
}

function validApp(value:unknown,allowSecret=false):value is App{
 if(!value||typeof value!=="object"||(!allowSecret&&"appSecret" in value))return false;
 const app=value as Record<string,unknown>;
 return typeof app.applicationId==="string"&&/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(app.applicationId)
  &&typeof app.name==="string"&&typeof app.purpose==="string"&&typeof app.appId==="string"&&app.appId.length>=20
  &&app.environment==="SANDBOX"&&app.status==="ACTIVE"&&validDateTime(app.createdAt)&&validDateTime(app.updatedAt)
  &&typeof app.requestId==="string"&&app.requestId.length>0;
}
function validCreatedApp(value:unknown):value is CreatedApp{return validApp(value,true)&&typeof (value as Record<string,unknown>).appSecret==="string"&&(value as Record<string,unknown>).appSecret!.toString().length>=43&&(value as Record<string,unknown>).secretShownOnce===true;}
function validDateTime(value:unknown){return typeof value==="string"&&!Number.isNaN(Date.parse(value));}
function invalidResponse(){return {code:"INVALID_RESPONSE",message:"服务响应格式异常，请稍后重试",requestId:"client",details:[],retryable:true};}
