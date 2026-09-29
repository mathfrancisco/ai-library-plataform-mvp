import {render,screen} from "@testing-library/react";
import {describe,it,expect} from "vitest";
import {BookCard} from "../BookCard";
describe("BookCard",()=>{it("renders title and author",()=>{render(<BookCard book={{id:"1",title:"Clean Architecture",authors:["Robert C. Martin"]}}/>);expect(screen.getByText("Clean Architecture")).toBeTruthy();expect(screen.getByText("Robert C. Martin")).toBeTruthy();});});
