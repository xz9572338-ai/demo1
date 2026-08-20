import { spawnSync } from "node:child_process";
import { mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { join } from "node:path";

const bin=join(process.cwd(),process.platform==="win32"?"node_modules/.bin/redocly.cmd":"node_modules/.bin/redocly");
const output="apps/web/public/api-docs-contract.json";
mkdirSync("apps/web/public",{recursive:true});
const bundled=spawnSync(bin,["bundle","contracts/openapi/openapi-v1.yaml","--output",output],{stdio:"inherit",shell:process.platform==="win32"});
if(bundled.status!==0)process.exit(bundled.status??1);
const root="contracts/examples";
const examples={
  cURL:readFileSync(join(root,"curl/orders.ps1"),"utf8"),
  Java:readFileSync(join(root,"java/SignedRequest.java"),"utf8"),
  Python:readFileSync(join(root,"python/signed_request.py"),"utf8"),
};
writeFileSync("apps/web/public/api-docs-examples.json",JSON.stringify(examples,null,2)+"\n");
writeFileSync("apps/web/public/api-docs-signing-vector.json",readFileSync("contracts/examples/signing-vector.json"));
