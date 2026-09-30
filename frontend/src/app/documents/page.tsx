"use client";
import {useState} from "react"; import Link from "next/link";
import {useMutation,useQuery,useQueryClient} from "@tanstack/react-query";
import {api} from "@/lib/api"; import {useSignedIn} from "@/lib/auth"; import {ErrorNote} from "@/components/ErrorNote"; import {Empty} from "@/components/Empty"; import {RagAnswerView} from "@/components/RagAnswerView";
import type {DocumentItem,RagAnswer} from "@/types/api";

function size(bytes:number){return bytes>1_048_576?`${(bytes/1_048_576).toFixed(1)} MB`:`${Math.max(1,Math.round(bytes/1024))} KB`}

export default function Documents(){
  const signedIn=useSignedIn(); const qc=useQueryClient();
  const q=useQuery({queryKey:["documents"],queryFn:()=>api<DocumentItem[]>("/api/documents"),enabled:signedIn,refetchInterval:q=>q.state.data?.some(d=>d.status==="STORED"||d.status==="PROCESSING")?3000:false});
  const [selected,setSelected]=useState<string>(); const [question,setQuestion]=useState(""); const [answer,setAnswer]=useState<RagAnswer>();
  const upload=useMutation({mutationFn:(f:File)=>{const form=new FormData();form.append("file",f);return api<DocumentItem>("/api/documents",{method:"POST",body:form})},onSuccess:d=>{setSelected(d.id);qc.invalidateQueries({queryKey:["documents"]})}});
  const remove=useMutation({mutationFn:(id:string)=>api(`/api/documents/${id}`,{method:"DELETE"}),onSuccess:(_,id)=>{if(selected===id){setSelected(undefined);setAnswer(undefined)}qc.invalidateQueries({queryKey:["documents"]})}});
  const ask=useMutation({mutationFn:()=>api<RagAnswer>(`/api/documents/${selected}/chat`,{method:"POST",body:JSON.stringify({question})}),onSuccess:setAnswer});
  const current=q.data?.find(d=>d.id===selected);
  if(!signedIn)return <main className="shell"><Empty title="Sign in to upload documents" body="Documents and their vector chunks are private to your account."/><p style={{textAlign:"center"}}><Link className="btn" href="/login">Sign in</Link></p></main>;
  return <main className="shell">
    <div className="sectionhead"><div><p className="eyebrow">Grounded RAG</p><h1>Documents</h1></div><span className="badge">PDF · EPUB · TXT · Markdown</span></div>
    <label className="upload"><b>Upload a permitted document</b><p className="muted">Private chunks are isolated by ownerId + documentId.</p><input type="file" accept=".pdf,.epub,.txt,.md,.markdown" onChange={e=>{const f=e.target.files?.[0];if(f)upload.mutate(f);e.target.value=""}}/>{upload.isPending&&<p className="muted">Uploading…</p>}</label>
    <ErrorNote error={upload.error??remove.error??q.error}/>
    <div className="panel" style={{marginTop:20}}><h2>Your documents</h2>
      {!q.data?.length&&!q.isLoading&&<p className="muted">No documents yet.</p>}
      <div className="doclist">{q.data?.map(d=><div key={d.id} className={`docrow ${d.id===selected?"selected":""}`}>
        <button className="btn secondary" style={{textAlign:"left"}} onClick={()=>{setSelected(d.id);setAnswer(undefined)}}>{d.originalName}</button>
        <span className="muted" style={{fontSize:13}}>{size(d.sizeBytes)}</span>
        <span className="badge" title={d.errorMessage}>{d.status}{d.status==="READY"?` · ${d.chunkCount} chunks`:""}</span>
        <button className="btn secondary" style={{padding:"7px 10px",fontSize:12}} disabled={remove.isPending} onClick={()=>remove.mutate(d.id)}>Delete</button>
      </div>)}</div>
    </div>
    {current&&<div className="panel" style={{marginTop:20}}><p className="eyebrow">Ask {current.originalName}</p>
      {current.status==="FAILED"&&<p className="errornote">{current.errorMessage??"Ingestion failed"}</p>}
      {(current.status==="STORED"||current.status==="PROCESSING")&&<p className="muted">Indexing… this updates automatically.</p>}
      <textarea className="textarea" value={question} onChange={e=>setQuestion(e.target.value)} placeholder="What are the main arguments in this document?"/>
      <button className="btn" onClick={()=>ask.mutate()} disabled={ask.isPending||!question.trim()||current.status!=="READY"}>{ask.isPending?"Retrieving…":"Ask with RAG"}</button>
      <ErrorNote error={ask.error}/>{answer&&<RagAnswerView answer={answer}/>}</div>}
  </main>;
}
