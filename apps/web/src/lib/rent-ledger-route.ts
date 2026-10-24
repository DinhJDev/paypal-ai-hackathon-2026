import "server-only";
import { rentApiRequest } from "./rent-api";
import { uuidPattern } from "./rent-ledger-model";

type Params = { id: string; periodId?: string; paymentId?: string };
export async function rentLedgerRoute(request: Request, params: Params, action: "list" | "payment" | "reversal" | "review"): Promise<Response> {
  const ids = action === "list" ? [params.id] : action === "reversal" ? [params.id, params.periodId, params.paymentId] : [params.id, params.periodId];
  const instance = `/api/rent-schedules/${params.id}/ledger`;
  if (ids.some(id => typeof id !== "string" || !uuidPattern.test(id))) return Response.json({ status: 404, detail: "Rent record was not found." },
    { status: 404, headers: { "Cache-Control": "private, no-store" } });
  const suffix = action === "list" ? "" : action === "payment" ? `/${params.periodId}/payments`
    : action === "review" ? `/${params.periodId}/review` : `/${params.periodId}/payments/${params.paymentId}/reversal`;
  let body: unknown;
  if (action !== "list") {
    try { body = await request.json(); } catch {
      return Response.json({ status: 400, detail: "Check the payment details and try again." },
        { status: 400, headers: { "Cache-Control": "private, no-store" } });
    }
  }
  return rentApiRequest(`/${params.id}/ledger${suffix}`, action === "list" ? "GET" : "POST", instance,
    body, false, request, action === "list" ? "ledger" : "period");
}
