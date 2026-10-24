import { rentLedgerRoute } from "@/lib/rent-ledger-route";
export async function POST(request: Request, context: { params: Promise<{ id: string; periodId: string; paymentId: string }> }): Promise<Response> {
  return rentLedgerRoute(request, await context.params, "reversal");
}
