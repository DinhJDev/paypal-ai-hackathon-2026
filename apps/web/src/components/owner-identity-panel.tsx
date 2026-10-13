"use client";

import { useEffect, useState } from "react";

type State = "loading" | "ready" | "signin" | "forbidden" | "unavailable";

export function OwnerIdentityPanel() {
  const [state, setState] = useState<State>("loading");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    async function checkAccess() {
      try {
        const response = await fetch("/api/me", {
          cache: "no-store", credentials: "same-origin", signal: controller.signal,
        });
        const nextState = response.status === 200 ? "ready" : response.status === 401 ? "signin"
          : response.status === 403 ? "forbidden" : "unavailable";
        if (!controller.signal.aborted) setState(nextState);
      } catch {
        if (!controller.signal.aborted) setState("unavailable");
      }
    }
    void checkAccess();
    return () => controller.abort();
  }, [attempt]);

  return (
    <section className="rounded-xl border p-6" aria-live="polite">
      {state === "loading" && <p>Checking your access…</p>}
      {state === "ready" && <>
        <h2 className="text-xl font-medium">Your owner workspace is ready.</h2>
        <p className="mt-2 text-muted-foreground">You have confirmed owner access. More of the demo is on the way.</p>
      </>}
      {state === "forbidden" && <p>Owner access has not been enabled for this account. Please contact the demo owner.</p>}
      {state === "signin" && <p>Your session needs to be renewed. <a className="underline" href="/auth/login">Sign in again</a>.</p>}
      {state === "unavailable" && <>
        <p>Your workspace is temporarily unavailable.</p>
        <button className="mt-3 font-medium underline" onClick={() => { setState("loading"); setAttempt(value => value + 1); }}>Try again</button>
      </>}
    </section>
  );
}
