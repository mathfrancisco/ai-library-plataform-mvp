import {describe,it,expect} from "vitest";
import {api,ApiError} from "../api";
import {auth} from "../auth";
import {stubFetch} from "@/test/render";

describe("api client",()=>{
  it("surfaces the backend error envelope code and message",async()=>{
    stubFetch({"GET /api/books/x":()=>({status:404,body:{code:"BOOK_NOT_FOUND",message:"Book not found"}})});
    await expect(api("/api/books/x")).rejects.toMatchObject({name:"ApiError",status:404,code:"BOOK_NOT_FOUND",message:"Book not found"});
  });
  it("refreshes once on 401 and retries with the rotated token",async()=>{
    auth.save("old","r1"); let first=true;
    const calls=stubFetch({
      "GET /api/library":()=>{if(first){first=false;return {status:401,body:{code:"UNAUTHENTICATED",message:"x"}}}return {body:[]}},
      "POST /api/auth/refresh":()=>({body:{accessToken:"new",refreshToken:"r2"}}),
    });
    await expect(api("/api/library")).resolves.toEqual([]);
    expect(calls.map(c=>`${c.method} ${c.path}`)).toEqual(["GET /api/library","POST /api/auth/refresh","GET /api/library"]);
    expect(auth.access()).toBe("new"); expect(auth.refresh()).toBe("r2");
  });
  it("clears the session when refresh fails",async()=>{
    auth.save("old","bad");
    stubFetch({"GET /api/library":()=>({status:401,body:{code:"UNAUTHENTICATED",message:"Authentication required"}}),"POST /api/auth/refresh":()=>({status:401,body:{code:"INVALID_REFRESH_TOKEN",message:"x"}})});
    await expect(api("/api/library")).rejects.toBeInstanceOf(ApiError);
    expect(auth.access()).toBeNull();
  });
});
