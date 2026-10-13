import { getAuth0 } from "@/lib/auth0";
import { readAuthConfig } from "@/lib/auth-config";
import { fetchOwnerIdentity } from "@/lib/owner-api";

const privateHeaders = { "Cache-Control": "private, no-store" };

function problem(status: 401 | 403 | 503): Response {
  const title = { 401: "Unauthorized", 403: "Forbidden", 503: "Service Unavailable" }[status];
  const detail = {
    401: "Please sign in again.",
    403: "Owner access has not been enabled for this account.",
    503: "Your workspace is temporarily unavailable. Please try again.",
  }[status];
  return Response.json({ type: "about:blank", title, status, detail, instance: "/api/me" }, {
    status, headers: { ...privateHeaders, "Content-Type": "application/problem+json" },
  });
}

export async function GET(): Promise<Response> {
  try {
    const config = readAuthConfig(process.env);
    const auth0 = getAuth0();
    if (!config || !auth0) return problem(503);
    const session = await auth0.getSession();
    const subject = session?.user.sub;
    if (typeof subject !== "string" || !subject) return problem(401);
    let accessToken: string;
    try {
      // Route Handlers can persist cookies when the SDK refreshes an expired token.
      accessToken = (await auth0.getAccessToken()).token;
      if (!accessToken) return problem(401);
    } catch { return problem(401); }
    const result = await fetchOwnerIdentity(config.apiOrigin, accessToken, subject);
    if (result.kind === "success") return Response.json(result.identity, { headers: privateHeaders });
    return problem({ unauthenticated: 401, forbidden: 403, unavailable: 503 }[result.kind] as 401 | 403 | 503);
  } catch { return problem(503); }
}
