import { Suspense } from "react";
import Link from "next/link";

const outcomes: Record<string, { title: string; detail: string }> = {
  approved: { title: "PayPal approval received", detail: "Your owner dashboard will show the rent schedule as active. Return there to review it." },
  cancelled: { title: "Approval cancelled", detail: "No payment method was saved. You can reopen the approval link from the owner dashboard." },
  failed: { title: "Approval could not be confirmed", detail: "The sandbox approval was not confirmed. Check the schedule in your owner dashboard and try again." },
};

async function PayPalReturnContent({
  searchParams,
}: {
  searchParams: Promise<{ status?: string | string[] }>;
}) {
  const params = await searchParams;
  const status = typeof params.status === "string" ? params.status : "failed";
  const result = outcomes[status] ?? outcomes.failed;
  return (
    <main className="mx-auto flex w-full max-w-2xl flex-1 flex-col gap-5 px-6 py-16">
      <p className="text-sm font-semibold uppercase tracking-widest text-muted-foreground">Suitefolk · PayPal sandbox</p>
      <section className="rounded-xl border p-6">
        <h1 className="text-2xl font-semibold">{result.title}</h1>
        <p className="mt-3 text-muted-foreground">{result.detail}</p>
        <Link className="mt-6 inline-block font-medium underline underline-offset-4" href="/dashboard">
          Return to owner dashboard
        </Link>
      </section>
    </main>
  );
}

export default function PayPalReturnPage({
  searchParams,
}: {
  searchParams: Promise<{ status?: string | string[] }>;
}) {
  return <Suspense fallback={<main className="mx-auto w-full max-w-2xl px-6 py-16"><p role="status">Checking PayPal approval…</p></main>}>
    <PayPalReturnContent searchParams={searchParams} />
  </Suspense>;
}
