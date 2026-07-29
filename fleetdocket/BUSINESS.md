# FleetDocket — Business Plan

Decisions made 2026-07 alongside PLAN.md (product/technical plan). This doc
covers the operating stack, costs, hosting, branding, and legal/compliance
posture for the business itself.

## Identity

- **Name:** FleetDocket ("docket" = the organized, dated case file — which is
  the product). Chosen over FleetBinder (fleetbinder.com taken; confusion +
  trademark risk), CarrierBinder, and RoadBinder.
- **Domain:** fleetdocket.com — verified available at $11.25/yr (2026-07-29).
  **Action: purchase before someone else does.** Buying via Vercel simplifies
  DNS; any registrar works.
- Trademark registration deferred until revenue (~$350 USPTO, DIY).

## Operating stack & vendors

| Layer | Service | Cost (dev) | Cost (launched) |
|---|---|---|---|
| Hosting/CDN/cron | Vercel | Free | $20/mo Pro (required for commercial use) |
| Postgres + Auth + Storage | Supabase | Free | $25/mo Pro (required: daily backups, no pausing) |
| Payments | Stripe | Free | 2.9% + 30¢ per transaction |
| Email (alerts) | Resend | Free (3k/mo) | $0 → $20/mo past ~100 emails/day |
| SMS | Twilio toll-free | Trial | ~$2/mo + ~$0.008/text |
| Errors | Sentry | Free | Free tier |
| Support email | Zoho Mail / Google Workspace | Free | $0–7/mo |
| Domain | fleetdocket.com | $11.25/yr | $11.25/yr |

Deliberately absent: MVR data providers (FCRA wall — see Legal), drug-testing
consortium APIs, telematics hardware, paid ads.

## Cost model

- **Build phase:** ~$12 total (domain only; everything else on free tiers).
- **Launched:** ~$55–75/mo. First $49/mo customer nearly covers all infra.
- **Headroom:** Pro tiers carry ~150–200 customer fleets before the first
  meaningful overage (storage: ~0.3–0.5GB documents/fleet vs 100GB included;
  overage $0.021/GB). ~200 fleets ≈ $17k MRR on ~$150/mo infra. The first
  real scaling constraint is founder time (support/onboarding) around 50–100
  customers, not infrastructure.
- **Low-overhead rules:** upgrade a tier only when a rule forces it; no SOC 2
  (publish a plain-English security page instead); defer trademark, Stripe
  Tax, and paid marketing until revenue justifies each.

## Hosting decision

Vercel + Supabase, US region, both managed. A self-hosted VPS would save
~$35/mo but costs founder time and carries unacceptable blast radius for
customers' federal compliance records. Addition: weekly automated pg_dump to
separate storage (Backblaze B2, ~$1/mo) so no single vendor holds the only
copy of customer data.

## Legal & compliance posture (ours)

*Not legal advice; validate with a small-business attorney (~1 hr) before
taking real customer data.*

- **PII, not HIPAA.** DOT medical certificates held by an employer are
  employment records; we are not a HIPAA covered entity or business
  associate. Still sensitive PII: encryption at rest/in transit, private
  storage + signed URLs, least-privilege access.
- **Breach notification:** all 50 states have laws with no small-business
  exemption. Maintain a simple breach-response plan from day one.
- **FCRA wall (design constraint):** MVRs are consumer reports. FleetDocket
  is a storage locker for documents the carrier obtained — we never procure
  or resell MVRs, or we'd become a CRA/reseller with heavy obligations.
  MVR integrations are out of scope for legal reasons, not just product ones.
- **TCPA/SMS:** verified toll-free number; Twilio auto-handles STOP opt-outs;
  ToS requires the carrier to warrant driver consent for texted upload links.
- **CAN-SPAM (outreach):** cold B2B email to the FMCSA new-authority list is
  legal with accurate headers, a physical address, and honored unsubscribes.
- **ToS is the most important legal doc:** record-keeping tool, NOT
  compliance advice; no guarantee of audit outcomes; liability capped at fees
  paid. Template + one-time attorney review (~$300–800).

## End-user regulatory scope (the product)

Carriers' obligations — encoded in `supabase/seed/requirement_catalog.sql`
with CFR citations: 49 CFR 391 (DQ files), 382 (drug & alcohol +
Clearinghouse), 383 (CDL), 387 (insurance), 390.19 (MCS-150), 396
(inspections); UCR/IFTA state programs. Retention rules become features:
DQ files 3 years post-termination, inspections 14 months, drug/alcohol
records 1–5 years.

## Founder licensing & insurance

- **No license required** to sell this software. We deliberately are not a
  CRA, a testing consortium/TPA, an insurance producer, or a law firm.
- **Entity:** single-member LLC in home state before first paying customer
  (~$50–500 + free EIN + separate bank account). Skip Delaware.
- **Insurance:** Tech E&O + Cyber bundle, ~$500–1,500/yr (Vouch, Hiscox,
  Coalition). Get before storing real driver data — the one overhead cost
  not to defer.
- **Sales tax:** ~20 states tax SaaS but economic-nexus thresholds
  (~$100k/state) are far off; enable Stripe Tax when revenue justifies it.

## Pre-launch business checklist

1. [ ] Buy fleetdocket.com
2. [ ] Form LLC + EIN + business bank account
3. [ ] Stripe account under the LLC
4. [ ] Tech E&O + cyber policy bound
5. [ ] ToS + privacy policy (template + attorney review)
6. [ ] Toll-free number purchased + verification submitted (lead time ~1 wk)
7. [ ] Email subdomain (mail.fleetdocket.com) with SPF/DKIM via Resend
8. [ ] Breach-response one-pager written
9. [ ] Security page on the marketing site
10. [ ] Catalog reviewed by one carrier or compliance consultant
