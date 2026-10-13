export const authEnvironment = {
  NODE_ENV: "test" as const,
  AUTH0_DOMAIN: "suitefolk.us.auth0.com",
  AUTH0_CLIENT_ID: "test-client",
  AUTH0_CLIENT_SECRET: "test-client-secret",
  AUTH0_SECRET: "a".repeat(64),
  APP_BASE_URL: "http://localhost:3000",
  AUTH0_AUDIENCE: "https://api.suitefolk.com",
  SUITEFOLK_API_BASE_URL: "http://localhost:8080",
};
