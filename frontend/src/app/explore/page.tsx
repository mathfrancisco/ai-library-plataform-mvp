"use client";
import {useState} from "react";
import {useMutation,useQuery} from "@tanstack/react-query";
import {api} from "@/lib/api";
import type {Book,SearchHit} from "@/types/api";

type Discovery={plan:{query:string;language?:string;maxPages?:number;categories?:string[]};results:SearchHit[]};

export default function Explore(){
  const [q,setQ]=useState("software architecture"); const [submitted,setSubmitted]=useState(q); const [mode,setMode]=useState("HYBRID");
  const [prompt,setPrompt]=useState("I want an accessible book to learn distributed systems as a Java developer"); const [discovered,setDiscovered]=useState<Discovery>();
  const search=useQuery({queryKey:["search",submitted,mode],queryFn:()=>api<SearchHit[]>(`/api/search?q=${encodeURIComponent(submitted)}&mode=${mode}`),enabled:!!submitted});
  const discover=useMutation({mutationFn:()=>api<Discovery>("/api/search/discover",{method:"POST",body:JSON.stringify({prompt})}),onSuccess:setDiscovered});
  const add=useMutation({mutationFn:async(h:SearchHit)=>{let id=h.localBookId;if(!id){const b=await api<Book>("/api/catalog/import",{method:"POST",body:JSON.stringify({provider:h.provider,externalId:h.externalId})});id=b.id} if(!id)throw new Error("Book import failed");return api(`/api/library/books/${id}`,{method:"POST",body:JSON.stringify({status:"WANT_TO_READ"})})}});
  const results=discovered?.results??search.data??[];
  return <main className="shell">
    <div className="sectionhead"><div><p className="eyebrow">Hybrid discovery</p><h1>Explore books</h1></div><span className="badge">FTS + vector + federated catalog</span></div>
    <form className="searchbox" onSubmit={e=>{e.preventDefault();setDiscovered(undefined);setSubmitted(q)}}><input className="input" value={q} onChange={e=>setQ(e.target.value)} placeholder="Title, author, ISBN or keywords"/><select className="select" style={{maxWidth:150}} value={mode} onChange={e=>setMode(e.target.value)}><option>HYBRID</option><option>LEXICAL</option><option>SEMANTIC</option></select><button className="btn">Search</button></form>
    <div className="panel" style={{marginBottom:28}}><p className="eyebrow">Discover with natural language</p><div className="searchbox" style={{marginBottom:0}}><input className="input" value={prompt} onChange={e=>setPrompt(e.target.value)} /><button className="btn secondary" onClick={()=>discover.mutate()} disabled={discover.isPending}>{discover.isPending?"Understanding…":"Discover with AI"}</button></div>{discovered&&<p className="muted">Interpreted as: <b>{discovered.plan.query}</b>{discovered.plan.categories?.length?` · ${discovered.plan.categories.join(", ")}`:""}</p>}</div>
    {(search.isLoading||discover.isPending)&&<p className="muted">Searching local knowledge and catalogs…</p>}
    <div className="grid">{results.map((h,i)=><article className="bookcard" key={`${h.provider}-${h.externalId}-${h.localBookId}-${i}`}><div className="cover">{h.coverUrl?<img src={h.coverUrl} alt=""/>:<span>{h.title[0]}</span>}</div><div className="bookmeta"><p className="eyebrow">{h.matchType}</p><h3>{h.title}</h3><p>{h.authors?.join(", ")}</p><div className="row"><span>{h.provider??"LOCAL"}</span><span>{h.score.toFixed(4)}</span></div><button className="smallbtn" onClick={()=>add.mutate(h)}>{add.isPending?"Adding…":"+ Want to read"}</button></div></article>)}</div>
  </main>
}
