import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { GET, POST } from "@/app/api/rent-schedules/route";
import { authEnvironment } from "./auth-fixture";

const { factory, session, accessToken } = vi.hoisted(() => ({ factory: vi.fn(), session: vi.fn(), accessToken: vi.fn() }));
vi.mock("@/lib/auth0", () => ({ getAuth0: factory }));

const schedule = {
  id: "f216e512-81d4-4f50-a0fc-317952e91f15", tenantLabel: "Suite 1", amount: 425,
  currency: "USD", frequency: "WEEKLY", nextDueDate: "2026-10-22", status: "PENDING_APPROVAL",
  approvalUrl: "https://www.sandbox.paypal.com/approve/demo", payments: [], paypalPaymentTokenId: "never-send-to-browser",
};

beforeEach(() => {
  vi.resetAllMocks();
  for (const [key, value] of Object.entries(authEnvironment)) vi.stubEnv(key, value);
  factory.mockReturnValue({ getSession: session, getAccessToken: accessToken });
  session.mockResolvedValue({ user: { sub: "auth0|rent-owner" } });
  accessToken.mockResolvedValue({ token: "private-access-token" });
});
afterEach(() => { vi.unstubAllEnvs(); vi.unstubAllGlobals(); });

it("gets owner schedules through a private no-store server-side API proxy", async () => {
  const request = vi.fn().mockResolvedValue(Response.json([schedule]));
  vi.stubGlobal("fetch", request);
  const response = await GET();
  expect(response.status).toBe(200);
  expect(response.headers.get("cache-control")).toBe("private, no-store");
  expect(await response.json()).toEqual([{
    id: schedule.id, tenantLabel: schedule.tenantLabel, amount: schedule.amount, currency: "USD",
    frequency: "WEEKLY", nextDueDate: schedule.nextDueDate, status: "PENDING_APPROVAL",
    approvalUrl: schedule.approvalUrl, payments: [],
  }]);
  expect(request).toHaveBeenCalledWith(new URL("http://localhost:8080/api/v1/rent-schedules"), expect.objectContaining({
    method: "GET", headers: { Authorization: "Bearer private-access-token", Accept: "application/json" }, cache: "no-store",
  }));
});

it("forwards only schedule creation to the fixed API and sanitizes errors", async () => {
  const request = vi.fn().mockResolvedValue(new Response("private upstream stack", { status: 400 }));
  vi.stubGlobal("fetch", request);
  const body = { tenantLabel: "Suite 1", amount: 425, frequency: "WEEKLY", nextDueDate: "2026-10-22" };
  const response = await POST(new Request("http://localhost:3000/api/rent-schedules", {
    method: "POST", headers: { "Content-Type": "application/json", Origin: "http://localhost:3000" }, body: JSON.stringify(body),
  }));
  expect(response.status).toBe(400);
  expect(await response.text()).not.toContain("private upstream stack");
  expect(request).toHaveBeenCalledWith(new URL("http://localhost:8080/api/v1/rent-schedules"), expect.objectContaining({
    method: "POST", body: JSON.stringify(body), headers: expect.objectContaining({ Authorization: "Bearer private-access-token" }),
  }));
});

it("rejects cross-site state changes before forwarding the owner token", async () => {
  const request = vi.fn();
  vi.stubGlobal("fetch", request);
  const response = await POST(new Request("http://localhost:3000/api/rent-schedules", {
    method: "POST", headers: { "Content-Type": "application/json", Origin: "https://attacker.example" },
    body: JSON.stringify({ tenantLabel: "Suite 1", amount: 425, frequency: "WEEKLY", nextDueDate: "2026-10-22" }),
  }));
  expect(response.status).toBe(403);
  expect(request).not.toHaveBeenCalled();
});

it("does not call the API without a verified web session", async () => {
  session.mockResolvedValue(null);
  const request = vi.fn();
  vi.stubGlobal("fetch", request);
  const response = await GET();
  expect(response.status).toBe(401);
  expect(request).not.toHaveBeenCalled();
});
