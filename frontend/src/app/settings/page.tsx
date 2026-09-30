"use client";
import {useQuery} from "@tanstack/react-query"; import {useRouter} from "next/navigation"; import Link from "next/link";
import {api,logout} from "@/lib/api"; import {useSignedIn} from "@/lib/auth"; import {ErrorNote} from "@/components/ErrorNote"; import type {AiUsage,User} from "@/types/api";

export default function SettingsPage(){
  const signedIn=useSignedIn(); const router=useRouter();
  const me=useQuery({queryKey:["me"],queryFn:()=>api<User>("/api/auth/me"),enabled:signedIn});
  const usage=useQuery({queryKey:["ai-usage"],queryFn:()=>api<AiUsage>("/api/ai/usage"),enabled:signedIn});
  async function signOut(){await logout();router.push("/login")}
  if(!signedIn)return <main className="shell" style={{maxWidth:720}}><div className="panel"><h1>Settings</h1><p className="muted">Sign in to manage your account.</p><Link className="btn" href="/login">Sign in</Link></div></main>;
  const u=usage.data;
  return <main className="shell" style={{maxWidth:820}}>
    <div className="sectionhead"><div><p className="eyebrow">Account</p><h1>Settings</h1></div><button className="btn secondary" onClick={signOut}>Sign out</button></div>
    <section className="panel"><h2>Profile</h2><ErrorNote error={me.error}/>{me.data&&<dl className="kv"><dt>Name</dt><dd>{me.data.displayName}</dd><dt>Email</dt><dd>{me.data.email}</dd><dt>Role</dt><dd><span className="badge">{me.data.role}</span></dd></dl>}</section>
    <section className="panel" style={{marginTop:20}}><h2>AI usage</h2><p className="muted">Every AI call records provider, model, latency and token counts. Requests are limited per minute per user.</p><ErrorNote error={usage.error}/>
      {u&&<div className="stats"><div className="stat"><span className="muted">Requests</span><strong>{u.requests}</strong></div><div className="stat"><span className="muted">Successful</span><strong>{u.successful}</strong></div><div className="stat"><span className="muted">Tokens in / out</span><strong>{u.inputTokens} / {u.outputTokens}</strong></div><div className="stat"><span className="muted">Avg latency</span><strong>{Math.round(u.averageLatencyMs)} ms</strong></div></div>}
    </section>
    <section className="panel" style={{marginTop:20}}><h2>Content policy</h2><p className="muted">Only upload files you are entitled to use. Uploaded documents are private to your account and are removed together with their vector chunks when you delete them.</p></section>
  </main>;
}
