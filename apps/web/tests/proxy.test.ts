import { beforeEach, expect, it, vi } from "vitest";
import { NextRequest, NextResponse } from "next/server";
import { proxy } from "@/proxy";

const { factory, middleware } = vi.hoisted(() => ({ factory: vi.fn(), middleware: vi.fn() }));
vi.mock("@/lib/auth0", () => ({ getAuth0: factory }));
beforeEach(() => { factory.mockReset(); middleware.mockReset(); factory.mockReturnValue(null); });
it("returns a generic no-store failure for unconfigured authentication routes", async () => {
  const response = await proxy(new NextRequest("http://localhost:3000/auth/login"));
  expect(response.status).toBe(503);
  expect(response.headers.get("cache-control")).toBe("private, no-store");
  expect(await response.text()).toBe("Sign-in is temporarily unavailable.");
});
it("keeps the public page available without configuration", async () => {
  const response = await proxy(new NextRequest("http://localhost:3000/"));
  expect(response.headers.get("x-middleware-next")).toBe("1");
});
it("delegates configured requests to SDK session handling", async () => {
  const request = new NextRequest("http://localhost:3000/dashboard");
  const response = NextResponse.next(); middleware.mockResolvedValue(response);
  factory.mockReturnValue({ middleware });
  expect(await proxy(request)).toBe(response);
  expect(middleware).toHaveBeenCalledWith(request);
});
