import "server-only";
import { getAuth0 } from "./auth0";
import { readAuthConfig } from "./auth-config";
import { safeLeaseWorkspace } from "./lease-workspace-model";

const headers = { "Cache-Control": "private, no-store" };
function problem(status: number, instance: string): Response {
  const detail = status === 400 ? "Check the lease terms and review any missing information. Your edits have been kept."
    : status === 401 ? "Please sign in again." : status === 403 ? "This request or account is not allowed."
      : status === 404 ? "The lease workspace could not be found."
        : status === 409 ? "The saved lease changed or has already been confirmed. Your edits are still here. Reload saved terms before continuing."
          : "Lease workspaces are temporarily unavailable. Your edits are still here; try again.";
  const title = ({400:"Bad Request",401:"Unauthorized",403:"Forbidden",404:"Not Found",409:"Conflict",503:"Service Unavailable"} as Record<number,string>)[status];
  return Response.json({ type: "about:blank", status, title, detail, instance }, { status, headers: { ...headers, "Content-Type": "application/problem+json" } });
}
export async function leaseApiRequest(path: string, method: "GET" | "POST", instance: string, body?: unknown, request?: Request): Promise<Response> {
  if (path !== "" && !/^\/[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}(?:\/(draft|confirm))?$/i.test(path)) return problem(404, instance);
  try {
    const config = readAuthConfig(process.env); const auth = getAuth0();
    if (!config || !auth) return problem(503, instance);
    if (method === "POST" && request?.headers.get("origin") !== config.appBaseUrl) return problem(403, instance);
    const session = await auth.getSession();
    if (typeof session?.user.sub !== "string" || !session.user.sub) return problem(401, instance);
    let token: string;
    try { token = (await auth.getAccessToken()).token; if (!token) return problem(401, instance); }
    catch { return problem(401, instance); }
    const response = await fetch(new URL(`/api/v1/leases${path}`, config.apiOrigin), { method,
      headers: { Authorization: `Bearer ${token}`, Accept: "application/json", ...(body === undefined ? {} : { "Content-Type": "application/json" }) },
      ...(body === undefined ? {} : { body: JSON.stringify(body) }), cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5000) });
    if ([400,401,403,404,409].includes(response.status)) return problem(response.status, instance);
    if (response.status !== 200 || response.redirected || response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") return problem(503, instance);
    const raw = await response.json();
    const value = method === "GET" && path === "" ? Array.isArray(raw) && raw.every(item => safeLeaseWorkspace(item) !== null) ? raw.map(safeLeaseWorkspace) : null : safeLeaseWorkspace(raw);
    return value === null ? problem(503, instance) : Response.json(value, { headers });
  } catch { return problem(503, instance); }
}
