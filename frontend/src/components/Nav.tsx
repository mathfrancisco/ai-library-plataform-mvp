"use client";
import Link from "next/link"; import { BookOpen, Bot, Library, Search, Gauge, Upload } from "lucide-react";
const links=[["/explore","Explore",Search],["/library","Library",Library],["/documents","Documents",Upload],["/ai","Assistant",Bot],["/dashboard","Dashboard",Gauge]] as const;
export function Nav(){return <header className="nav"><Link href="/" className="brand"><span className="brandmark"><BookOpen size={20}/></span><span>ShelfMind</span></Link><nav>{links.map(([href,label,Icon])=><Link href={href} key={href}><Icon size={16}/>{label}</Link>)}</nav><Link className="pill" href="/login">Account</Link></header>}
