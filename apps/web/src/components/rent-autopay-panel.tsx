"use client";

import dynamic from "next/dynamic";
import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { AlertCircle, ArrowUpRight, Building2, CalendarClock, CalendarDays, CheckCircle2, CircleDollarSign, Clock3, Plus, RefreshCw, Search, Wrench } from "lucide-react";
import {
  buildRecentRentPayments,
  buildRentAttentionItems,
  buildRentCalendarEvents,
  summarizeRentSchedules,
  type RentCalendarEvent,
  type RentSchedule,
} from "@/lib/rent-dashboard-model";

const PortfolioGrid = dynamic(
  () => import("./portfolio-grid").then(module => module.PortfolioGrid),
  { ssr: false, loading: () => <div role="status" className="h-40 animate-pulse rounded-lg bg-slate-100" /> },
);

const RentCalendar = dynamic(
  () => import("./rent-calendar").then(module => module.RentCalendar),
  { ssr: false, loading: () => <div role="status" className="h-[680px] animate-pulse bg-slate-100" /> },
);

function tomorrow(): string {
  const value = new Date();
  value.setDate(value.getDate() + 1);
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}

function localToday(): string {
  const value = new Date();
  return `${value.getFullYear()}-${String(value.getMonth() + 1).padStart(2, "0")}-${String(value.getDate()).padStart(2, "0")}`;
}

function money(amount: number): string {
  return new Intl.NumberFormat("en-US", { style: "currency", currency: "USD" }).format(amount);
}

function displayDate(value: string): string {
  const date = new Date(value + "T12:00:00");
  return Number.isNaN(date.getTime()) ? value : new Intl.DateTimeFormat("en-US", {
    month: "short", day: "numeric", year: "numeric",
  }).format(date);
}

function statusClass(status: string): string {
  if (status === "FAILED" || status === "PAYMENT_FAILED" || status === "SETUP_FAILED" || status === "PAST_DUE") return "bg-rose-50 text-rose-700";
  if (status === "PENDING_APPROVAL" || status === "PROCESSING") return "bg-amber-50 text-amber-800";
  if (status === "COMPLETED" || status === "ACTIVE") return "bg-emerald-50 text-emerald-800";
  return "bg-slate-100 text-slate-600";
}

async function readJson(response: Response): Promise<unknown> {
  try { return await response.json(); } catch { return null; }
}

export function RentAutopayPanel() {
  const [schedules, setSchedules] = useState<RentSchedule[]>([]);
  const [tenantLabel, setTenantLabel] = useState("");
  const [amount, setAmount] = useState("");
  const [frequency, setFrequency] = useState<"WEEKLY" | "MONTHLY">("MONTHLY");
  const [nextDueDate, setNextDueDate] = useState(tomorrow);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [formOpen, setFormOpen] = useState(false);
  const [searchText, setSearchText] = useState("");
  const [activeView, setActiveView] = useState<"portfolio" | "calendar">("portfolio");
  const [updatingScheduleId, setUpdatingScheduleId] = useState<string | null>(null);
  const [scheduleMessage, setScheduleMessage] = useState<{ id: string; text: string } | null>(null);
  const [error, setError] = useState("");

  const reload = useCallback(async () => {
    try {
      const response = await fetch("/api/rent-schedules", { cache: "no-store", credentials: "same-origin" });
      const payload = await readJson(response);
      if (!response.ok || !Array.isArray(payload)) throw new Error("Rent schedules are temporarily unavailable.");
      setSchedules(payload as RentSchedule[]);
      setError("");
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Rent schedules are temporarily unavailable.");
    } finally { setLoading(false); }
  }, []);

  useEffect(() => {
    const frame = window.requestAnimationFrame(() => { void reload(); });
    return () => window.cancelAnimationFrame(frame);
  }, [reload]);

  const updateSchedule = useCallback(async (id: string, action: "approval-check" | "pause") => {
    setError("");
    setScheduleMessage(null);
    setUpdatingScheduleId(id);
    try {
      const response = await fetch(`/api/rent-schedules/${id}/${action}`, {
        method: "POST", credentials: "same-origin", cache: "no-store",
      });
      const payload = await readJson(response);
      if (!response.ok || !payload || typeof payload !== "object") throw new Error("Could not update this rent schedule.");
      const updated = payload as RentSchedule;
      setSchedules(current => current.map(schedule => schedule.id === id ? updated : schedule));
      if (action === "approval-check") {
        setScheduleMessage({
          id,
          text: updated.status === "ACTIVE"
            ? updated.autopayEnabled
              ? "PayPal approval confirmed. Automatic charges are enabled."
              : "PayPal approval confirmed. Automatic charges are disabled in API settings."
            : `PayPal approval is still pending. Current status: ${updated.status.replaceAll("_", " ")}.`,
        });
      }
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Could not update this rent schedule.");
    } finally {
      setUpdatingScheduleId(null);
    }
  }, []);

  async function createSchedule(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSaving(true);
    setError("");
    try {
      const response = await fetch("/api/rent-schedules", {
        method: "POST", credentials: "same-origin", cache: "no-store",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ tenantLabel, amount: Number(amount), frequency, nextDueDate }),
      });
      const payload = await readJson(response);
      if (!response.ok) throw new Error("Could not create the rent schedule. Check the details and sandbox setup.");
      if (payload && typeof payload === "object") setSchedules(current => [payload as RentSchedule, ...current]);
      setTenantLabel("");
      setAmount("");
      setFormOpen(false);
    } catch (failure) {
      setError(failure instanceof Error ? failure.message : "Could not create the rent schedule.");
    } finally { setSaving(false); }
  }

  const summary = useMemo(() => summarizeRentSchedules(schedules, localToday()), [schedules]);
  const attentionItems = useMemo(() => buildRentAttentionItems(schedules), [schedules]);
  const recentPayments = useMemo(() => buildRecentRentPayments(schedules).slice(0, 6), [schedules]);
  const calendarEvents = useMemo<RentCalendarEvent[]>(() =>
    buildRentCalendarEvents(schedules, "0000-01-01", "9999-12-31"), [schedules]);
  const upcomingSchedules = useMemo(() => schedules
    .filter(schedule => schedule.status !== "SETUP_FAILED")
    .slice()
    .sort((left, right) => left.nextDueDate.localeCompare(right.nextDueDate))
    .slice(0, 5), [schedules]);

  return (
    <div className="space-y-6">
      <section className="flex flex-col gap-4 rounded-2xl border bg-white p-5 shadow-sm sm:flex-row sm:items-center sm:justify-between sm:p-6">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.16em] text-slate-500">Owner overview</p>
          <h2 className="mt-1 text-2xl font-semibold tracking-tight">Your portfolio</h2>
          <p className="mt-1 text-sm text-slate-600">Rent performance and suite activity, all in one place.</p>
        </div>
        <div className="flex flex-wrap items-center gap-3">
          <span className="inline-flex items-center gap-2 rounded-full border border-sky-200 bg-sky-50 px-3 py-1.5 text-xs font-semibold text-sky-900">
            <span className="size-2 rounded-full bg-sky-500" aria-hidden="true" /> PayPal sandbox
          </span>
          <button type="button" onClick={() => { setLoading(true); void reload(); }} disabled={loading}
            className="inline-flex items-center gap-2 rounded-lg border px-3 py-2 text-sm font-medium transition hover:bg-slate-50 disabled:opacity-50">
            <RefreshCw className={`size-4 ${loading ? "animate-spin" : ""}`} aria-hidden="true" />
            {loading ? "Refreshing…" : "Refresh data"}
          </button>
        </div>
      </section>

      {error && <p role="alert" className="rounded-lg border border-rose-200 bg-rose-50 px-4 py-3 text-sm text-rose-800">{error}</p>}

      <section aria-label="Portfolio summary" className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <article className="rounded-xl border bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between text-sm text-slate-600">
            <span>Rent due in 7 days</span><CalendarClock className="size-4 text-slate-400" aria-hidden="true" />
          </div>
          <p className="mt-3 text-2xl font-semibold tracking-tight">{money(summary.dueWithinSevenDaysAmount)}</p>
          <p className="mt-1 text-xs text-slate-500">Across {summary.dueWithinSevenDaysCount} scheduled {summary.dueWithinSevenDaysCount === 1 ? "payment" : "payments"}</p>
        </article>
        <article className="rounded-xl border bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between text-sm text-slate-600">
            <span>Automatic charges enabled</span><CheckCircle2 className="size-4 text-emerald-600" aria-hidden="true" />
          </div>
          <p className="mt-3 text-2xl font-semibold tracking-tight">{summary.activeAutopayCount}</p>
          <p className="mt-1 text-xs text-slate-500">of {schedules.length} rent schedules</p>
        </article>
        <article className="rounded-xl border bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between text-sm text-slate-600">
            <span>Awaiting approval</span><Clock3 className="size-4 text-amber-600" aria-hidden="true" />
          </div>
          <p className="mt-3 text-2xl font-semibold tracking-tight">{summary.pendingApprovalCount}</p>
          <p className="mt-1 text-xs text-slate-500">Tenant approval is still needed</p>
        </article>
        <article className="rounded-xl border bg-white p-5 shadow-sm">
          <div className="flex items-center justify-between text-sm text-slate-600">
            <span>Payment issues</span><AlertCircle className="size-4 text-rose-600" aria-hidden="true" />
          </div>
          <p className="mt-3 text-2xl font-semibold tracking-tight">{summary.paymentIssueCount}</p>
          <p className="mt-1 text-xs text-slate-500">Failed rent payments to review</p>
        </article>
      </section>

      <section aria-labelledby="portfolio-title" className="overflow-hidden rounded-2xl border bg-white shadow-sm">
        <div className="flex flex-col gap-4 border-b p-5 sm:p-6">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <div className="flex items-center gap-2">
                <h3 id="portfolio-title" className="text-lg font-semibold">{activeView === "portfolio" ? "Portfolio" : "Rent calendar"}</h3>
                <span className="rounded-full bg-slate-100 px-2.5 py-1 text-xs font-medium text-slate-600">{schedules.length} {schedules.length === 1 ? "suite" : "suites"}</span>
              </div>
              <p className="mt-1 text-sm text-slate-500">{activeView === "portfolio" ? "Review rent schedules, due dates, and autopay status." : "See scheduled rent and recorded payment outcomes by date."}</p>
            </div>
            <div aria-label="Dashboard view" className="inline-flex w-fit rounded-lg bg-slate-100 p-1">
              <button type="button" aria-pressed={activeView === "portfolio"} onClick={() => setActiveView("portfolio")}
                className={`inline-flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition ${activeView === "portfolio" ? "bg-white text-slate-950 shadow-sm" : "text-slate-500 hover:text-slate-900"}`}>
                <Building2 className="size-4" aria-hidden="true" /> Portfolio
              </button>
              <button type="button" aria-pressed={activeView === "calendar"} onClick={() => setActiveView("calendar")}
                className={`inline-flex items-center gap-2 rounded-md px-3 py-1.5 text-sm font-medium transition ${activeView === "calendar" ? "bg-white text-slate-950 shadow-sm" : "text-slate-500 hover:text-slate-900"}`}>
                <CalendarDays className="size-4" aria-hidden="true" /> Calendar
              </button>
            </div>
          </div>
          {activeView === "portfolio" && <label className="relative block w-full sm:max-w-xs">
            <span className="sr-only">Search suites and tenants</span>
            <Search className="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-slate-400" aria-hidden="true" />
            <input value={searchText} onChange={event => setSearchText(event.target.value)}
              className="w-full rounded-lg border bg-white py-2 pl-9 pr-3 text-sm outline-none transition placeholder:text-slate-400 focus:border-slate-400 focus:ring-2 focus:ring-slate-200"
              placeholder="Search portfolio" />
          </label>}
        </div>
        {activeView === "portfolio"
          ? loading && schedules.length === 0
            ? <p role="status" className="p-6 text-sm text-slate-500">Loading your portfolio…</p>
            : schedules.length === 0
              ? <div className="p-8 text-center">
                  <Building2Placeholder />
                  <p className="mt-3 font-medium">Your portfolio is ready for its first suite</p>
                  <p className="mt-1 text-sm text-slate-500">Create a rent schedule to start tracking rent and autopay.</p>
                </div>
              : <PortfolioGrid schedules={schedules} searchText={searchText} updatingScheduleId={updatingScheduleId} onUpdateSchedule={updateSchedule} />
          : calendarEvents.length === 0
            ? <div className="p-8 text-center">
                <CalendarClock className="mx-auto size-8 text-slate-400" aria-hidden="true" />
                <p className="mt-3 font-medium">No rent events to show yet</p>
                <p className="mt-1 text-sm text-slate-500">Create a rent schedule or record a payment to populate the calendar.</p>
              </div>
            : <RentCalendar events={calendarEvents} />}
        {scheduleMessage && <p role="status" className="border-t bg-emerald-50 px-5 py-3 text-sm text-emerald-800">{scheduleMessage.text}</p>}
        <div className="flex items-center justify-between gap-3 border-t bg-slate-50/70 px-5 py-3 text-xs text-slate-500 sm:px-6">
          <span>{activeView === "portfolio" ? "Rent collection is processed through the PayPal sandbox." : `${calendarEvents.length} rent due dates and recorded payments; no future recurrence is projected.`}</span>
          <span>USD</span>
        </div>
      </section>

      {attentionItems.length > 0 && <section aria-labelledby="attention-title" className="rounded-2xl border border-amber-200 bg-amber-50/60 p-5 sm:p-6">
        <div className="flex items-start gap-3">
          <span className="rounded-lg bg-amber-100 p-2 text-amber-800"><Wrench className="size-4" aria-hidden="true" /></span>
          <div className="min-w-0 flex-1">
            <h3 id="attention-title" className="font-semibold">Needs your attention <span className="ml-1 text-sm font-normal text-amber-800">{attentionItems.length}</span></h3>
            <ul className="mt-3 divide-y divide-amber-200/70">
              {attentionItems.map(item => <li key={item.scheduleId} className="flex flex-wrap items-center justify-between gap-3 py-3 first:pt-0 last:pb-0">
                <div>
                  <p className="text-sm font-medium">{item.tenantLabel}</p>
                  <p className="mt-0.5 text-sm text-slate-600">{item.message}</p>
                </div>
                <a href="#portfolio-title" className="inline-flex items-center gap-1 text-sm font-medium text-slate-800 underline underline-offset-4">
                  Review <ArrowUpRight className="size-3.5" aria-hidden="true" />
                </a>
              </li>)}
            </ul>
          </div>
        </div>
      </section>}

      <div className="grid gap-5 lg:grid-cols-2">
        <section aria-labelledby="upcoming-title" className="rounded-2xl border bg-white p-5 shadow-sm sm:p-6">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 id="upcoming-title" className="font-semibold">Upcoming rent</h3>
              <p className="mt-1 text-sm text-slate-500">The next due date for each suite.</p>
            </div>
            <CalendarClock className="size-5 text-slate-400" aria-hidden="true" />
          </div>
          {upcomingSchedules.length === 0
            ? <p className="mt-6 text-sm text-slate-500">Upcoming rent will appear when you add a schedule.</p>
            : <ul className="mt-4 divide-y">
                {upcomingSchedules.map(schedule => <li key={schedule.id} className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{schedule.tenantLabel}</p>
                    <p className="mt-1 text-xs text-slate-500">{displayDate(schedule.nextDueDate)} · {schedule.frequency.toLowerCase()}</p>
                  </div>
                  <p className="shrink-0 text-sm font-medium">{money(schedule.amount)}</p>
                </li>)}
              </ul>}
        </section>

        <section aria-labelledby="activity-title" className="rounded-2xl border bg-white p-5 shadow-sm sm:p-6">
          <div className="flex items-center justify-between gap-3">
            <div>
              <h3 id="activity-title" className="font-semibold">Recent rent activity</h3>
              <p className="mt-1 text-sm text-slate-500">Recorded payment attempts and outcomes.</p>
            </div>
            <CircleDollarSign className="size-5 text-slate-400" aria-hidden="true" />
          </div>
          {recentPayments.length === 0
            ? <p className="mt-6 text-sm text-slate-500">No rent payments have been recorded yet.</p>
            : <ul className="mt-4 divide-y">
                {recentPayments.map((payment, index) => <li key={`${payment.scheduleId}-${payment.dueDate}-${payment.status}-${index}`} className="flex items-center justify-between gap-4 py-3 first:pt-0 last:pb-0">
                  <div className="min-w-0">
                    <p className="truncate text-sm font-medium">{payment.tenantLabel}</p>
                    <p className="mt-1 text-xs text-slate-500">Due {displayDate(payment.dueDate)}</p>
                  </div>
                  <div className="shrink-0 text-right">
                    <p className="text-sm font-medium">{money(payment.amount)}</p>
                    <span className={`mt-1 inline-block rounded-full px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide ${statusClass(payment.status)}`}>{payment.status.toLowerCase()}</span>
                  </div>
                </li>)}
              </ul>}
        </section>
      </div>

      <section className="overflow-hidden rounded-2xl border bg-white shadow-sm">
        <button type="button" aria-expanded={formOpen} aria-controls="create-rent-schedule-form"
          onClick={() => setFormOpen(open => !open)}
          className="flex w-full items-center justify-between gap-4 p-5 text-left transition hover:bg-slate-50 sm:px-6">
          <span>
            <span className="block font-semibold">Create a rent schedule</span>
            <span className="mt-1 block text-sm text-slate-500">Set weekly or monthly USD rent and send a sandbox approval link.</span>
          </span>
          <Plus className={`size-5 shrink-0 transition-transform ${formOpen ? "rotate-45" : ""}`} aria-hidden="true" />
        </button>
        {formOpen && <form id="create-rent-schedule-form" className="grid gap-4 border-t bg-slate-50/60 p-5 sm:grid-cols-2 sm:p-6" onSubmit={createSchedule}>
          <label className="flex flex-col gap-1.5 text-sm sm:col-span-2">
            Suite or tenant label
            <input required maxLength={120} value={tenantLabel} onChange={event => setTenantLabel(event.target.value)}
              className="rounded-lg border bg-white px-3 py-2.5" placeholder="Suite 1" />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            Rent amount (USD)
            <input required type="number" min="0.01" step="0.01" value={amount} onChange={event => setAmount(event.target.value)}
              className="rounded-lg border bg-white px-3 py-2.5" placeholder="1200.00" />
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            Frequency
            <select value={frequency} onChange={event => setFrequency(event.target.value as "WEEKLY" | "MONTHLY")}
              className="rounded-lg border bg-white px-3 py-2.5">
              <option value="WEEKLY">Weekly</option><option value="MONTHLY">Monthly</option>
            </select>
          </label>
          <label className="flex flex-col gap-1.5 text-sm">
            First due date
            <input required type="date" min={tomorrow()} value={nextDueDate} onChange={event => setNextDueDate(event.target.value)}
              className="rounded-lg border bg-white px-3 py-2.5" />
          </label>
          <div className="flex items-end">
            <button disabled={saving} className="inline-flex items-center gap-2 rounded-lg bg-slate-950 px-4 py-2.5 text-sm font-medium text-white transition hover:bg-slate-800 disabled:opacity-50">
              {saving ? "Creating…" : "Create schedule"}
            </button>
          </div>
          <p className="text-xs text-slate-500 sm:col-span-2">Automatic charges run only in the PayPal sandbox and only when explicitly enabled on the API.</p>
        </form>}
      </section>
    </div>
  );
}

function Building2Placeholder() {
  return <div className="mx-auto flex size-11 items-center justify-center rounded-xl bg-slate-100 text-slate-500"><Building2 className="size-5" aria-hidden="true" /></div>;
}
