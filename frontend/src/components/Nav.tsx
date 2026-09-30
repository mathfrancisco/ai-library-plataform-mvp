"use client";
import Link from "next/link"; import {usePathname} from "next/navigation"; import {useState} from "react";
import { BookOpen, Bot, Library, Search, Gauge, Upload, Settings, Menu, X } from "lucide-react"; import {useSignedIn} from "@/lib/auth";
const links=[["/explore","Explore",Search],["/library","Library",Library],["/documents","Documents",Upload],["/ai","Assistant",Bot],["/dashboard","Dashboard",Gauge]] as const;
export function Nav(){
  const signedIn=useSignedIn(); const pathname=usePathname(); const [open,setOpen]=useState(false);
  const account=signedIn?<Link className="pill" href="/settings" onClick={()=>setOpen(false)}><Settings size={14}/>Settings</Link>:<Link className="pill" href="/login" onClick={()=>setOpen(false)}>Sign in</Link>;
  return <header className="nav"><Link href="/" className="brand"><span className="brandmark"><BookOpen size={20}/></span><span>ShelfMind</span></Link>
    <nav id="main-nav" className={open?"open":""}>{links.map(([href,label,Icon])=><Link href={href} key={href} aria-current={pathname?.startsWith(href)?"page":undefined} onClick={()=>setOpen(false)}><Icon size={16}/>{label}</Link>)}</nav>
    {account}
    <button type="button" className="menubtn" aria-label={open?"Close menu":"Open menu"} aria-expanded={open} aria-controls="main-nav" onClick={()=>setOpen(v=>!v)}>{open?<X size={20}/>:<Menu size={20}/>}</button>
  </header>;
}
