import "server-only";
import { leaseApiRequest } from "./lease-api";
export async function leaseRoute(request: Request, path: string, method: "GET" | "POST") {
  let body: unknown;
  if (method === "POST") {
    try { body = await request.json(); } catch {
      return Response.json({ status: 400, detail: "Check the lease details and try again." }, { status: 400, headers: { "Cache-Control": "private, no-store" } });
    }
  }
  return leaseApiRequest(path, method, `/api/leases${path}`, body, request);
}
