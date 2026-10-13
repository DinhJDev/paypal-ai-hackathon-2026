import { expect, it, vi } from "vitest";
import { fetchOwnerIdentity } from "@/lib/owner-api";

const identity = { ownerId: "e0623a63-f680-4d56-b2ad-cf7c4a73e136", subject: "auth0|current-test-owner", role: "OWNER" };
const jsonResponse = (body: unknown, status = 200) => Response.json(body, { status });

it("forwards the current caller's credential to a fixed non-cached destination", async () => {
  const request = vi.fn().mockResolvedValue(jsonResponse({ ...identity, ignored: "private-upstream-field" }));
  expect(await fetchOwnerIdentity("http://localhost:8080", "test-token", identity.subject, request))
    .toEqual({ kind: "success", identity });
  expect(request).toHaveBeenCalledWith(new URL("http://localhost:8080/api/v1/me"), expect.objectContaining({
    headers: { Authorization: "Bearer test-token", Accept: "application/json" },
    cache: "no-store", redirect: "error", signal: expect.any(AbortSignal),
  }));
});
it.each([
  { ...identity, ownerId: "not-a-uuid" }, { ...identity, subject: "auth0|other-test-owner" },
  { ...identity, role: "ADMIN" }, { ...identity, subject: undefined }, null,
])("rejects a mismatched or malformed identity", async body => {
  expect(await fetchOwnerIdentity("http://localhost:8080", "test-token", identity.subject,
    vi.fn().mockResolvedValue(jsonResponse(body)))).toEqual({ kind: "unavailable" });
});
it.each([[401, "unauthenticated"], [403, "forbidden"], [500, "unavailable"], [302, "unavailable"]])(
  "sanitizes upstream status %s", async (status, kind) => {
    const request = vi.fn().mockResolvedValue(new Response("secret-upstream-details", { status: Number(status) }));
    expect(await fetchOwnerIdentity("http://localhost:8080", "test-token", identity.subject, request)).toEqual({ kind });
  },
);
it.each([
  new Response("not json", { headers: { "Content-Type": "application/json" } }),
  new Response(JSON.stringify(identity), { headers: { "Content-Type": "text/html" } }),
])("handles malformed JSON or an unexpected media type", async response => {
  expect(await fetchOwnerIdentity("http://localhost:8080", "test-token", identity.subject,
    vi.fn().mockResolvedValue(response))).toEqual({ kind: "unavailable" });
});
it.each([new Error("network-private-details"), new DOMException("timeout", "TimeoutError")])(
  "sanitizes fetch failures", async error => {
    expect(await fetchOwnerIdentity("http://localhost:8080", "test-token", identity.subject,
      vi.fn().mockRejectedValue(error))).toEqual({ kind: "unavailable" });
  },
);
it("keeps sequential callers' tokens and identities separate", async () => {
  const other = { ...identity, subject: "auth0|second-test-owner" };
  const request = vi.fn().mockResolvedValueOnce(jsonResponse(identity)).mockResolvedValueOnce(jsonResponse(other));
  expect(await fetchOwnerIdentity("http://localhost:8080", "first-token", identity.subject, request))
    .toEqual({ kind: "success", identity });
  expect(await fetchOwnerIdentity("http://localhost:8080", "second-token", other.subject, request))
    .toEqual({ kind: "success", identity: other });
  expect(request.mock.calls.map(call => call[1].headers.Authorization)).toEqual(["Bearer first-token", "Bearer second-token"]);
});
