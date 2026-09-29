import { auth } from "./auth";
const BASE=process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";
async function refreshAccess(){ const token=auth.refresh(); if(!token)return false; const r=await fetch(`${BASE}/api/auth/refresh`,{method:"POST",headers:{"Content-Type":"application/json"},body:JSON.stringify({refreshToken:token})}); if(!r.ok){auth.clear();return false} const j=await r.json();auth.save(j.accessToken,j.refreshToken);return true; }
export async function api<T>(path:string, init:RequestInit={}, retry=true):Promise<T>{
 const headers=new Headers(init.headers); if(!(init.body instanceof FormData))headers.set("Content-Type","application/json"); const token=auth.access(); if(token)headers.set("Authorization",`Bearer ${token}`);
 let r=await fetch(`${BASE}${path}`,{...init,headers,cache:"no-store"});
 if(r.status===401&&retry&&await refreshAccess())return api<T>(path,init,false);
 if(!r.ok){let m=`Request failed (${r.status})`;try{const j=await r.json();m=j.message??m}catch{}throw new Error(m)}
 if(r.status===204)return undefined as T; return r.json();
}
export {BASE};
