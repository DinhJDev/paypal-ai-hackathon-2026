import { afterEach, beforeEach, expect, it, vi } from "vitest";
import { authEnvironment } from "./auth-fixture";

const constructed = vi.hoisted(() => vi.fn());
vi.mock("@auth0/nextjs-auth0/server", () => ({
  Auth0Client: class { constructor(options: unknown) { constructed(options); } },
}));
beforeEach(() => {
  vi.resetModules(); constructed.mockClear();
  for (const key of Object.keys(authEnvironment)) vi.stubEnv(key, "");
});
afterEach(() => vi.unstubAllEnvs());

it("does not construct a client when configuration is absent", async () => {
  const { getAuth0 } = await import("@/lib/auth0");
  expect(getAuth0()).toBeNull();
  expect(constructed).not.toHaveBeenCalled();
});
it("creates one server client with explicit audience and no browser token endpoint", async () => {
  for (const [key, value] of Object.entries(authEnvironment)) vi.stubEnv(key, value);
  const { getAuth0 } = await import("@/lib/auth0");
  const client = getAuth0();
  expect(client).not.toBeNull(); expect(getAuth0()).toBe(client);
  expect(constructed).toHaveBeenCalledTimes(1);
  expect(constructed).toHaveBeenCalledWith(expect.objectContaining({
    enableAccessTokenEndpoint: false, signInReturnToPath: "/dashboard",
    authorizationParameters: { audience: "https://api.suitefolk.com", scope: "openid profile email offline_access" },
  }));
});
