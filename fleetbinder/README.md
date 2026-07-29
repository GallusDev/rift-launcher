# FleetBinder

DOT compliance management for small trucking fleets (5–50 trucks). Every
driver, vehicle, and company has a checklist of dated requirements; the app
stores the proof, watches the dates, and nags the right person before
anything lapses.

See [PLAN.md](./PLAN.md) for the full product plan, market research summary,
architecture, and build milestones.

## Stack

Next.js (App Router) + TypeScript + Tailwind on Vercel; Supabase (Postgres +
Auth + Storage, RLS multi-tenancy); Stripe subscriptions; Resend email;
Twilio SMS.

## Development

```bash
npm install
cp .env.example .env.local   # fill in Supabase keys
npm run dev
```

Database schema lives in `supabase/migrations/` (plain SQL, applied with the
Supabase CLI). The federal requirement catalog seed — the product's brain —
is in `supabase/seed/requirement_catalog.sql`, with a CFR citation on every
row.

## Status

Phase 0 (scaffold) complete. Next: auth + tenancy (Phase 1). Milestones and
phase details are in PLAN.md.
