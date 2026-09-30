import {QueryClient,QueryClientProvider} from "@tanstack/react-query";
import {render} from "@testing-library/react";
import type {ReactElement} from "react";
export function renderWithQuery(ui:ReactElement){const client=new QueryClient({defaultOptions:{queries:{retry:false},mutations:{retry:false}}});return render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>)}
/** Minimal fetch stub routing by "METHOD path" prefix; returns 404 for unknown routes. */
export function stubFetch(routes:Record<string,(init?:RequestInit)=>{status?:number;body?:unknown}>){
  const calls:{method:string;path:string;body?:string}[]=[];
  const fn=async(input:RequestInfo|URL,init?:RequestInit)=>{const url=new URL(String(input));const method=(init?.method??"GET").toUpperCase();const path=url.pathname+url.search;calls.push({method,path,body:typeof init?.body==="string"?init.body:undefined});
    const key=Object.keys(routes).find(k=>{const [m,p]=k.split(" ");return m===method&&path.startsWith(p)});
    if(!key)return new Response(JSON.stringify({code:"NOT_FOUND",message:"Not found"}),{status:404});
    const r=routes[key](init);return new Response(r.status===204?null:JSON.stringify(r.body??{}),{status:r.status??200,headers:{"Content-Type":"application/json"}})};
  globalThis.fetch=fn as typeof fetch; return calls;
}
