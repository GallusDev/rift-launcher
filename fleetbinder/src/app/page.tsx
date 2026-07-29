export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center bg-slate-950 px-6 text-slate-100">
      <div className="max-w-2xl text-center">
        <p className="mb-4 text-sm font-semibold uppercase tracking-widest text-amber-400">
          FleetBinder
        </p>
        <h1 className="text-4xl font-bold tracking-tight sm:text-5xl">
          Your DOT audit is coming.
          <br />
          Be ready in an afternoon.
        </h1>
        <p className="mt-6 text-lg text-slate-400">
          Driver qualification files, medical cards, annual inspections, and
          drug &amp; alcohol program records — tracked, alerted, and exported
          as an audit-ready binder in one click. Built for fleets of 5&ndash;50
          trucks. No hardware, no telematics tax.
        </p>
        <p className="mt-10 text-sm text-slate-500">
          Early access coming soon.
        </p>
      </div>
    </main>
  );
}
