import "server-only";

export type AuthConfig = {
  domain: string;
  clientId: string;
  clientSecret: string;
  sessionSecret: string;
  appBaseUrl: string;
  audience: string;
  apiOrigin: string;
};

function origin(value: string, production: boolean): string | null {
  try {
    const url = new URL(value);
    const local = ["localhost", "127.0.0.1", "[::1]"].includes(url.hostname);
    if (url.username || url.password || url.search || url.hash || url.pathname !== "/") return null;
    if (url.protocol !== "https:" && !(url.protocol === "http:" && local && !production)) return null;
    return url.origin;
  } catch { return null; }
}

export function readAuthConfig(env: NodeJS.ProcessEnv): AuthConfig | null {
  const domain = env.AUTH0_DOMAIN;
  const clientId = env.AUTH0_CLIENT_ID;
  const clientSecret = env.AUTH0_CLIENT_SECRET;
  const sessionSecret = env.AUTH0_SECRET;
  const appUrl = env.APP_BASE_URL;
  const audience = env.AUTH0_AUDIENCE;
  const apiUrl = env.SUITEFOLK_API_BASE_URL;
  if (!domain || !clientId || !clientSecret || !sessionSecret || !appUrl || !audience || !apiUrl) return null;
  if ([domain, clientId, clientSecret, sessionSecret, appUrl, audience, apiUrl].some(value => value.trim() !== value)) return null;
  if (!/^(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$/i.test(domain)) return null;
  if (!/^[a-f0-9]{64}$/i.test(sessionSecret) || audience !== "https://api.suitefolk.com") return null;
  const appBaseUrl = origin(appUrl, env.NODE_ENV === "production");
  const apiOrigin = origin(apiUrl, env.NODE_ENV === "production");
  if (!appBaseUrl || !apiOrigin) return null;
  return { domain, clientId, clientSecret, sessionSecret, appBaseUrl, audience, apiOrigin };
}
