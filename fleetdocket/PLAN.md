# FleetDocket — DOT Compliance SaaS for Small Trucking Fleets

## Context

We set out to find a side-income SaaS idea in a **less-saturated** business/productivity market. Three parallel market-research passes (competitor pricing, saturation analysis, underserved-niche hunt) eliminated the crowded options (document collection, client portals, cancellation waitlists — the last is being absorbed natively by booking platforms) and converged on **DOT compliance management for small trucking fleets (5–50 trucks)**:

- Small carriers track driver qualification files, CDL/medical-cert expirations, drug/alcohol consortium enrollment, and vehicle inspections with **paper files and spreadsheets**.
- A single missed expiration can mean out-of-service orders and fines up to **~$23k** — pain is regulatory, not aspirational.
- Incumbents are telematics-first at $35–50/truck/mo + hardware (Samsara, Motive) or consultant-priced (J.J. Keller). **No cheap software-only option exists.**
- Regulation = near-zero churn. FMCSA publishes new trucking authorities weekly = a free prospect list (new entrants face a mandatory safety audit within 12 months — a dated, burning reason to buy).
- User is comfortable with outreach-style marketing, which this niche requires.

**Product one-liner:** every driver, vehicle, and company has a checklist of dated requirements; the app stores the proof, watches the dates, and nags the right person before anything lapses.

## MVP Feature Set

1. **Compliance checklists + document vault** — per-driver (49 CFR 391.51 DQ file), per-vehicle, and company-level requirement checklists with red/yellow/green status; upload PDF/photo proof against any item.
2. **Expiration engine + escalating alerts** — daily scan; email digests at 60/30/14/7/0 days; SMS for ≤14-day tiers (per-org toggle).
3. **Driver self-upload links** — tokenized SMS/email link (no driver login); driver photographs new medical card on phone; admin review queue approves + sets expiry. The chase-the-driver killer feature.
4. **Audit-readiness dashboard** — score (% required items current), 30-day lookahead.
5. **One-click audit binder PDF** — per-driver DQ file / full-company binder in regulation order. The marquee sales demo.
6. **Basics** — org settings, admin invite, driver/vehicle CRUD with termination handling (terminated drivers keep records ~3yr retention, drop from alerting/scoring), CSV import.

**Out of scope for MVP:** ELD/HOS, telematics/hardware, IFTA calculation (renewal date tracking only), load/dispatch/payroll, Clearinghouse/DMV/MVR integrations, driver logins, native apps, e-sign, SSO, CSA score monitoring.

## Key Design Decision: Data-Driven Requirement Catalog

Requirements live in a `requirement_catalog` **table, not code**: `applies_to` (driver/vehicle/company), `recurrence_kind` (`one_time` | `document_expiry` | `fixed_interval` | `calendar_rule` with JSON rules for oddballs like MCS-150 biennial/UCR), `default_lead_days`, `regulation_ref`. Global seeded rows (`org_id NULL`) + org-custom rows + per-org enable/override table. New requirement = one INSERT, no deploy. Instances materialize into `compliance_items` per subject; status is derived (nightly + on-write) from `expires_at`, so it's always recomputable.

## Stack (boring, cheap, low-ops — ~$45/mo infra)

- **Next.js 15 (App Router) + TypeScript + Tailwind + shadcn/ui** on **Vercel**
- **Supabase**: Postgres + Auth + private Storage bucket (signed URLs; docs are PII — medical certs/MVRs). Plain SQL migrations + generated types; no ORM.
- **Multi-tenancy**: `org_id` on every table, **RLS everywhere** via security-definer `user_org_ids()` helper; service-role key only in webhooks/cron/token-upload route. Cross-tenant access tests in CI.
- **Stripe** Checkout + Customer Portal + webhooks (idempotency table). Flat tiers: **$49/mo ≤10 drivers, $99 ≤25, $149 ≤50**; 14-day trial.
- **Resend + React Email** (authenticated subdomain day one — deliverability is the product), **Twilio** toll-free for SMS.
- **Vercel Cron** daily job (secured route): recompute statuses → send tiered alerts → `alert_log` with dedupe key `(item, tier, channel, recipient)`; heartbeat self-alert if job doesn't run.
- **PDF binder**: `@react-pdf/renderer` (cover/TOC/checklist pages) + `pdf-lib` (merge stored docs, image→PDF normalization, HEIC converted at ingest). No Puppeteer. Async-to-Storage path for big fleets.

## Core Schema

`orgs`, `memberships`, `drivers`, `vehicles`, `requirement_catalog`, `org_requirement_settings`, `compliance_items` (UNIQUE per org/requirement/subject), `documents` (review_status, expiry), `upload_requests` (SHA-256 token hash, link expiry, single-use), `alert_log`, `stripe_events`.

## Build Milestones (ordered)

| Phase | Delivers | Size |
|---|---|---|
| 0. Scaffold | Next.js+Supabase+CI+Vercel deploy, Sentry | S |
| 1. Auth + tenancy | Signup→org, invites, full RLS + cross-tenant tests | M |
| 2. Subjects + catalog + checklists | CRUD, **seeded catalog (CFR-cited — the product's brain)**, materialized items, status UI | L |
| 3. Document vault | Uploads, expiry entry, signed-URL viewing, dashboard + score | M |
| 4. Expiration engine + alerts | Daily cron, tiered email digests, SMS toggle, dedupe + heartbeat | M |
| 5. Driver upload links | Token flow, mobile camera-capture page, review queue | M |
| 6. Audit binder PDF | Per-driver DQ file first, then full binder; the demo feature | L |
| 7. Stripe billing | Checkout, webhooks, portal, trial gating (read-only after) | M |
| 8. Landing + onboarding | Pain-led marketing page, 10-min guided onboarding, CSV import, mobile QA | M |

**Top risks:** (1) requirement-catalog correctness — cite CFR on every row, get one carrier/compliance consultant to review before launch; (2) PDF merge edge cases — normalize at ingest, placeholder pages over hard failures; (3) alert reliability — dedupe + heartbeat + weekly digest backstop; (4) RLS discipline — tests in CI from Phase 1.

## Launch / Validation

- Build Phases 0–3 while running validation in parallel: landing page + waitlist + 10 carrier conversations. Channel: weekly FMCSA new-authority list, filtered to 5–50 power units, short plain-text outreach — "Your DOT new-entrant audit is coming. Be ready in an afternoon."
- Before investing in Phases 6–8, confirm: trial users actually upload documents (activation) and use driver upload links (differentiator). White-glove data entry is fine and doubles as discovery.
- 90-second screen recording of the one-click DQ-file binder for outreach emails.
- Pre-launch legal hygiene: privacy policy/ToS covering PII + retention, "software, not compliance advice" disclaimer.

## Repo Logistics

Name decided: **FleetDocket** (fleetdocket.com — see BUSINESS.md for branding, costs, hosting, and legal posture). Per session constraints, code is developed on branch `claude/saas-app-ideation-4tysvo` of `GallusDev/rift-launcher` — the app lives in a top-level `fleetdocket/` directory there, with a recommendation to migrate to a dedicated repo before launch. Phase 0 (scaffold, core schema migration, CFR-cited catalog seed, CI) is complete; next is Phase 1 (auth + tenancy).

## Verification

- Phase 1: automated cross-tenant RLS tests (org A cannot read org B's rows/storage) in CI.
- Phase 2+: seed a demo fleet (3 drivers, 2 trucks) and verify checklist statuses match hand-computed dates, including MCS-150/UCR calendar rules.
- Phase 4: run the cron against fixture data with a frozen "today"; assert alert_log rows for each tier exactly once across repeated runs.
- Phase 5: end-to-end driver-link flow on a real phone (camera capture → review → item goes green).
- Phase 6: binder generated from mixed inputs (PDF, JPEG, HEIC, oversized scan) opens correctly and pages are in regulation order.
- Phase 7: Stripe test-mode checkout → webhook → plan gates enforced; trial expiry flips to read-only.
