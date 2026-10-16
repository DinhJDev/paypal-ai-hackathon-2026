import { rentApiRequest } from "@/lib/rent-api";

const uuid = /^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i;

export async function POST(request: Request, context: { params: Promise<{ id: string }> }): Promise<Response> {
  const { id } = await context.params;
  if (!uuid.test(id)) return Response.json({ type: "about:blank", title: "Not Found", status: 404,
    detail: "The rent schedule could not be found.", instance: `/api/rent-schedules/${id}/pause` }, {
    status: 404, headers: { "Cache-Control": "private, no-store", "Content-Type": "application/problem+json" },
  });
  return rentApiRequest(`/${id}/pause`, "POST", `/api/rent-schedules/${id}/pause`, undefined, false, request);
}
