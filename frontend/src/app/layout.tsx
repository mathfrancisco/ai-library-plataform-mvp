import "./globals.css"; import type {Metadata} from "next"; import {Nav} from "@/components/Nav"; import {QueryProvider} from "@/lib/query";
export const metadata:Metadata={title:"ShelfMind — AI Library",description:"Search, understand and explore books with grounded AI.",icons:{icon:"/favicon.svg"}};
export default function RootLayout({children}:{children:React.ReactNode}){return <html lang="en"><body><QueryProvider><Nav/>{children}<footer className="footer">ShelfMind · AI Library portfolio project · Metadata-first, rights-aware RAG.</footer></QueryProvider></body></html>}
