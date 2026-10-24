import { rentApiRequest } from "@/lib/rent-api";
import { uuidPattern } from "@/lib/rent-ledger-model";
export async function POST(request: Request, context: { params: Promise<{ id: string }> }): Promise<Response> {
  const { id } = await context.params;
  if (!uuidPattern.test(id)) return Response.json({ status: 404, detail: "Rent schedule was not found." },
    { status: 404, headers: { "Cache-Control": "private, no-store" } });
  let body: unknown;
  try { body = await request.json(); } catch {
    return Response.json({ status: 400, detail: "Review the autopay start date." },
      { status: 400, headers: { "Cache-Control": "private, no-store" } });
  }
  return rentApiRequest(`/${id}/autopay-setup`, "POST", `/api/rent-schedules/${id}/autopay-setup`, body, false, request);
}
