export type ExternalPaymentMethod = "CASH" | "VENMO" | "BANK_TRANSFER" | "OTHER";
export type ExternalPayment = {
  id: string; amount: number; paidOn: string; method: ExternalPaymentMethod; reference: string;
  recordedAt: string; reversedAt: string | null; reversalReason: string | null;
};
export type RentPeriod = {
  id: string; dueDate: string; amountDue: number; amountReceived: number; balance: number;
  paymentStatus: "UNPAID" | "PARTIALLY_PAID" | "PAID" | "OVERPAID";
  collectionHeld: boolean; reviewRequired: boolean; reconciliationRequired: boolean;
  holdReason: string | null; revision: number; reviewedAt: string | null;
  externalPayments: ExternalPayment[];
  paypalPayments: { dueDate: string; amount: number; currency: "USD"; status: "PROCESSING" | "COMPLETED" | "FAILED" | "REFUNDED" }[];
};
export const uuidPattern = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i;
const money = (value: unknown): value is number => typeof value === "number" && Number.isFinite(value) &&
  Math.abs(value * 100 - Math.round(value * 100)) < 0.00001;
const date = (value: unknown): value is string => typeof value === "string" && /^\d{4}-\d{2}-\d{2}$/.test(value) &&
  Number.isFinite(Date.parse(value + "T00:00:00Z")) && new Date(value + "T00:00:00Z").toISOString().slice(0, 10) === value;
const timestamp = (value: unknown): value is string => typeof value === "string" && Number.isFinite(Date.parse(value));

export function safeRentPeriod(value: unknown): RentPeriod | null {
  if (!value || typeof value !== "object") return null;
  const p = value as Record<string, unknown>;
  if (typeof p.id !== "string" || !uuidPattern.test(p.id) || !date(p.dueDate) ||
      !money(p.amountDue) || p.amountDue <= 0 || !money(p.amountReceived) || p.amountReceived < 0 || !money(p.balance) ||
      Math.round(p.amountDue * 100) - Math.round(p.amountReceived * 100) !== Math.round(p.balance * 100) ||
      typeof p.collectionHeld !== "boolean" || typeof p.reviewRequired !== "boolean" || typeof p.reconciliationRequired !== "boolean" ||
      !Number.isSafeInteger(p.revision) || (p.revision as number) < 0 ||
      !(p.reviewedAt === null || timestamp(p.reviewedAt)) ||
      !(p.holdReason === null || typeof p.holdReason === "string" && p.holdReason.length <= 32) ||
      !Array.isArray(p.externalPayments) || !Array.isArray(p.paypalPayments)) return null;
  const status = p.balance < 0 ? "OVERPAID" : p.balance === 0 ? "PAID" : p.amountReceived > 0 ? "PARTIALLY_PAID" : "UNPAID";
  if (p.paymentStatus !== status) return null;
  const external: ExternalPayment[] = [];
  for (const value of p.externalPayments) {
    if (!value || typeof value !== "object") return null;
    const r = value as Record<string, unknown>;
    if (typeof r.id !== "string" || !uuidPattern.test(r.id) || !money(r.amount) || r.amount <= 0 || !date(r.paidOn) ||
        !["CASH", "VENMO", "BANK_TRANSFER", "OTHER"].includes(String(r.method)) ||
        typeof r.reference !== "string" || r.reference.length > 120 || !timestamp(r.recordedAt) ||
        !(r.reversedAt === null || timestamp(r.reversedAt)) ||
        !(r.reversalReason === null || typeof r.reversalReason === "string" && r.reversalReason.length <= 240)) return null;
    external.push({ id: r.id, amount: r.amount, paidOn: r.paidOn, method: r.method as ExternalPaymentMethod,
      reference: r.reference, recordedAt: r.recordedAt, reversedAt: r.reversedAt, reversalReason: r.reversalReason });
  }
  const provider: RentPeriod["paypalPayments"] = [];
  for (const value of p.paypalPayments) {
    if (!value || typeof value !== "object") return null;
    const r = value as Record<string, unknown>;
    if (!date(r.dueDate) || !money(r.amount) || r.amount <= 0 || r.currency !== "USD" ||
        !["PROCESSING", "COMPLETED", "FAILED", "REFUNDED"].includes(String(r.status))) return null;
    provider.push({ dueDate: r.dueDate, amount: r.amount, currency: "USD", status: r.status as RentPeriod["paypalPayments"][number]["status"] });
  }
  return { id: p.id, dueDate: p.dueDate, amountDue: p.amountDue, amountReceived: p.amountReceived, balance: p.balance,
    paymentStatus: status, collectionHeld: p.collectionHeld, reviewRequired: p.reviewRequired,
    reconciliationRequired: p.reconciliationRequired, holdReason: p.holdReason, revision: p.revision as number,
    reviewedAt: p.reviewedAt, externalPayments: external, paypalPayments: provider };
}

export function businessToday(): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: "America/Chicago", year: "numeric", month: "2-digit", day: "2-digit" }).format(new Date());
}
