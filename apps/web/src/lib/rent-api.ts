import "server-only";

import { getAuth0 } from "./auth0";
import { safeRentPeriod } from "./rent-ledger-model";
import { readAuthConfig } from "./auth-config";

const privateHeaders = { "Cache-Control": "private, no-store" };
type ApiMethod = "GET" | "POST";

function problem(status: 400 | 401 | 403 | 404 | 409 | 503, instance: string): Response {
  const title = { 400: "Bad Request", 401: "Unauthorized", 403: "Forbidden", 404: "Not Found", 409: "Conflict", 503: "Service Unavailable" }[status];
  const detail = {
    400: "Check the rent schedule details and try again.",
    401: "Please sign in again.",
    403: "Owner access has not been enabled for this account.",
    404: "The rent schedule could not be found.",
    409: "The ledger changed or this confirmation was already used. Refresh and review the current balance.",
    503: "Rent scheduling is temporarily unavailable. Please try again.",
  }[status];
  return Response.json({ type: "about:blank", title, status, detail, instance }, {
    status, headers: { ...privateHeaders, "Content-Type": "application/problem+json" },
  });
}

function originProblem(instance: string): Response {
  return Response.json({ type: "about:blank", title: "Forbidden", status: 403,
    detail: "This request origin is not allowed.", instance }, {
    status: 403, headers: { ...privateHeaders, "Content-Type": "application/problem+json" },
  });
}

function safeSchedule(value: unknown): Record<string, unknown> | null {
  if (!value || typeof value !== "object") return null;
  const item = value as Record<string, unknown>;
  if (typeof item.id !== "string" || !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(item.id) ||
    typeof item.tenantLabel !== "string" || typeof item.amount !== "number" || !Number.isFinite(item.amount) ||
    item.currency !== "USD" || !["WEEKLY", "MONTHLY"].includes(String(item.frequency)) ||
    typeof item.nextDueDate !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(item.nextDueDate) ||
    !["SCHEDULED", "PENDING_APPROVAL", "SETUP_FAILED", "ACTIVE", "PAUSED", "PAYMENT_FAILED", "PAST_DUE"].includes(String(item.status)) ||
    typeof item.autopayEnabled !== "boolean" || !Array.isArray(item.payments)) return null;
  let approvalUrl: string | null = null;
  if (item.approvalUrl !== null && item.approvalUrl !== undefined) {
    try {
      const url = new URL(String(item.approvalUrl));
      const host = url.hostname.toLowerCase();
      if (url.protocol !== "https:" || url.username || url.password || (url.port && url.port !== "443") ||
        !(host === "sandbox.paypal.com" || host.endsWith(".sandbox.paypal.com"))) return null;
      approvalUrl = url.toString();
    } catch { return null; }
  }
  const payments = item.payments.map(value => {
    if (!value || typeof value !== "object") return null;
    const payment = value as Record<string, unknown>;
    if (typeof payment.dueDate !== "string" || !/^\d{4}-\d{2}-\d{2}$/.test(payment.dueDate) ||
      typeof payment.amount !== "number" || !Number.isFinite(payment.amount) || payment.currency !== "USD" ||
      !["PROCESSING", "COMPLETED", "FAILED", "REFUNDED"].includes(String(payment.status))) return null;
    return { dueDate: payment.dueDate, amount: payment.amount, currency: "USD", status: payment.status };
  });
  if (payments.some(payment => payment === null)) return null;
  return {
    id: item.id, tenantLabel: item.tenantLabel, amount: item.amount, currency: "USD",
    frequency: item.frequency, nextDueDate: item.nextDueDate, status: item.status,
    autopayEnabled: item.autopayEnabled, approvalUrl, payments,
    ...(item.endDate === null || typeof item.endDate === "string" && /^\d{4}-\d{2}-\d{2}$/.test(item.endDate) ? { endDate: item.endDate } : {}),
    ...(typeof item.scheduleEnded === "boolean" ? { scheduleEnded: item.scheduleEnded } : {}),
    ...(typeof item.autopayStartDate === "string" && /^\d{4}-\d{2}-\d{2}$/.test(item.autopayStartDate) ? { autopayStartDate: item.autopayStartDate } : {}),
    ...(typeof item.outstandingBalance === "number" && Number.isFinite(item.outstandingBalance) ? { outstandingBalance: item.outstandingBalance } : {}),
    ...(typeof item.reviewRequiredCount === "number" && Number.isSafeInteger(item.reviewRequiredCount) && item.reviewRequiredCount >= 0 ? { reviewRequiredCount: item.reviewRequiredCount } : {}),
  };
}

function safePayload(value: unknown, list: boolean): unknown | null {
  if (list) {
    if (!Array.isArray(value)) return null;
    const schedules = value.map(safeSchedule);
    return schedules.every(schedule => schedule !== null) ? schedules : null;
  }
  return safeSchedule(value);
}

export async function rentApiRequest(path: string, method: ApiMethod, instance: string,
  body?: unknown, list = false, request?: Request, shape: "schedule" | "ledger" | "period" = "schedule"): Promise<Response> {
  try {
    const config = readAuthConfig(process.env);
    const auth0 = getAuth0();
    if (!config || !auth0) return problem(503, instance);
    if (method === "POST" && request?.headers.get("origin") !== config.appBaseUrl) return originProblem(instance);
    const session = await auth0.getSession();
    if (typeof session?.user.sub !== "string" || !session.user.sub) return problem(401, instance);
    let accessToken: string;
    try {
      accessToken = (await auth0.getAccessToken()).token;
      if (!accessToken) return problem(401, instance);
    } catch { return problem(401, instance); }

    const response = await fetch(new URL(`/api/v1/rent-schedules${path}`, config.apiOrigin), {
      method,
      headers: {
        Authorization: `Bearer ${accessToken}`,
        Accept: "application/json",
        ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }),
      cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5000),
    });
    if (response.status === 401) return problem(401, instance);
    if (response.status === 403) return problem(403, instance);
    if (response.status === 404) return problem(404, instance);
    if (response.status === 400) return problem(400, instance);
    if (response.status === 409) return problem(409, instance);
    if (![200, 201].includes(response.status) || response.redirected ||
      response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") {
      return problem(503, instance);
    }
    const raw = await response.json();
    const payload = shape === "period" ? safeRentPeriod(raw) : shape === "ledger"
      ? Array.isArray(raw) && raw.every(value => safeRentPeriod(value) !== null) ? raw.map(safeRentPeriod) : null
      : safePayload(raw, list);
    if (payload === null) return problem(503, instance);
    return Response.json(payload, { status: response.status, headers: privateHeaders });
  } catch { return problem(503, instance); }
}
