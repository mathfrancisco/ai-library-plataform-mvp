import {describe,it,expect} from "vitest";
import {emptyBookForm,toCreateRequest} from "../books";
describe("toCreateRequest",()=>{
  it("maps comma lists to the API's pipe format and drops blanks",()=>{
    const r=toCreateRequest({...emptyBookForm,title:"  Dune ",authors:"Frank Herbert, , Brian Herbert",categories:"Sci-fi",pageCount:"412",isbn13:" ",publishedYear:""});
    expect(r).toMatchObject({title:"Dune",authorNames:"Frank Herbert | Brian Herbert",categoryNames:"Sci-fi",pageCount:412,publicDomain:false});
    expect(r.isbn13).toBeUndefined(); expect(r.publishedYear).toBeUndefined();
  });
});
