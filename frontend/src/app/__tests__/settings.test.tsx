import {screen,fireEvent,waitFor} from "@testing-library/react";
import {describe,it,expect,vi} from "vitest";
import {renderWithQuery,stubFetch} from "@/test/render";
import {auth} from "@/lib/auth";
import SettingsPage from "../settings/page";

const push=vi.fn();
vi.mock("next/navigation",()=>({useRouter:()=>({push})}));

describe("Settings page",()=>{
  it("shows profile and AI usage, and signs out by revoking the refresh token",async()=>{
    auth.save("t","refresh-1");
    const calls=stubFetch({
      "GET /api/auth/me":()=>({body:{id:"u",email:"ana@example.com",displayName:"Ana",role:"USER"}}),
      "GET /api/ai/usage":()=>({body:{requests:3,successful:2,inputTokens:120,outputTokens:80,averageLatencyMs:512.4}}),
      "POST /api/auth/logout":()=>({status:204}),
    });
    renderWithQuery(<SettingsPage/>);
    expect(await screen.findByText("ana@example.com")).toBeTruthy();
    expect(await screen.findByText("120 / 80")).toBeTruthy();
    fireEvent.click(screen.getByText("Sign out"));
    await waitFor(()=>expect(push).toHaveBeenCalledWith("/login"));
    expect(calls.find(c=>c.path==="/api/auth/logout")?.body).toBe(JSON.stringify({refreshToken:"refresh-1"}));
    expect(auth.access()).toBeNull();
  });
});
