"use client";

import { useCallback, useEffect, useState, type FormEvent } from "react";
import type { RentSchedule } from "@/lib/rent-dashboard-model";
import { businessToday, type ExternalPaymentMethod, type RentPeriod } from "@/lib/rent-ledger-model";

const money = (amount: number) => new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(amount);
const input = "rounded-lg border bg-white px-3 py-2 text-sm";
const button = "cursor-pointer rounded-lg border px-3 py-2 text-sm font-medium hover:bg-slate-50 disabled:cursor-wait disabled:opacity-50";

const uncertainSave = "The save could not be confirmed. Retry this confirmation unchanged. If you refresh, check the payment history before starting another record.";
async function mutate(path: string, body: unknown): Promise<void> {
  let response: Response;
  try {
    response = await fetch(path, { method: "POST", credentials: "same-origin", cache: "no-store",
      headers: { "Content-Type": "application/json" }, body: JSON.stringify(body) });
  } catch { throw new Error(uncertainSave); }
  if (!response.ok) throw new Error(response.status === 409
    ? "The ledger changed or this confirmation was already used. Refresh and check the history before creating another record."
    : response.status === 400 ? "Check the entered values and confirm again." : uncertainSave);
}

export function RentLedgerPanel({ schedules, onChanged, initialScheduleId = "" }: { schedules: RentSchedule[]; onChanged: () => Promise<void>; initialScheduleId?: string }) {
  const [selected, setSelected] = useState(initialScheduleId);
  const schedule = schedules.find(s => s.id === selected) ?? schedules[0];
  const id = schedule?.id;
  const [periods, setPeriods] = useState<RentPeriod[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [reloadKey, setReloadKey] = useState(0);
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    const frame = requestAnimationFrame(() => {
      setLoading(true); setError(""); setPeriods([]);
      void fetch(`/api/rent-schedules/${id}/ledger`, { credentials: "same-origin", cache: "no-store", signal: controller.signal })
        .then(async response => { if (!response.ok) throw new Error("The rent ledger is temporarily unavailable."); return response.json(); })
        .then(value => { if (!Array.isArray(value)) throw new Error("The rent ledger could not be read."); if (!controller.signal.aborted) setPeriods(value); })
        .catch(failure => { if (!controller.signal.aborted) setError(failure instanceof Error ? failure.message : "Could not load the ledger."); })
        .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    });
    return () => { cancelAnimationFrame(frame); controller.abort(); };
  }, [id, reloadKey]);
  const changed = useCallback(async () => { setReloadKey(key => key + 1); await onChanged(); }, [onChanged]);
  return <section id="rent-ledger" aria-labelledby="ledger-title" className="rounded-2xl border bg-white p-5 shadow-sm sm:p-6">
    <div className="flex flex-wrap items-center justify-between gap-3">
      <div><h3 id="ledger-title" className="text-lg font-semibold">Rent ledger</h3>
        <p className="mt-1 text-sm text-slate-500">Rent owed, outside payments, and collection holds for each due date.</p></div>
      <button type="button" className={button} onClick={() => setReloadKey(key => key + 1)} disabled={loading || !id}>Refresh ledger</button>
    </div>
    {schedules.length === 0 ? <p className="mt-4 text-sm text-slate-500">Create a schedule to start tracking rent balances.</p> : <>
      <label className="mt-4 flex max-w-sm flex-col gap-1 text-sm">Suite / tenant
        <select className={input} value={id} onChange={event => setSelected(event.target.value)}>
          {schedules.map(s => <option key={s.id} value={s.id}>{s.tenantLabel}</option>)}
        </select>
      </label>
      <p className="mt-3 text-xs text-slate-500">Outside payments are owner-recorded. Partial payments hold automatic collection for that rent period. Confirming review keeps that hold in place.</p>
      {error && <p role="alert" className="mt-3 text-sm text-rose-700">{error}</p>}
      {loading ? <p role="status" className="mt-4 text-sm">Loading ledger…</p> :
        <div key={id} className="mt-4 space-y-3">{periods.map(period => <PeriodCard key={period.id} period={period}
          base={`/api/rent-schedules/${id}/ledger/${period.id}`} onChanged={changed} />)}</div>}
    </>}
  </section>;
}

function PeriodCard({ period, base, onChanged }: { period: RentPeriod; base: string; onChanged: () => Promise<void> }) {
  const [action, setAction] = useState<"record" | "review" | string | null>(null);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function confirm(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setBusy(true); setError("");
    try {
      if (action === "review") await mutate(`${base}/review`, { revision: period.revision, confirmed: true });
      else await mutate(`${base}/payments/${action}/reversal`, { reason, confirmed: true });
      setAction(null); setReason(""); await onChanged();
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Could not confirm the change."); }
    finally { setBusy(false); }
  }
  const held = period.collectionHeld && period.balance > 0;
  return <article className="rounded-xl border p-4">
    <div className="flex flex-wrap items-start justify-between gap-3">
      <div><h4 className="font-semibold">Rent due {period.dueDate}</h4>
        <p className="mt-1 text-sm text-slate-600">Due {money(period.amountDue)} · Received {money(period.amountReceived)} · Balance {money(period.balance)}</p></div>
      <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${period.balance === 0 ? "bg-emerald-50 text-emerald-800" : "bg-slate-100 text-slate-700"}`}>{period.paymentStatus.toLowerCase().replaceAll("_", " ")}</span>
    </div>
    {period.reconciliationRequired && <p role="status" className="mt-2 text-sm text-rose-800">Reconciliation required: a provider payment is processing or uncertain, or this period has an excess payment. Review the payment records before further collection.</p>}
    {held && <p className="mt-2 text-sm text-amber-800">{period.reviewRequired ? "On hold—owner review required." : "Reviewed—manual collection only for this period."} The remaining balance will not be charged automatically.</p>}
    {period.balance === 0 && <p className="mt-2 text-sm text-emerald-800">This rent period is paid. No further automatic charge is scheduled for it.</p>}
    {period.reviewedAt && <p className="mt-1 text-xs text-slate-500">Owner reviewed {new Date(period.reviewedAt).toLocaleString()}</p>}
    <ul className="mt-3 space-y-2 text-sm">
      {period.paypalPayments.map((payment, index) => <li key={index}>PayPal sandbox · {money(payment.amount)} · {payment.status.toLowerCase()}</li>)}
      {period.externalPayments.map(payment => <li key={payment.id} className="flex flex-wrap items-center justify-between gap-2 border-t pt-2">
        <span>{money(payment.amount)} · {payment.method.toLowerCase().replaceAll("_", " ")} · {payment.paidOn} · owner recorded
          {payment.reference && ` · ${payment.reference}`}
          {payment.reversedAt && <span className="block text-xs text-slate-500">Reversed: {payment.reversalReason} · {new Date(payment.reversedAt).toLocaleString()}</span>}</span>
        {!payment.reversedAt && <button type="button" className="cursor-pointer text-xs underline" disabled={busy}
          onClick={() => { setAction(payment.id); setError(""); }}>Correct record</button>}
      </li>)}
    </ul>
    {error && <p role="alert" className="mt-3 text-sm text-rose-700">{error}</p>}
    {action === "record" ? <ExternalPaymentForm base={base} period={period} onCancel={() => setAction(null)} onSaved={async () => { setAction(null); await onChanged(); }} />
      : action ? <form onSubmit={confirm} className="mt-4 space-y-3 rounded-lg bg-slate-50 p-3">
          {action === "review" ? <p className="text-sm">Confirm review of the {money(period.balance)} remaining balance. Collection stays on hold; record further payments manually.</p>
            : <><p className="text-sm">Reverse this record, then enter a replacement if needed. The original record stays in the history. This does not refund an actual payment.</p>
                <label className="flex flex-col gap-1 text-sm">Correction reason<input className={input} required maxLength={240} value={reason} onChange={event => setReason(event.target.value)} /></label></>}
          <div className="flex flex-wrap gap-2"><button disabled={busy} className={button}>{busy ? "Saving…" : action === "review" ? "Confirm review—keep collection on hold" : "Confirm reversal"}</button>
            <button type="button" disabled={busy} className={button} onClick={() => setAction(null)}>Cancel</button></div>
        </form>
      : <div className="mt-4 flex flex-wrap gap-2">
          <button type="button" className={button} onClick={() => setAction("record")}>Record outside payment</button>
          {period.reviewRequired && !period.reconciliationRequired && <button type="button" className={button} onClick={() => setAction("review")}>Review balance</button>}
        </div>}
  </article>;
}

function ExternalPaymentForm({ base, period, onCancel, onSaved }: { base: string; period: RentPeriod; onCancel: () => void; onSaved: () => Promise<void> }) {
  const [amount, setAmount] = useState("");
  const [method, setMethod] = useState<ExternalPaymentMethod>("CASH");
  const [paidOn, setPaidOn] = useState(businessToday);
  const [reference, setReference] = useState("");
  const [requestId] = useState(() => crypto.randomUUID());
  const [reviewing, setReviewing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!reviewing) { setReviewing(true); return; }
    setBusy(true); setError("");
    try {
      await mutate(`${base}/payments`, { requestId, amount: Number(amount), method, paidOn, reference, revision: period.revision, confirmed: true });
      await onSaved();
    } catch (failure) { setError(failure instanceof Error ? failure.message : "Could not record payment."); }
    finally { setBusy(false); }
  }
  return <form onSubmit={submit} className="mt-4 space-y-3 rounded-lg bg-slate-50 p-3">
    {reviewing ? <div className="space-y-2 text-sm">
      <p className="font-medium">Review before saving</p>
      <p>{money(Number(amount))} via {method.toLowerCase().replaceAll("_", " ")} · Paid {paidOn} · Rent due {period.dueDate}</p>
      {reference && <p>Reference: {reference}</p>}
      <p>Resulting balance: {money((Math.round(period.balance * 100) - Math.round(Number(amount) * 100)) / 100)}. This is an owner-recorded payment; Suitefolk has not verified it with the payment platform.</p>
      <p>Automatic collection for this rent period will stay on hold. Any payment already processing will require reconciliation.</p>
      <button type="button" className={button} disabled={busy} onClick={() => setReviewing(false)}>Edit values</button>
    </div> : <div className="grid gap-3 sm:grid-cols-2">
      <label className="flex flex-col gap-1 text-sm">Amount received (USD)<input className={input} required type="number" min="0.01" max="99999999.99" step="0.01" value={amount} onChange={event => setAmount(event.target.value)} /></label>
      <label className="flex flex-col gap-1 text-sm">Payment method<select className={input} value={method} onChange={event => setMethod(event.target.value as ExternalPaymentMethod)}>
        <option value="CASH">Cash</option><option value="VENMO">Venmo</option><option value="BANK_TRANSFER">Bank transfer</option><option value="OTHER">Other</option></select></label>
      <label className="flex flex-col gap-1 text-sm">Payment date<input className={input} required type="date" max={businessToday()} value={paidOn} onChange={event => setPaidOn(event.target.value)} /></label>
      <label className="flex flex-col gap-1 text-sm">Reference (optional)<input className={input} maxLength={120} value={reference} onChange={event => setReference(event.target.value)} /></label>
    </div>}
    {error && <p role="alert" className="text-sm text-rose-700">{error}</p>}
    <div className="flex gap-2"><button className={button} disabled={busy}>{busy ? "Saving…" : reviewing ? "Confirm payment record" : "Review payment"}</button>
      <button type="button" className={button} disabled={busy} onClick={onCancel}>Cancel</button></div>
  </form>;
}
