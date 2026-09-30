import {screen,fireEvent,waitFor} from "@testing-library/react";
import {describe,it,expect,vi} from "vitest";
import {renderWithQuery,stubFetch} from "@/test/render";
import {auth} from "@/lib/auth";
import LibraryPage from "../library/page";

vi.mock("next/navigation",()=>({useRouter:()=>({push:vi.fn()})}));

const item={book:{id:"b1",title:"Dune",authors:["Frank Herbert"]},status:"READING",favorite:false,rating:null,addedAt:"2026-09-30T00:00:00Z"};

describe("Library page",()=>{
  it("asks anonymous users to sign in",()=>{
    renderWithQuery(<LibraryPage/>);
    expect(screen.getByText("Sign in to see your shelf")).toBeTruthy();
  });
  it("lists books and patches status, favorite and rating",async()=>{
    auth.save("t","r");
    const calls=stubFetch({"GET /api/library":()=>({body:[item]}),"PATCH /api/library/books/b1":()=>({body:item})});
    renderWithQuery(<LibraryPage/>);
    expect(await screen.findByText("Dune")).toBeTruthy();
    fireEvent.change(screen.getByLabelText("Reading status"),{target:{value:"READ"}});
    fireEvent.click(screen.getByLabelText("Mark as favorite"));
    fireEvent.click(screen.getByLabelText("Rate 4"));
    await waitFor(()=>expect(calls.filter(c=>c.method==="PATCH").map(c=>JSON.parse(c.body!))).toEqual([{status:"READ"},{favorite:true},{rating:4}]));
  });
});
