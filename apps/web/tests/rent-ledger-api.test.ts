import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { rentApiRequest } from "@/lib/rent-api";
import { authEnvironment } from "./auth-fixture";

const { factory, session, accessToken }=vi.hoisted(()=>({factory:vi.fn(),session:vi.fn(),accessToken:vi.fn()}));
vi.mock("@/lib/auth0",()=>({getAuth0:factory}));
const id="f216e512-81d4-4f50-a0fc-317952e91f15";
const period={id,dueDate:"2026-10-24",amountDue:1200,amountReceived:400,balance:800,paymentStatus:"PARTIALLY_PAID",
  collectionHeld:true,reviewRequired:true,reconciliationRequired:false,holdReason:"EXTERNAL_PAYMENT",revision:1,reviewedAt:null,
  externalPayments:[{id,amount:400,paidOn:"2026-10-24",method:"CASH",reference:"Receipt 1",recordedAt:"2026-10-24T12:00:00Z",reversedAt:null,reversalReason:null}],paypalPayments:[]};
beforeEach(()=>{
  vi.resetAllMocks();for(const [k,v] of Object.entries(authEnvironment))vi.stubEnv(k,v);
  factory.mockReturnValue({getSession:session,getAccessToken:accessToken});
  session.mockResolvedValue({user:{sub:"auth0|owner"}});accessToken.mockResolvedValue({token:"test-private-token"});
});
afterEach(()=>{vi.unstubAllEnvs();vi.unstubAllGlobals();});

it("returns a private ledger allowlist without provider tokens or internal ownership fields",async()=>{
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue(Response.json([{...period,ownerId:"private",paypalPaymentTokenId:"private",externalPayments:[{...period.externalPayments[0],recordedBy:"internal-owner"}]}])));
  const response=await rentApiRequest(`/${id}/ledger`,"GET","/ledger",undefined,false,undefined,"ledger");
  expect(response.status).toBe(200);expect(response.headers.get("cache-control")).toBe("private, no-store");
  expect(await response.json()).toEqual([period]);
});
it("rejects contradictory financial totals from the upstream API",async()=>{
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue(Response.json([{...period,balance:1200}])));
  const response=await rentApiRequest(`/${id}/ledger`,"GET","/ledger",undefined,false,undefined,"ledger");
  expect(response.status).toBe(503);
});
it("retains an overpaid balance for reconciliation without treating it as a new charge",async()=>{
  const overpaid={...period,amountReceived:1600,balance:-400,paymentStatus:"OVERPAID",reconciliationRequired:true};
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue(Response.json(overpaid)));
  const response=await rentApiRequest(`/${id}/ledger/${id}/payments`,"POST","/ledger",{},false,
    new Request("http://localhost:3000/ledger",{method:"POST",headers:{Origin:"http://localhost:3000"}}),"period");
  expect(response.status).toBe(200);expect((await response.json()).balance).toBe(-400);
});
it("preserves a stale-review conflict while removing private upstream error details",async()=>{
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue(new Response("private-stack",{status:409})));
  const response=await rentApiRequest(`/${id}/ledger/${id}/review`,"POST","/ledger",{revision:1,confirmed:true},false,
    new Request("http://localhost:3000/ledger",{method:"POST",headers:{Origin:"http://localhost:3000"}}),"period");
  expect(response.status).toBe(409);expect(await response.text()).not.toContain("private-stack");
});
it("rejects cross-origin receipt confirmation before contacting the API",async()=>{
  const fetch=vi.fn();vi.stubGlobal("fetch",fetch);
  const response=await rentApiRequest(`/${id}/ledger/${id}/payments`,"POST","/ledger",{},false,
    new Request("http://localhost:3000/ledger",{method:"POST",headers:{Origin:"https://attacker.example"}}),"period");
  expect(response.status).toBe(403);expect(fetch).not.toHaveBeenCalled();
});
