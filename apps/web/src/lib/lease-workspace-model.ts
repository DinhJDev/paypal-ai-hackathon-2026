import { uuidPattern } from "./rent-ledger-model";

export type LeaseTerms = {
  tenantName: string; suiteLabel: string; startDate: string | null; endDate: string | null;
  amount: number | null; frequency: "WEEKLY" | "MONTHLY" | null; firstDueDate: string | null;
  lateFeeTerms: string; furnishedItems: string;
};
export type LeaseWorkspace = {
  id: string; status: "WORKING" | "CONFIRMED"; revision: number; terms: LeaseTerms;
  leaseStatus: "UNCONFIRMED" | "UPCOMING" | "ACTIVE" | "ENDED";
  confirmationIssues: string[]; previewDueDates: string[]; hasMoreDueDates: boolean;
  scheduleId: string | null; updatedAt: string; confirmedAt: string | null;
};
export type LeaseFields = { [K in keyof LeaseTerms]: string };

function date(value: unknown): value is string {
  if (typeof value !== "string" || !/^(19|20|21)\d{2}-\d{2}-\d{2}$/.test(value)) return false;
  const parsed = new Date(value + "T00:00:00Z");
  return Number.isFinite(parsed.getTime()) && parsed.toISOString().slice(0, 10) === value && value <= "2100-12-31";
}
function instant(value: unknown): value is string {
  return typeof value === "string" && /^\d{4}-\d{2}-\d{2}T/.test(value) && Number.isFinite(Date.parse(value));
}
function money(value: unknown): value is number {
  return typeof value === "number" && Number.isFinite(value) && value > 0 && value <= 99999999.99 &&
    Math.abs(value * 100 - Math.round(value * 100)) < 0.000001;
}
function safeTerms(value: unknown): LeaseTerms | null {
  if (!value || typeof value !== "object") return null;
  const t = value as Record<string, unknown>;
  for (const [key, limit] of [["tenantName", 120], ["suiteLabel", 120], ["lateFeeTerms", 2000], ["furnishedItems", 4000]] as const)
    if (typeof t[key] !== "string" || t[key].length > limit) return null;
  if ([t.startDate, t.endDate, t.firstDueDate].some(v => v !== null && !date(v)) ||
    t.amount !== null && !money(t.amount) || t.frequency !== null && t.frequency !== "WEEKLY" && t.frequency !== "MONTHLY") return null;
  return { tenantName: t.tenantName as string, suiteLabel: t.suiteLabel as string, startDate: t.startDate as string | null,
    endDate: t.endDate as string | null, amount: t.amount as number | null, frequency: t.frequency as LeaseTerms["frequency"],
    firstDueDate: t.firstDueDate as string | null, lateFeeTerms: t.lateFeeTerms as string, furnishedItems: t.furnishedItems as string };
}
export function safeLeaseWorkspace(value: unknown): LeaseWorkspace | null {
  if (!value || typeof value !== "object") return null;
  const v = value as Record<string, unknown>; const terms = safeTerms(v.terms);
  if (!terms || typeof v.id !== "string" || !uuidPattern.test(v.id) || !Number.isSafeInteger(v.revision) || (v.revision as number) < 0 ||
    v.status !== "WORKING" && v.status !== "CONFIRMED" || !["UNCONFIRMED", "UPCOMING", "ACTIVE", "ENDED"].includes(String(v.leaseStatus)) ||
    !Array.isArray(v.confirmationIssues) || v.confirmationIssues.length > 12 || v.confirmationIssues.some(i => typeof i !== "string" || i.length > 200) ||
    !Array.isArray(v.previewDueDates) || v.previewDueDates.length > 5 || v.previewDueDates.some(d => !date(d)) ||
    typeof v.hasMoreDueDates !== "boolean" || !instant(v.updatedAt)) return null;
  if (v.status === "CONFIRMED") {
    if (typeof v.scheduleId !== "string" || !uuidPattern.test(v.scheduleId) || !instant(v.confirmedAt) || v.leaseStatus === "UNCONFIRMED" ||
      !terms.tenantName.trim() || !terms.suiteLabel.trim() || !terms.startDate || !terms.endDate || !terms.firstDueDate ||
      terms.amount === null || terms.frequency === null || terms.startDate > terms.firstDueDate || terms.firstDueDate > terms.endDate ||
      v.confirmationIssues.length > 0) return null;
  } else if (v.scheduleId !== null || v.confirmedAt !== null || v.leaseStatus !== "UNCONFIRMED") return null;
  if (v.previewDueDates.some((d, i, all) => !terms.firstDueDate || !terms.endDate || d < terms.firstDueDate || d > terms.endDate || i > 0 && d <= all[i - 1])) return null;
  return { id: v.id, status: v.status, revision: v.revision as number, terms, leaseStatus: v.leaseStatus as LeaseWorkspace["leaseStatus"],
    confirmationIssues: v.confirmationIssues, previewDueDates: v.previewDueDates, hasMoreDueDates: v.hasMoreDueDates,
    scheduleId: v.scheduleId as string | null, updatedAt: v.updatedAt, confirmedAt: v.confirmedAt as string | null };
}
function fields(terms: LeaseTerms): LeaseFields {
  return { tenantName: terms.tenantName, suiteLabel: terms.suiteLabel, startDate: terms.startDate ?? "", endDate: terms.endDate ?? "",
    amount: terms.amount === null ? "" : terms.amount.toFixed(2), frequency: terms.frequency ?? "", firstDueDate: terms.firstDueDate ?? "",
    lateFeeTerms: terms.lateFeeTerms, furnishedItems: terms.furnishedItems };
}
function inputTerms(value: LeaseFields): LeaseTerms {
  const terms = safeTerms({ ...value, amount: value.amount.trim() === "" ? null : Number(value.amount), frequency: value.frequency || null,
    startDate: value.startDate || null, endDate: value.endDate || null, firstDueDate: value.firstDueDate || null });
  if (!terms) throw new LeaseSaveError(400, "Use valid dates, a positive rent amount with at most two decimals, and the field length limits. Your edits are still here.");
  return terms;
}
export class LeaseSaveError extends Error {
  constructor(public readonly status: number, message: string) { super(message); }
}
type SaveTransport = (id: string, revision: number, terms: LeaseTerms) => Promise<LeaseWorkspace>;
export class LeaseDraftSession {
  private lease: LeaseWorkspace;
  private fields: LeaseFields;
  private editVersion = 0;
  private savedVersion = 0;
  private busy = false;
  private error = "";
  private conflict = false;
  private pending: Promise<LeaseWorkspace> | null = null;
  private listeners = new Set<() => void>();
  constructor(lease: LeaseWorkspace, private readonly transport: SaveTransport) { this.lease = lease; this.fields = fields(lease.terms); }
  snapshot() { return { lease: this.lease, fields: { ...this.fields }, dirty: this.editVersion !== this.savedVersion,
    editVersion: this.editVersion, busy: this.busy, error: this.error, conflict: this.conflict }; }
  subscribe(listener: () => void) { this.listeners.add(listener); return () => { this.listeners.delete(listener); }; }
  private notify() { this.listeners.forEach(listener => listener()); }
  edit(key: keyof LeaseFields, value: string) {
    if (this.lease.status === "CONFIRMED") return;
    this.fields = { ...this.fields, [key]: value }; this.editVersion++; this.error = ""; this.notify();
  }
  save(): Promise<LeaseWorkspace> {
    if (this.conflict) return Promise.reject(new LeaseSaveError(409, "Reload the saved lease before saving again. Your local edits are still here."));
    if (this.pending) return this.pending;
    this.busy = true; this.error = ""; this.notify();
    this.pending = this.drain().finally(() => { this.pending = null; this.busy = false; this.notify(); });
    return this.pending;
  }
  private async drain(): Promise<LeaseWorkspace> {
    try {
      while (this.savedVersion !== this.editVersion) {
        const version = this.editVersion;
        const saved = await this.transport(this.lease.id, this.lease.revision, inputTerms(this.fields));
        this.lease = saved; this.savedVersion = version;
        this.notify();
      }
      return this.lease;
    } catch (error) {
      this.conflict = error instanceof LeaseSaveError && error.status === 409;
      this.error = error instanceof Error ? error.message : "Could not save. Your edits are still here.";
      this.notify(); throw error;
    }
  }
  navigateWhenSaved(navigate: () => void): boolean {
    if (this.busy || this.savedVersion !== this.editVersion) return false;
    navigate(); return true;
  }
  replaceSaved(lease: LeaseWorkspace, expectedEditVersion = this.editVersion) {
    if (expectedEditVersion !== this.editVersion) throw new LeaseSaveError(409, "Your edits changed while loading. They have been kept; reload again when ready.");
    if (lease.id !== this.lease.id || this.busy) throw new Error("Cannot replace this workspace.");
    this.lease = lease; this.fields = fields(lease.terms); this.savedVersion = this.editVersion;
    this.error = ""; this.conflict = false; this.notify();
  }
}
