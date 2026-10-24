import { describe, expect, it, vi } from "vitest";
import { LeaseDraftSession, LeaseSaveError, safeLeaseWorkspace, type LeaseWorkspace } from "@/lib/lease-workspace-model";

const id="66f0c382-6991-4518-84da-0a31a3c4ddab";
const draft: LeaseWorkspace={id,status:"WORKING",revision:0,terms:{tenantName:"Taylor",suiteLabel:"Suite 8",startDate:null,endDate:null,
  amount:null,frequency:null,firstDueDate:null,lateFeeTerms:"",furnishedItems:"Chair"},leaseStatus:"UNCONFIRMED",
  confirmationIssues:["Enter the rent amount."],previewDueDates:[],hasMoreDueDates:false,scheduleId:null,updatedAt:"2026-10-24T12:00:00Z",confirmedAt:null};

describe("lease workspace boundary",()=>{
  it("allowlists saved terms while stripping internal ownership and arbitrary extra fields",()=>{
    expect(safeLeaseWorkspace({...draft,ownerId:"private-owner",terms:{...draft.terms,paypalToken:"private"}})).toEqual(draft);
  });
  it("rejects malformed dates, invalid money, and contradictory confirmation states",()=>{
    expect(safeLeaseWorkspace({...draft,terms:{...draft.terms,startDate:"2026-02-30"}})).toBeNull();
    expect(safeLeaseWorkspace({...draft,terms:{...draft.terms,amount:12.345}})).toBeNull();
    expect(safeLeaseWorkspace({...draft,status:"CONFIRMED"})).toBeNull();
    expect(safeLeaseWorkspace({...draft,revision:-1})).toBeNull();
    expect(safeLeaseWorkspace({...draft,previewDueDates:["2026-11-01"]})).toBeNull();
  });
});

describe("saved draft coordination",()=>{
  it("refuses a delayed reload that would replace edits entered after loading began",()=>{
    const session=new LeaseDraftSession(draft,vi.fn());
    const reloadVersion=session.snapshot().editVersion;
    session.edit("tenantName","Typed while loading");
    expect(()=>session.replaceSaved({...draft,revision:3},reloadVersion)).toThrow();
    expect(session.snapshot().fields.tenantName).toBe("Typed while loading");
    expect(session.snapshot().dirty).toBe(true);
  });
  it("defers navigation when creation finishes after the current lease received an edit",async()=>{
    const session=new LeaseDraftSession(draft,vi.fn());
    const navigate=vi.fn();
    let finish:()=>void=()=>{};
    const created=new Promise<void>(resolve=>{finish=resolve;});
    const opening=created.then(()=>session.navigateWhenSaved(navigate));
    session.edit("furnishedItems","Do not discard this new item");
    finish(); await opening;
    expect(navigate).not.toHaveBeenCalled();
    expect(session.snapshot().fields.furnishedItems).toBe("Do not discard this new item");
    expect(session.snapshot().dirty).toBe(true);
  });
  it("serializes delayed saves without replacing newer edits or reusing an old revision",async()=>{
    let release:(value:LeaseWorkspace)=>void=()=>{};
    const transport=vi.fn().mockImplementationOnce(()=>new Promise<LeaseWorkspace>(resolve=>{release=resolve;}))
      .mockImplementationOnce(async(_id,revision,terms)=>({...draft,revision:revision+1,terms}));
    const session=new LeaseDraftSession(draft,transport);
    session.edit("tenantName","First edit");
    const saving=session.save();
    session.edit("tenantName","Latest owner edit");
    release({...draft,revision:1,terms:{...draft.terms,tenantName:"First edit"}});
    await saving;
    expect(transport).toHaveBeenCalledTimes(2);
    expect(transport.mock.calls[1][1]).toBe(1);
    expect(transport.mock.calls[1][2].tenantName).toBe("Latest owner edit");
    expect(session.snapshot().fields.tenantName).toBe("Latest owner edit");
    expect(session.snapshot().dirty).toBe(false);
    expect(session.snapshot().lease.revision).toBe(2);
  });
  it("retains local values and stops automatic saving on a conflicting tab",async()=>{
    const transport=vi.fn().mockRejectedValue(new LeaseSaveError(409,"The saved lease changed."));
    const session=new LeaseDraftSession(draft,transport);
    session.edit("furnishedItems","Owner correction, keep this text");
    await expect(session.save()).rejects.toThrow();
    expect(session.snapshot().fields.furnishedItems).toBe("Owner correction, keep this text");
    expect(session.snapshot().dirty).toBe(true);
    expect(session.snapshot().conflict).toBe(true);
    session.edit("tenantName","Another local edit");
    await expect(session.save()).rejects.toThrow();
    expect(transport).toHaveBeenCalledTimes(1);
  });
  it("keeps failed saves dirty and retries the same saved revision on explicit retry",async()=>{
    const transport=vi.fn().mockRejectedValueOnce(new LeaseSaveError(503,"Try saving again."))
      .mockImplementationOnce(async(_id,revision,terms)=>({...draft,revision:revision+1,terms}));
    const session=new LeaseDraftSession(draft,transport);
    session.edit("amount","1200.00");
    await expect(session.save()).rejects.toThrow();
    expect(session.snapshot().dirty).toBe(true);
    await session.save();
    expect(transport.mock.calls.map(call=>call[1])).toEqual([0,0]);
    expect(session.snapshot().lease.terms.amount).toBe(1200);
  });
  it("does not submit invalid money or accept edits to a confirmed snapshot",async()=>{
    const transport=vi.fn();const session=new LeaseDraftSession(draft,transport);
    session.edit("amount","12.345");
    await expect(session.save()).rejects.toThrow();expect(transport).not.toHaveBeenCalled();
    const confirmed=new LeaseDraftSession({...draft,status:"CONFIRMED"},transport);
    confirmed.edit("tenantName","Do not change");
    expect(confirmed.snapshot().fields.tenantName).toBe("Taylor");
  });
});
