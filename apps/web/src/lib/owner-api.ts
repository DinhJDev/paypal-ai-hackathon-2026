import "server-only";
import type { OwnerIdentity } from "./owner-identity";

export type OwnerResult =
  | { kind: "success"; identity: OwnerIdentity }
  | { kind: "unauthenticated" | "forbidden" | "unavailable" };

export async function fetchOwnerIdentity(apiOrigin: string, accessToken: string,
  expectedSubject: string, fetchImpl: typeof fetch = fetch): Promise<OwnerResult> {
  try {
    const response = await fetchImpl(new URL("/api/v1/me", apiOrigin), {
      headers: { Authorization: `Bearer ${accessToken}`, Accept: "application/json" },
      cache: "no-store", redirect: "error", signal: AbortSignal.timeout(5000),
    });
    if (response.status === 401) return { kind: "unauthenticated" };
    if (response.status === 403) return { kind: "forbidden" };
    if (response.status !== 200 || response.redirected ||
      response.headers.get("content-type")?.split(";")[0].trim().toLowerCase() !== "application/json") {
      return { kind: "unavailable" };
    }
    const body: unknown = await response.json();
    if (!body || typeof body !== "object") return { kind: "unavailable" };
    const { ownerId, subject, role } = body as Record<string, unknown>;
    if (typeof ownerId !== "string" || !/^[a-f0-9]{8}-(?:[a-f0-9]{4}-){3}[a-f0-9]{12}$/i.test(ownerId) ||
      typeof subject !== "string" || !subject || subject !== expectedSubject || role !== "OWNER") {
      return { kind: "unavailable" };
    }
    return { kind: "success", identity: { ownerId, subject, role } };
  } catch { return { kind: "unavailable" }; }
}
