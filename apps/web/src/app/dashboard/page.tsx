import { Suspense } from "react";
import { redirect } from "next/navigation";
import { connection } from "next/server";
import { getAuth0 } from "@/lib/auth0";
import { OwnerIdentityPanel } from "@/components/owner-identity-panel";

async function Workspace() {
  // Configuration may be supplied at deployment after a build without secrets.
  await connection();
  const auth0 = getAuth0();
  if (!auth0) return <p className="rounded-xl border bg-white p-5 text-sm text-slate-600">Sign-in is temporarily unavailable. Please try again later.</p>;
  const session = await auth0.getSession();
  if (!session?.user.sub) redirect("/auth/login");
  const name = typeof session.user.name === "string" ? session.user.name : "Owner";
  return (
    <>
      <header className="flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
        <div>
          <p className="text-xs font-semibold uppercase tracking-[0.18em] text-slate-500">Suitefolk</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">Portfolio</h1>
          <p className="mt-1 text-sm text-slate-600">Manage suites, rent, and autopay in one place.</p>
        </div>
        <div className="flex items-center justify-between gap-4 border-t pt-3 sm:justify-end sm:border-0 sm:pt-0">
          <span className="max-w-[18rem] truncate text-sm text-slate-500">{name}</span>
          <a href="/auth/logout" className="text-sm font-medium underline underline-offset-4">Sign out</a>
        </div>
      </header>
      <OwnerIdentityPanel />
    </>
  );
}

export default function Dashboard() {
  return (
    <main className="mx-auto flex w-full max-w-screen-2xl flex-1 flex-col gap-6 px-4 py-8 sm:px-8 sm:py-10">
      <Suspense fallback={<p role="status" className="text-sm text-slate-500">Loading your workspace…</p>}>
        <Workspace />
      </Suspense>
    </main>
  );
}
