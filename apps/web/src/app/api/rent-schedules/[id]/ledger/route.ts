import { rentLedgerRoute } from "@/lib/rent-ledger-route";
export async function GET(request: Request, context: { params: Promise<{ id: string }> }): Promise<Response> {
  return rentLedgerRoute(request, await context.params, "list");
}
