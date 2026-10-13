export default function Home() {
  return (
    <main className="mx-auto flex w-full max-w-3xl flex-1 flex-col justify-center gap-6 px-6 py-24">
      <p className="text-sm font-semibold uppercase tracking-widest text-muted-foreground">Suitefolk</p>
      <h1 className="max-w-xl text-4xl font-semibold tracking-tight sm:text-5xl">A clearer view of your rental business.</h1>
      <p className="max-w-lg text-lg leading-8 text-muted-foreground">
        A workspace for independent rental owners. Sign in to access the early demo.
      </p>
      <a href="/auth/login" className="w-fit rounded-lg bg-primary px-5 py-3 font-medium text-primary-foreground hover:opacity-90">
        Sign in to Suitefolk
      </a>
    </main>
  );
}
