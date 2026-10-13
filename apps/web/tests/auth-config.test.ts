import { describe, expect, it } from "vitest";
import { readAuthConfig } from "@/lib/auth-config";
import { authEnvironment } from "./auth-fixture";

describe("server authentication configuration", () => {
  it("accepts complete development configuration", () => {
    expect(readAuthConfig(authEnvironment)).toEqual({
      domain: authEnvironment.AUTH0_DOMAIN, clientId: "test-client",
      clientSecret: "test-client-secret", sessionSecret: "a".repeat(64),
      appBaseUrl: "http://localhost:3000", audience: "https://api.suitefolk.com",
      apiOrigin: "http://localhost:8080",
    });
  });
  it.each(Object.keys(authEnvironment).filter(key => key !== "NODE_ENV"))(
    "fails closed without %s", key => {
      expect(readAuthConfig({ ...authEnvironment, [key]: "" })).toBeNull();
    },
  );
  it.each([
    ["AUTH0_SECRET", "short"], ["AUTH0_SECRET", "z".repeat(64)],
    ["AUTH0_DOMAIN", "https://suitefolk.us.auth0.com"],
    ["AUTH0_DOMAIN", "evil.test/path"], ["AUTH0_DOMAIN", "localhost"],
    ["AUTH0_AUDIENCE", "test-client"],
    ["APP_BASE_URL", "https://user:password@example.test"],
    ["APP_BASE_URL", "https://example.test/?returnTo=evil"],
    ["APP_BASE_URL", "https://example.test/#fragment"],
    ["APP_BASE_URL", "http://example.test"],
    ["SUITEFOLK_API_BASE_URL", "http://example.test"],
    ["SUITEFOLK_API_BASE_URL", "https://api.example.test/path"],
  ])("rejects invalid %s", (key, value) => {
    expect(readAuthConfig({ ...authEnvironment, [key]: value })).toBeNull();
  });
  it("requires HTTPS in production", () => {
    expect(readAuthConfig({ ...authEnvironment, NODE_ENV: "production" })).toBeNull();
    expect(readAuthConfig({ ...authEnvironment, NODE_ENV: "production",
      APP_BASE_URL: "https://suitefolk.example.test", SUITEFOLK_API_BASE_URL: "https://api.example.test",
    })).not.toBeNull();
  });
});
