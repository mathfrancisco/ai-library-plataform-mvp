"use client";
import {useQuery,useMutation,useQueryClient} from "@tanstack/react-query"; import {useState} from "react"; import Link from "next/link";
import {api} from "@/lib/api"; import {useSignedIn} from "@/lib/auth"; import {BookCard} from "@/components/BookCard"; import {Empty} from "@/components/Empty"; import {ErrorNote} from "@/components/ErrorNote";
import {FavoriteToggle,Rating,StatusSelect,type LibraryPatch} from "@/components/LibraryControls";
import {LIBRARY_STATUSES,type LibraryItem,type LibraryStatus} from "@/types/api";

export default function LibraryPage(){
  const signedIn=useSignedIn(); const [status,setStatus]=useState<LibraryStatus|"">(""); const qc=useQueryClient();
  const q=useQuery({queryKey:["library",status],queryFn:()=>api<LibraryItem[]>(`/api/library${status?`?status=${status}`:""}`),enabled:signedIn});
  const invalidate=()=>{qc.invalidateQueries({queryKey:["library"]});qc.invalidateQueries({queryKey:["dashboard"]})};
  const update=useMutation({mutationFn:({id,patch}:{id:string;patch:LibraryPatch})=>api<LibraryItem>(`/api/library/books/${id}`,{method:"PATCH",body:JSON.stringify(patch)}),onSuccess:invalidate});
  const remove=useMutation({mutationFn:(id:string)=>api(`/api/library/books/${id}`,{method:"DELETE"}),onSuccess:invalidate});
  if(!signedIn)return <main className="shell"><Empty title="Sign in to see your shelf" body="Your library is private to your account."/><p style={{textAlign:"center"}}><Link className="btn" href="/login">Sign in</Link></p></main>;
  return <main className="shell">
    <div className="sectionhead"><div><p className="eyebrow">Personal library</p><h1>My shelf</h1></div><div className="row"><Link className="btn secondary" href="/books/new">+ Add manually</Link><select aria-label="Filter by status" className="select" style={{maxWidth:180}} value={status} onChange={e=>setStatus(e.target.value as LibraryStatus|"")}><option value="">All books</option>{LIBRARY_STATUSES.map(s=><option key={s.value} value={s.value}>{s.label}</option>)}</select></div></div>
    <ErrorNote error={q.error??update.error??remove.error}/>
    {!q.isLoading&&!q.data?.length?<Empty title="Your shelf is empty" body="Search the catalog and add a book to begin."/>:<div className="grid">{q.data?.map(x=>{const id=x.book.id!;const busy=update.isPending&&update.variables?.id===id;return <div key={id}><BookCard book={x.book}/>
      <div style={{padding:"10px 4px",display:"grid",gap:8}}>
        <div className="row"><StatusSelect value={x.status} disabled={busy} onChange={s=>update.mutate({id,patch:{status:s}})}/><FavoriteToggle value={x.favorite} disabled={busy} onChange={v=>update.mutate({id,patch:{favorite:v}})}/></div>
        <div className="row" style={{justifyContent:"space-between"}}><Rating value={x.rating} disabled={busy} onChange={v=>update.mutate({id,patch:{rating:v}})}/><button className="btn secondary" style={{padding:"7px 10px",fontSize:12}} onClick={()=>remove.mutate(id)}>Remove</button></div>
      </div></div>})}</div>}
  </main>;
}
