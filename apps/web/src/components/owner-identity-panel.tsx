"use client";

import { useEffect, useState } from "react";
import { RentAutopayPanel } from "@/components/rent-autopay-panel";

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
    <div aria-live="polite" className="space-y-5">
      {state === "loading" && <p role="status" className="rounded-xl border bg-white p-5 text-sm text-slate-500">Checking your owner access…</p>}
      {state === "ready" && <>
        <div className="flex flex-col gap-3 rounded-xl border border-emerald-200 bg-emerald-50/70 px-4 py-3 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 className="text-sm font-semibold text-emerald-950">Your owner workspace is ready</h2>
            <p className="mt-0.5 text-sm text-emerald-800">Owner access confirmed for this account.</p>
          </div>
          <span className="inline-flex w-fit items-center gap-2 rounded-full bg-white/80 px-3 py-1 text-xs font-medium text-emerald-800">
            <span className="size-1.5 rounded-full bg-emerald-500" aria-hidden="true" /> Owner access
          </span>
        </div>
        <RentAutopayPanel />
      </>}
      {state === "forbidden" && <p className="rounded-xl border border-amber-200 bg-amber-50 p-5 text-sm text-amber-900">Owner access has not been enabled for this account. Please contact the demo owner.</p>}
      {state === "signin" && <p className="rounded-xl border bg-white p-5 text-sm">Your session needs to be renewed. <a className="font-medium underline underline-offset-4" href="/auth/login">Sign in again</a>.</p>}
      {state === "unavailable" && <div className="rounded-xl border bg-white p-5">
        <p className="text-sm text-slate-600">Your workspace is temporarily unavailable.</p>
        <button className="mt-3 text-sm font-medium underline underline-offset-4" onClick={() => { setState("loading"); setAttempt(value => value + 1); }}>Try again</button>
      </div>}
    </div>
  );
}
