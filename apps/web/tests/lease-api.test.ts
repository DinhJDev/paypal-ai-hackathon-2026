import { afterEach,beforeEach,expect,it,vi } from "vitest";
import { leaseApiRequest } from "@/lib/lease-api";
import { authEnvironment } from "./auth-fixture";
const {factory,session,accessToken}=vi.hoisted(()=>({factory:vi.fn(),session:vi.fn(),accessToken:vi.fn()}));
vi.mock("@/lib/auth0",()=>({getAuth0:factory}));
const id="66f0c382-6991-4518-84da-0a31a3c4ddab";
const draft={id,status:"WORKING",revision:0,terms:{tenantName:"Taylor",suiteLabel:"Suite 8",startDate:null,endDate:null,
  amount:null,frequency:null,firstDueDate:null,lateFeeTerms:"",furnishedItems:"Chair"},leaseStatus:"UNCONFIRMED",
  confirmationIssues:["Enter the rent amount."],previewDueDates:[],hasMoreDueDates:false,scheduleId:null,updatedAt:"2026-10-24T12:00:00Z",confirmedAt:null};
beforeEach(()=>{vi.resetAllMocks();for(const[k,v]of Object.entries(authEnvironment))vi.stubEnv(k,v);
  factory.mockReturnValue({getSession:session,getAccessToken:accessToken});session.mockResolvedValue({user:{sub:"auth0|owner"}});
  accessToken.mockResolvedValue({token:"private-test-token"});});
afterEach(()=>{vi.unstubAllEnvs();vi.unstubAllGlobals();});
it("forwards owner credentials server-side and returns only the private lease DTO",async()=>{
  const fetch=vi.fn().mockResolvedValue(Response.json({...draft,confirmedBy:"private",ownerId:"private"}));vi.stubGlobal("fetch",fetch);
  const response=await leaseApiRequest(`/${id}`,"GET","/lease");
  expect(response.status).toBe(200);expect(await response.json()).toEqual(draft);
  expect(response.headers.get("cache-control")).toBe("private, no-store");
  expect(fetch.mock.calls[0][0].pathname).toBe(`/api/v1/leases/${id}`);
  expect(fetch.mock.calls[0][1].headers.Authorization).toBe("Bearer private-test-token");
  expect(fetch.mock.calls[0][1].redirect).toBe("error");
});
it("rejects cross-origin saves and missing sessions before any upstream request",async()=>{
  const fetch=vi.fn();vi.stubGlobal("fetch",fetch);
  const request=new Request("http://localhost:3000/api/leases",{method:"POST",headers:{Origin:"https://attacker.example"}});
  expect((await leaseApiRequest("","POST","/lease",{},request)).status).toBe(403);
  session.mockResolvedValue(null);
  expect((await leaseApiRequest("","GET","/lease")).status).toBe(401);
  expect(fetch).not.toHaveBeenCalled();
});
it("preserves stale-revision status without exposing the upstream error",async()=>{
  vi.stubGlobal("fetch",vi.fn().mockResolvedValue(new Response("private diagnostic",{status:409})));
  const response=await leaseApiRequest(`/${id}/draft`,"POST","/lease",{revision:1},
    new Request("http://localhost:3000/lease",{method:"POST",headers:{Origin:"http://localhost:3000"}}));
  expect(response.status).toBe(409);expect(await response.text()).not.toContain("private diagnostic");
});
it("fails closed for malformed upstream data and does not accept arbitrary paths",async()=>{
  const fetch=vi.fn().mockResolvedValue(Response.json({...draft,revision:-1}));vi.stubGlobal("fetch",fetch);
  expect((await leaseApiRequest(`/${id}`,"GET","/lease")).status).toBe(503);
  expect((await leaseApiRequest("/../../secrets","GET","/lease")).status).toBe(404);
  expect(fetch).toHaveBeenCalledTimes(1);
});
