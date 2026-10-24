"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { CheckCircle2, FileText, Plus, Save } from "lucide-react";
import { LeaseDraftSession, LeaseSaveError, safeLeaseWorkspace, type LeaseFields, type LeaseTerms, type LeaseWorkspace } from "@/lib/lease-workspace-model";

const inputClass = "w-full rounded-lg border border-slate-300 bg-white px-3 py-2.5 text-sm outline-none focus:border-slate-500 focus:ring-2 focus:ring-slate-200 disabled:bg-slate-50 disabled:text-slate-600";
const buttonClass = "cursor-pointer rounded-lg border px-3 py-2 text-sm font-medium hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-50";

async function leaseRequest(path: string, body?: unknown): Promise<LeaseWorkspace> {
  const response = await fetch(`/api/leases${path}`, { method: body === undefined ? "GET" : "POST", cache: "no-store", credentials: "same-origin",
    ...(body === undefined ? {} : { headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) }) });
  let raw: unknown; try { raw = await response.json(); } catch { raw = null; }
  if (!response.ok) {
    const detail = raw && typeof raw === "object" && "detail" in raw && typeof raw.detail === "string" ? raw.detail : "Could not save this lease. Your edits are still here.";
    throw new LeaseSaveError(response.status, detail);
  }
  const lease = safeLeaseWorkspace(raw);
  if (!lease) throw new LeaseSaveError(503, "The lease response could not be verified. Try again before leaving this page.");
  return lease;
}
function dateLabel(date: string | null) {
  return date ? new Intl.DateTimeFormat("en-US", { month: "short", day: "numeric", year: "numeric", timeZone: "UTC" }).format(new Date(date + "T12:00:00Z")) : "Not entered";
}
function money(value: number | null) { return value === null ? "Not entered" : new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(value); }

export function LeaseWorkspacePanel({ onConfirmed }: { onConfirmed: () => Promise<void> | void }) {
  const [leases, setLeases] = useState<LeaseWorkspace[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [locked, setLocked] = useState(false);
  const [error, setError] = useState("");
  const createId = useRef<string | null>(null);
  const activeSession = useRef<LeaseDraftSession | null>(null);
  const registerSession = useCallback((session: LeaseDraftSession | null) => { activeSession.current = session; }, []);
  const upsert = useCallback((lease: LeaseWorkspace) => setLeases(current => [lease, ...current.filter(item => item.id !== lease.id)]), []);
  const load = useCallback(async () => {
    try {
      const response = await fetch("/api/leases", { cache: "no-store", credentials: "same-origin" });
      const raw: unknown = await response.json();
      if (!response.ok || !Array.isArray(raw) || raw.some(item => safeLeaseWorkspace(item) === null)) throw new Error("Saved leases are temporarily unavailable.");
      setLeases(raw.map(item => safeLeaseWorkspace(item)!)); setError("");
    } catch (error) { setError(error instanceof Error ? error.message : "Could not load saved leases."); }
    finally { setLoading(false); }
  }, []);
  useEffect(() => { const frame = requestAnimationFrame(() => { void load(); }); return () => cancelAnimationFrame(frame); }, [load]);
  async function create() {
    if (locked) return;
    if (activeSession.current && !activeSession.current.navigateWhenSaved(() => {})) return;
    setCreating(true); setError("");
    createId.current ??= crypto.randomUUID();
    try {
      const lease = await leaseRequest("", { requestId: createId.current });
      upsert(lease);
      const open = () => setSelected(lease.id);
      if (!activeSession.current) open();
      else if (!activeSession.current.navigateWhenSaved(open)) setError("The new draft was saved in your lease list. Save your current edits before opening it.");
      createId.current = null;
    } catch (error) { setError(error instanceof Error ? error.message : "Could not create a lease. Try again."); }
    finally { setCreating(false); }
  }
  const current = leases.find(lease => lease.id === selected);
  return <section id="lease-workspace" aria-labelledby="lease-workspace-title" className="overflow-hidden rounded-2xl border bg-white shadow-sm">
    <div className="flex flex-wrap items-center justify-between gap-3 border-b p-5 sm:p-6">
      <div className="flex items-start gap-3"><FileText aria-hidden="true" className="mt-0.5 size-5 text-slate-500" /><div>
        <h3 id="lease-workspace-title" className="font-semibold">Leases</h3>
        <p className="mt-1 text-sm text-slate-500">Save your progress, review the terms, then confirm a rent schedule.</p>
      </div></div>
      <button type="button" disabled={creating || locked || loading} onClick={() => { void create(); }} className="inline-flex cursor-pointer items-center gap-2 rounded-lg bg-slate-950 px-3 py-2 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50">
        <Plus aria-hidden="true" className="size-4" />{creating ? "Creating…" : "New lease"}
      </button>
    </div>
    {error && <div role="alert" className="border-b bg-rose-50 p-4 text-sm text-rose-800">{error} {!current && <button className="ml-2 underline" onClick={() => { void load(); }}>Retry loading</button>}</div>}
    {loading ? <p role="status" className="p-6 text-sm text-slate-500">Loading saved leases…</p>
      : <div className="grid lg:grid-cols-[235px_1fr]">
        <nav aria-label="Saved leases" className="max-h-96 space-y-2 overflow-y-auto border-b bg-slate-50/60 p-4 lg:max-h-[850px] lg:border-r lg:border-b-0">
          {leases.length === 0 && <p className="p-2 text-sm text-slate-500">Start a lease to save terms, even when information is missing.</p>}
          {leases.map(lease => <button key={lease.id} type="button" aria-current={selected === lease.id ? "true" : undefined}
            disabled={locked && selected !== lease.id || creating} onClick={() => {
              if (activeSession.current) activeSession.current.navigateWhenSaved(() => setSelected(lease.id));
              else setSelected(lease.id);
            }}
            className={`w-full cursor-pointer rounded-lg border p-3 text-left disabled:cursor-not-allowed disabled:opacity-50 ${selected === lease.id ? "border-slate-500 bg-white shadow-sm" : "border-transparent hover:bg-white"}`}>
            <span className="block truncate text-sm font-medium">{lease.terms.suiteLabel || "Untitled lease"}</span>
            <span className="mt-1 block truncate text-xs text-slate-500">{lease.terms.tenantName || "Tenant not entered"}</span>
            <span className="mt-2 block text-xs font-medium text-slate-600">{lease.status === "WORKING" ? "Working draft" : `Confirmed · ${lease.leaseStatus.toLowerCase()}`}</span>
          </button>)}
          {locked && <p className="px-2 text-xs text-slate-500">Save or reload this draft before switching leases.</p>}
        </nav>
        {current ? <LeaseEditor key={current.id} initial={current} onUpdated={upsert} onLockChange={setLocked} registerSession={registerSession} navigationPending={creating} onConfirmed={onConfirmed} />
          : <div className="flex min-h-64 items-center justify-center p-8 text-center"><div><FileText aria-hidden="true" className="mx-auto size-8 text-slate-300" />
            <p className="mt-3 font-medium">Your lease workspace</p><p className="mt-1 text-sm text-slate-500">Choose a saved lease or start a new one. Nothing is scheduled until you confirm.</p></div></div>}
      </div>}
  </section>;
}

function LeaseEditor({ initial, onUpdated, onLockChange, registerSession, navigationPending, onConfirmed }: {
  initial: LeaseWorkspace; onUpdated: (lease: LeaseWorkspace) => void; onLockChange: (locked: boolean) => void;
  registerSession: (session: LeaseDraftSession | null) => void; navigationPending: boolean; onConfirmed: () => Promise<void> | void;
}) {
  const [session] = useState(() => new LeaseDraftSession(initial, async (id: string, revision: number, terms: LeaseTerms) => {
    const saved = await leaseRequest(`/${id}/draft`, { revision, terms }); onUpdated(saved); return saved;
  }));
  const [state, setState] = useState(() => session.snapshot());
  const [review, setReview] = useState<LeaseWorkspace | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [reloading, setReloading] = useState(false);
  const [error, setError] = useState("");
  useEffect(() => session.subscribe(() => setState(session.snapshot())), [session]);
  useEffect(() => { registerSession(session); return () => registerSession(null); }, [registerSession, session]);
  useEffect(() => { onLockChange(state.dirty || state.busy || confirming || reloading); }, [state.dirty, state.busy, confirming, reloading, onLockChange]);
  useEffect(() => {
    if (!state.dirty || state.busy || state.error || state.conflict || review) return;
    const timer = setTimeout(() => { void session.save().catch(() => {}); }, 750);
    return () => clearTimeout(timer);
  }, [session, state.dirty, state.busy, state.editVersion, state.error, state.conflict, review]);
  useEffect(() => {
    if (!state.dirty && !state.busy) return;
    const warn = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = ""; };
    window.addEventListener("beforeunload", warn); return () => window.removeEventListener("beforeunload", warn);
  }, [state.dirty, state.busy]);
  const confirmed = state.lease.status === "CONFIRMED";
  const change = (key: keyof LeaseFields, value: string) => { setReview(null); setError(""); session.edit(key, value); };
  async function save() { setError(""); try { await session.save(); } catch {} }
  async function showReview() {
    setError("");
    try { const saved = await session.save(); if (!saved.confirmationIssues.length) setReview(saved); }
    catch {}
  }
  async function reload() {
    if ((state.dirty || state.conflict) && !window.confirm("Discard the unsaved edits on this screen and reload the saved lease?")) return;
    const version = session.snapshot().editVersion;
    setReloading(true);
    try { const saved = await leaseRequest(`/${initial.id}`); session.replaceSaved(saved,version); onUpdated(saved); setReview(null); setError(""); }
    catch (error) { setError(error instanceof Error ? error.message : "Could not reload the saved lease."); }
    finally { setReloading(false); }
  }
  async function confirm() {
    if (!review) return;
    setConfirming(true); setError("");
    try {
      const saved = await leaseRequest(`/${initial.id}/confirm`, { revision: review.revision, confirmed: true });
      session.replaceSaved(saved); onUpdated(saved); setReview(null); await onConfirmed();
    } catch (error) { setError(error instanceof Error ? error.message : "Could not confirm. Retry this confirmation before creating another lease."); }
    finally { setConfirming(false); }
  }
  const terms = (review ?? state.lease).terms;
  return <div className="space-y-5 p-5 sm:p-6">
    <div className="flex flex-wrap items-center justify-between gap-3"><div>
      <h4 className="font-semibold">{confirmed ? "Confirmed lease terms" : review ? "Review before confirmation" : "Working draft"}</h4>
      <p role="status" className="mt-1 text-xs text-slate-500">{state.busy ? "Saving…" : state.dirty ? "Unsaved changes" : `Saved · revision ${state.lease.revision}`}</p>
    </div><button type="button" disabled={state.busy || confirming || reloading || navigationPending} className={buttonClass} onClick={() => { void reload(); }}>{reloading ? "Reloading…" : "Reload saved terms"}</button></div>
    {(state.error || error) && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 p-3 text-sm text-rose-800">{state.error || error}</p>}
    {state.conflict && <p className="text-sm text-amber-800">Saving is paused to protect your edits. Copy anything you want to keep before reloading the other saved version.</p>}
    {confirmed && <div className="rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-900"><p className="flex items-center gap-2 font-medium"><CheckCircle2 aria-hidden="true" className="size-4" />Owner review confirmed · {state.lease.leaseStatus.toLowerCase()}</p>
      <p className="mt-2">Rent is scheduled through {dateLabel(terms.endDate)}. PayPal autopay enrollment remains a separate step in the portfolio.</p>
      <p className="mt-1 text-xs">Confirmed {state.lease.confirmedAt ? new Date(state.lease.confirmedAt).toLocaleString() : ""}. These terms are read-only; agreement changes and billing adjustments are handled separately.</p></div>}
    {review ? <>
      <dl className="grid gap-4 rounded-xl border bg-slate-50 p-4 text-sm sm:grid-cols-2">
        {[["Tenant",terms.tenantName],["Suite",terms.suiteLabel],["Lease start",dateLabel(terms.startDate)],["Lease end (inclusive)",dateLabel(terms.endDate)],
          ["Rent",`${money(terms.amount)} · ${terms.frequency?.toLowerCase()}`],["First due date",dateLabel(terms.firstDueDate)],
          ["Late-fee notes",terms.lateFeeTerms || "None recorded"],["Furnished items",terms.furnishedItems || "None recorded"]].map(([label,value]) =>
          <div key={label}><dt className="text-xs font-medium text-slate-500">{label}</dt><dd className="mt-1 whitespace-pre-wrap break-words">{value}</dd></div>)}
      </dl>
      <div className="rounded-lg border p-4 text-sm"><h5 className="font-medium">Rent date preview</h5><ul className="mt-2 space-y-1">{review.previewDueDates.map(date => <li key={date}>{dateLabel(date)} · {money(terms.amount)}</li>)}</ul>
        {review.hasMoreDueDates && <p className="mt-2 text-xs text-slate-500">First five dates shown. The same recurrence continues through the lease end.</p>}
        <p className="mt-3 text-xs text-slate-600">The full rent amount applies on each date, including the final date. No prorating, automatic renewal, or late-fee charges are applied.</p></div>
      <p className="text-sm text-slate-600">Confirming records your review and creates one rent schedule from this saved revision. It does not change a signed agreement or enable PayPal autopay.</p>
      <div className="flex flex-wrap gap-3"><button type="button" disabled={confirming || reloading || navigationPending} className={buttonClass} onClick={() => setReview(null)}>Back to editing</button>
        <button type="button" disabled={confirming || reloading || navigationPending} onClick={() => { void confirm(); }} className="cursor-pointer rounded-lg bg-slate-950 px-4 py-2.5 text-sm font-medium text-white disabled:opacity-50">{confirming ? "Confirming…" : "Confirm and create schedule"}</button></div>
    </> : <>
      <fieldset disabled={confirmed || confirming || reloading || navigationPending} className="grid gap-4 sm:grid-cols-2">
        <legend className="sr-only">Lease terms</legend>
        {([ ["tenantName","Tenant name","text"], ["suiteLabel","Suite label","text"], ["startDate","Lease start","date"],
          ["endDate","Lease end (inclusive)","date"], ["firstDueDate","First rent due date","date"] ] as const).map(([key,label,type]) =>
          <label key={key} className="space-y-1.5 text-sm"><span className="block">{label}</span><input type={type} maxLength={type === "text" ? 120 : undefined}
            min={type === "date" ? "1900-01-01" : undefined} max={type === "date" ? "2100-12-31" : undefined} value={state.fields[key]}
            onChange={event => change(key,event.target.value)} className={inputClass} /></label>)}
        <label className="space-y-1.5 text-sm"><span className="block">Rent amount (USD)</span><input type="text" inputMode="decimal" value={state.fields.amount} maxLength={14}
          onChange={event => change("amount",event.target.value)} className={inputClass} placeholder="1200.00" /></label>
        <label className="space-y-1.5 text-sm"><span className="block">Rent frequency</span><select value={state.fields.frequency} onChange={event => change("frequency",event.target.value)} className={inputClass}>
          <option value="">Choose frequency</option><option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option></select></label>
        <label className="space-y-1.5 text-sm sm:col-span-2"><span className="block">Late-fee terms <span className="text-slate-500">(optional notes)</span></span>
          <textarea rows={2} maxLength={2000} value={state.fields.lateFeeTerms} onChange={event => change("lateFeeTerms",event.target.value)} className={inputClass} />
          <span className="block text-xs text-slate-500">Recorded for reference. Fees are not automatically added or charged.</span></label>
        <label className="space-y-1.5 text-sm sm:col-span-2"><span className="block">Furnished items <span className="text-slate-500">(optional)</span></span>
          <textarea rows={2} maxLength={4000} value={state.fields.furnishedItems} onChange={event => change("furnishedItems",event.target.value)} className={inputClass} /></label>
      </fieldset>
      {!confirmed && <>
        {state.lease.confirmationIssues.length > 0 && <div className="rounded-lg bg-slate-50 p-3 text-sm text-slate-600"><p className="font-medium">Before confirming the saved draft</p>
          <ul className="mt-2 list-disc space-y-1 pl-5">{state.lease.confirmationIssues.map(issue => <li key={issue}>{issue}</li>)}</ul></div>}
        <div className="flex flex-wrap items-center gap-3"><button type="button" disabled={state.busy || state.conflict || reloading || navigationPending || !state.dirty} onClick={() => { void save(); }} className={`${buttonClass} inline-flex items-center gap-2`}><Save aria-hidden="true" className="size-4" />Save draft</button>
          <button type="button" disabled={state.busy || state.conflict || reloading || navigationPending} onClick={() => { void showReview(); }} className="cursor-pointer rounded-lg bg-slate-950 px-4 py-2.5 text-sm font-medium text-white disabled:cursor-not-allowed disabled:opacity-50">Review lease and schedule</button></div>
        <p className="text-xs text-slate-500">Incomplete drafts are welcome. Wait for Saved before leaving; only confirmed terms create a rent schedule.</p>
      </>}
    </>}
  </div>;
}
