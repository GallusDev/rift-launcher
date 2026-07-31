# Private-Server SaaS Platform — Full Plan

> Approved plan (July 2026). Market context and sourcing in `MARKET_RESEARCH.md`.
> This is a future standalone product; it lives in this repo's branch only for
> preservation until its own repository is created.

## Vision

Scene-agnostic SaaS chassis for private-server owners, launched RSPS-first:
hosted server site + donation store + vote hub + verified-count directory +
launcher distribution, integrated via a drop-in JVM SDK. Owners self-serve through
self-verifying wizards; the platform runs itself (steady-state founder load ≈
~1 hr/week directory-abuse review). Neutral branding, zero game IP on platform
infrastructure.

## Architecture (4 multi-tenant pieces)

1. **Hosted dashboard + public sites** — Shopify model: `server.<platform>.gg` or
   custom domain; themes, store, hiscores, vote page, launch countdown, news.
2. **JVM SDK** (RSPS) — Kotlin, Java-8-compatible shaded JAR added as a Gradle/Maven
   dependency to any server base (Elvarg, Kronos-family, RSMod, Zenyte-lineage
   allowlist). API-key handshake; events (`player_login/logout`, `vote_claimed`,
   `donation_credited`, custom); command channel (grant item/points); offline queue +
   batching; heartbeat with unique-IP-hash player counts. For OT expansion this
   becomes a VPS agent (outbound-only) applying MySQL/Lua shims.
3. **Payments orchestration** — owners connect THEIR OWN processors (PayPal
   OAuth/IPN, Coinbase Commerce/NOWPayments keys; PIX/P24 for OT later). Platform
   verifies with a test transaction + confirmed in-game credit before a store can go
   live. Platform is never merchant of record for donations.
4. **Public directory** — ranked by telemetry-verified player counts (salted IP
   hashes, not raw IPs), uptime, longevity. "Verified" badge. Free tier = listing +
   badge → acquisition funnel and trust moat (all incumbent toplists are gamed).

## Phased modules

| Phase | Scope |
|---|---|
| **M0 Validation** (1–2 wks) | 10–15 mid-tier RSPS owner interviews (`M0_INTERVIEW_GUIDE.md`); landing page + waitlist |
| **M1 Chassis** | Auth (email+TOTP), org/tenant model, SDK v1, telemetry pipeline → verified counts, dashboard with self-verifying setup wizard |
| **M2 Store/votes/site** | Store builder (packages, donor ranks, mystery boxes), owner-connected processors + test-transaction gate, toplist vote-callback hub, hosted public sites (SSR), Discord bot |
| **M3 Directory** | Public launch of verified directory; anti-abuse (telemetry anomaly detection, VirusTotal on linked clients, rule-based enforcement) |
| **M4 Launcher** | Owner uploads client → per-tenant bucket → hash manifest + delta updates via CDN; signed desktop launcher (white-label or universal). Reuse rift-launcher (see verdict below) |
| **M5 Analytics + mobile** | Retention/cohorts/donor LTV/launch funnel; Android owner companion app (Kotlin/Compose, FCM push: donations, live counts, moderation) — Play-Store-safe |
| **M6 Expansion** | Season/wipe tooling, mailing-list re-engagement; Tibia OT adapter (agent + PIX/Przelewy24 + PT-BR/PL localization), Ragnarok Online adapter |

## Tech stack

- **Monorepo**: pnpm + Turborepo; TypeScript web-side.
- **Web**: Next.js — dashboard + multi-tenant public sites (custom-domain routing, SSR for directory SEO).
- **API**: separate Fastify (Node/TS) service for SDK ingestion + payment webhooks (isolated from web deploys); tRPC or REST+OpenAPI to dashboard.
- **Data**: Postgres (tenant_id scoping, Drizzle ORM); Redis + BullMQ (webhook retries, reward delivery, notifications); telemetry in Postgres first, ClickHouse when volume demands.
- **SDK**: Kotlin → Java 8 bytecode, minimal deps (HttpURLConnection or shaded OkHttp), one shaded JAR on Maven coordinates.
- **Launcher**: extend rift-launcher (verdict below); otherwise Tauri.
- **Mobile**: Kotlin + Jetpack Compose (Android first).
- **Discord**: discord.js bot. **Auth**: Auth.js/Better Auth + TOTP; hashed rotatable API keys.

### rift-launcher reuse verdict (M4)

Evaluated July 2026: rift-launcher is a Java 11 Swing/FlatLaf desktop launcher with
OAuth2/PKCE auth (Supabase), encrypted local account store (DPAPI on Windows), an
HTTP API client, client launch handoff, and unit tests — packaged as a single fat
JAR. This covers most of the M4 launcher skeleton (auth, account management, launch
flow). Work needed for platform use: multi-tenant branding/white-label config,
manifest/delta update engine, code-signing pipeline, and pointing auth at the
platform API instead of Supabase (or keeping Supabase if the platform adopts it).
Verdict: **viable base — extend rather than rewrite.**

## Infrastructure

- **Hosting**: Hetzner + Docker Compose/Coolify initially; Next.js optionally on Vercel.
- **Storage/CDN**: Cloudflare R2 + CDN for client builds/manifests; Cloudflare proxying all web properties (the platform itself will be DDoS'd — scene norm).
- **Platform billing**: Paddle or Lemon Squeezy as merchant of record for our subscriptions (sidesteps processor-risk debates); Stripe direct as fallback.
- **Email**: Resend/Postmark. **Observability**: Sentry + Grafana Cloud/Axiom + status page.
- **CI/CD**: GitHub Actions — web deploys, SDK JAR publish, signed launcher builds, Android APK artifacts.

## Security & IP hygiene (non-negotiable)

- No Jagex/CipSoft assets ever on platform infra; per-owner buckets for client builds; platform serves manifests/deltas only.
- Neutral brand (nothing containing rune/scape/tibia); "game-server tooling" positioning.
- Argon2 hashing, 2FA default-on for owners, scoped API keys, audit log.
- SDK reports salted IP hashes, not raw IPs; GDPR-friendly retention.

## Pricing

| Tier | Price | Includes |
|---|---|---|
| Free | $0 | Directory listing + verified badge + basic telemetry |
| Core | $29/mo | Hosted site + store + vote hub + Discord bot |
| Growth | $79/mo | Launcher CDN + analytics + custom domain + season tooling |
| Scale | $149/mo | White-label launcher, priority support, multi-world |

Monthly only — edition-cycle churn is the market's rhythm; win the relaunches.

## Go-to-market

Launch post on Rune-Server + scene Discords (the "one forum post reaches hundreds of
owners" distribution thesis). Seed 2–3 flagship servers free/white-glove at M2–M3;
their verified badges bootstrap the directory. Expansion entries (OTLand, xTibia,
RateMyServer) happen only after the product is proven, with testimonials.

## Execution order & first code milestone

M0 → M1 → M2 (first revenue) → M3 → M4 → M5 → M6.
First code milestone: monorepo scaffold + auth/tenancy + SDK handshake demonstrated
end-to-end against a local open-source RSPS base (Elvarg) in CI.

## Verification strategy (once building)

- SDK integration test vs local Elvarg base in CI: handshake, event ingestion, command round-trip.
- Payments: PayPal sandbox + crypto testnet end-to-end → in-game credit via SDK stub.
- Load: simulated 500-server / 50k-player telemetry ingestion benchmark.
- E2E: Playwright over wizard flows (signup → SDK connect → store live).
- Launcher: manifest/delta integrity tests + signed-binary verification.
