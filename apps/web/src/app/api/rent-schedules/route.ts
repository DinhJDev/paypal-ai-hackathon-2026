import { rentApiRequest } from "@/lib/rent-api";

export async function GET(): Promise<Response> {
  return rentApiRequest("", "GET", "/api/rent-schedules", undefined, true);
}

export async function POST(request: Request): Promise<Response> {
  try {
    return rentApiRequest("", "POST", "/api/rent-schedules", await request.json(), false, request);
  } catch {
    return Response.json({ type: "about:blank", title: "Bad Request", status: 400,
      detail: "Check the rent schedule details and try again.", instance: "/api/rent-schedules" }, {
      status: 400, headers: { "Cache-Control": "private, no-store", "Content-Type": "application/problem+json" },
    });
  }
}
