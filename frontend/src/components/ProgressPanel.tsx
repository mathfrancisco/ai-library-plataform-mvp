"use client";
import {useMutation} from "@tanstack/react-query"; import {useState} from "react";
import {api} from "@/lib/api"; import {ErrorNote} from "./ErrorNote"; import type {Progress} from "@/types/api";

/** Remount with a new `key` when the saved progress changes to reset the form. */
export function ProgressPanel({bookId,pageCount,progress,onSaved}:{bookId:string;pageCount?:number;progress:Progress|null|undefined;onSaved:(p:Progress)=>void}){
  const [page,setPage]=useState(progress?String(progress.currentPage):""); const [percentage,setPercentage]=useState(progress?String(progress.percentage):"");
  const derived=!!pageCount&&!!page;
  const save=useMutation({mutationFn:()=>api<Progress>(`/api/reading/${bookId}`,{method:"PUT",body:JSON.stringify({currentPage:page?Number(page):undefined,percentage:percentage&&!derived?Number(percentage):undefined})}),onSuccess:onSaved});
  return <div className="panel" style={{marginTop:28}}><p className="eyebrow">Reading progress</p>
    {progress&&<><div className="progress" aria-label="Progress"><span style={{width:`${progress.percentage}%`}}/></div><p className="muted">{progress.percentage}%{progress.startedAt?` · started ${progress.startedAt}`:""}{progress.completedAt?` · finished ${progress.completedAt}`:""}</p></>}
    <div className="row"><input aria-label="Current page" className="input" style={{maxWidth:180}} type="number" min="0" max={pageCount} placeholder="Current page" value={page} onChange={e=>setPage(e.target.value)}/><input aria-label="Percent" className="input" style={{maxWidth:180}} type="number" min="0" max="100" placeholder="Percent" value={percentage} disabled={derived} onChange={e=>setPercentage(e.target.value)}/><button className="btn secondary" onClick={()=>save.mutate()}>{save.isPending?"Saving…":"Save progress"}</button></div>
    {pageCount?<p className="muted" style={{fontSize:13}}>Percentage is derived from the page when the page count is known.</p>:null}
    <ErrorNote error={save.error}/></div>;
}
