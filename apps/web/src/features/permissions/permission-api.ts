import createClient from "openapi-fetch";
import type { components, paths } from "../../generated/api/console-v1";
export type Permission = components["schemas"]["PermissionResponse"];
export type PermissionCode = components["schemas"]["PermissionCode"];
const client=createClient<paths>({baseUrl:`${globalThis.location?.origin??""}/console/api/v1`,fetch:(r:Request)=>globalThis.fetch(r)});
export async function listPermissions(applicationId:string){const r=await client.GET("/applications/{applicationId}/permissions",{params:{path:{applicationId}}});if(r.error)throw r.error;if(!valid(r.data))throw invalid();return r.data;}
export async function submitPermissions(applicationId:string,permissions:PermissionCode[],reason:string){const csrf=await client.GET("/sessions/csrf");if(csrf.error||!csrf.data)throw csrf.error;const r=await client.POST("/applications/{applicationId}/permissions",{params:{path:{applicationId}},headers:{[csrf.data.headerName]:csrf.data.token},body:{permissions,reason}});if(r.error)throw r.error;if(!valid(r.data,true))throw invalid();return r.data;}
function valid(value:unknown,partial=false):value is Permission[]{if(!Array.isArray(value)||value.length<(partial?1:3)||value.length>3)return false;return value.every(v=>v&&typeof v==="object"&&["CUSTOMER_BASE_READ","ORDER_LIST_READ","ORDER_DETAIL_READ"].includes(String((v as Permission).code))&&["NOT_APPLIED","PENDING_REVIEW","APPROVED","REJECTED"].includes(String((v as Permission).status))&&typeof (v as Permission).requestId==="string");}
function invalid(){return {code:"INVALID_RESPONSE",message:"服务响应格式异常，请稍后重试",requestId:"client",details:[],retryable:true};}
