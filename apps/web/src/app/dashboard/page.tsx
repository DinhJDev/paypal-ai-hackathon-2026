import { Suspense } from "react";
import { redirect } from "next/navigation";
import { getAuth0 } from "@/lib/auth0";
import { OwnerIdentityPanel } from "@/components/owner-identity-panel";

async function Workspace() {
  const auth0 = getAuth0();
  if (!auth0) return <p>Sign-in is temporarily unavailable. Please try again later.</p>;
  const session = await auth0.getSession();
  if (!session?.user.sub) redirect("/auth/login");
  const name = typeof session.user.name === "string" ? session.user.name : "back";
  return (
    <>
      <h1 className="text-3xl font-semibold tracking-tight">Welcome, {name}.</h1>
      <OwnerIdentityPanel />
      <a href="/auth/logout" className="w-fit text-sm font-medium underline underline-offset-4">Sign out</a>
    </>
  );
}

export default function Dashboard() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col gap-6 px-6 py-16">
      <p className="text-sm font-semibold uppercase tracking-widest text-muted-foreground">Suitefolk</p>
      <Suspense fallback={<p role="status">Loading your workspace…</p>}>
        <Workspace />
      </Suspense>
    </main>
  );
}
