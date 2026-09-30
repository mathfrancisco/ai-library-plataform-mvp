import type {LibraryStatus} from "@/types/api";

export type BookForm={title:string;subtitle:string;authors:string;categories:string;isbn13:string;isbn10:string;description:string;publisher:string;language:string;publishedYear:string;pageCount:string;coverUrl:string;publicDomain:boolean;shelf:LibraryStatus|""};
export const emptyBookForm:BookForm={title:"",subtitle:"",authors:"",categories:"",isbn13:"",isbn10:"",description:"",publisher:"",language:"",publishedYear:"",pageCount:"",coverUrl:"",publicDomain:false,shelf:"WANT_TO_READ"};

/** The API stores multi-valued names as " | "-separated text; the form accepts commas. */
export function toCreateRequest(f:BookForm){
  const list=(v:string)=>v.split(/[,|]/).map(x=>x.trim()).filter(Boolean).join(" | ")||undefined;
  const text=(v:string)=>v.trim()||undefined; const num=(v:string)=>v.trim()?Number(v):undefined;
  return {title:f.title.trim(),subtitle:text(f.subtitle),authorNames:list(f.authors),categoryNames:list(f.categories),isbn13:text(f.isbn13),isbn10:text(f.isbn10),description:text(f.description),publisher:text(f.publisher),language:text(f.language),publishedYear:num(f.publishedYear),pageCount:num(f.pageCount),coverUrl:text(f.coverUrl),publicDomain:f.publicDomain};
}

