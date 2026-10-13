import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { GET } from "@/app/api/me/route";
import { authEnvironment } from "./auth-fixture";

const { factory, session, accessToken, gateway } = vi.hoisted(() => ({
  factory: vi.fn(), session: vi.fn(), accessToken: vi.fn(), gateway: vi.fn(),
}));
vi.mock("@/lib/auth0", () => ({ getAuth0: factory }));
vi.mock("@/lib/owner-api", () => ({ fetchOwnerIdentity: gateway }));
const identity = { ownerId: "e0623a63-f680-4d56-b2ad-cf7c4a73e136", subject: "auth0|test-owner", role: "OWNER" };
beforeEach(() => {
  vi.resetAllMocks();
  for (const [key, value] of Object.entries(authEnvironment)) vi.stubEnv(key, value);
  factory.mockReturnValue({ getSession: session, getAccessToken: accessToken });
  session.mockResolvedValue({ user: { sub: identity.subject } });
  accessToken.mockResolvedValue({ token: "private-test-access-token" });
  gateway.mockResolvedValue({ kind: "success", identity });
});
afterEach(() => vi.unstubAllEnvs());

it("returns only API-confirmed identity with no caching or credentials", async () => {
  const response = await GET();
  expect(response.status).toBe(200);
  expect(response.headers.get("Cache-Control")).toBe("private, no-store");
  expect(await response.json()).toEqual(identity);
  expect(gateway).toHaveBeenCalledWith("http://localhost:8080", "private-test-access-token", identity.subject);
});
it("returns 503 without complete server settings", async () => {
  vi.stubEnv("AUTH0_CLIENT_SECRET", "");
  const response = await GET(); expect(response.status).toBe(503);
  expect(gateway).not.toHaveBeenCalled(); expect(accessToken).not.toHaveBeenCalled();
});
it("returns 503 when the client is unavailable", async () => {
  factory.mockReturnValue(null);
  expect((await GET()).status).toBe(503);
});
it.each([null, { user: {} }, { user: { sub: "" } }])("requires a verified session subject", async value => {
  session.mockResolvedValue(value);
  const response = await GET(); expect(response.status).toBe(401);
  expect(accessToken).not.toHaveBeenCalled(); expect(gateway).not.toHaveBeenCalled();
});
it.each([["unauthenticated", 401], ["forbidden", 403], ["unavailable", 503]])(
  "returns a sanitized %s result", async (kind, status) => {
    gateway.mockResolvedValue({ kind });
    const response = await GET(); expect(response.status).toBe(status);
    expect(response.headers.get("content-type")).toContain("application/problem+json");
    const body = await response.json();
    expect(body).toEqual(expect.objectContaining({ type: "about:blank", status, instance: "/api/me" }));
    expect(JSON.stringify(body)).not.toContain("private-test-access-token");
  },
);
it("handles expired refresh credentials without leaking SDK errors", async () => {
  accessToken.mockRejectedValue(new Error("private-token-provider-error"));
  const response = await GET(); expect(response.status).toBe(401);
  expect(await response.text()).not.toContain("private-token-provider-error");
  expect(gateway).not.toHaveBeenCalled();
});
it("handles session read failures safely", async () => {
  session.mockRejectedValue(new Error("private-session-error"));
  const response = await GET(); expect(response.status).toBe(503);
  expect(await response.text()).not.toContain("private-session-error");
});
