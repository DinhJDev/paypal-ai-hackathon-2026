import "server-only";
import { Auth0Client } from "@auth0/nextjs-auth0/server";
import { readAuthConfig } from "./auth-config";

let client: Auth0Client | undefined;

export function getAuth0(): Auth0Client | null {
  const config = readAuthConfig(process.env);
  if (!config) return null;
  client ??= new Auth0Client({
    domain: config.domain,
    clientId: config.clientId,
    clientSecret: config.clientSecret,
    secret: config.sessionSecret,
    appBaseUrl: config.appBaseUrl,
    signInReturnToPath: "/dashboard",
    enableAccessTokenEndpoint: false,
    authorizationParameters: {
      audience: config.audience,
      scope: "openid profile email offline_access",
    },
  });
  return client;
}
