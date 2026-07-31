# Private-Server SaaS — Market Research (July 2026)

> Research supporting the platform plan in `PLATFORM_PLAN.md`. Compiled from five deep
> web-research passes across RSPS, Tibia OT, WoW, and other private-server ecosystems.
> This document is intentionally kept in a separate branch/folder — it is planning
> material for a future standalone product, not part of the rift-launcher application.

## 1. RSPS (RuneScape private servers) — the launch market

**Size & money.** Small but real-money scene. Top servers: Ferox ~1,000+ concurrent,
Ikov ~600–900, Alora ~500–600, Roat Pkz ~400–550; total scene concurrency plausibly
5–15k. Servers with 300+ concurrent commonly gross $5k+/month from donation stores;
historical ceiling was SoulSplit (~$1M/yr). Realistically 200–400 revenue-generating
servers, a few dozen grossing meaningfully. Dev infrastructure is modernizing
(Kotlin: RSMod, RSProt/RSProx, OpenRS2 cache archive; RuneLite-fork clients + mobile
are table stakes) while total players slowly decline. Community life moved from
Rune-Server forums to Discord.

**Legal.** Jagex enforcement escalated 2024–2025: UDRP domain seizures (RuneWild
closed; "-scape" names ruled confusingly similar), DMCA campaign via contractor Web
Capio that wiped out RSPS YouTube channels, Discord takedowns. Jagex's sanctioned
alternative (Project Zanaris) paused indefinitely June 2025. Enforcement is erratic,
not total.

**Existing platforms.**

| Layer | Incumbents | State |
|---|---|---|
| Toplists | RSPS-List (sells rank $10/mo), RuLocus, rsps.org, RSPSToplist, RuneList, rsps.dev | Corrupt/gamed: paid placement normalized, industrial vote bots, owners bot their own counts |
| Vote/store/hiscores APIs | EverythingRS (free JAR drop-in, de-facto incumbent since ~2017) | Aging, outage-prone, unmonetized, single-maintainer. No modern paid successor |
| Payments | PayPal (+ chargeback misery), crypto fallback. Tebex/CraftingStore/Stripe unavailable (AUP/ToS prohibit infringing virtual goods) | No merchant-of-record exists; the slot is empty because underwriting infringing goods is a banking dead end |
| Hosting/DDoS | PSProtect (RSPS-specific), OVH default | No TCPShield-for-RSPS (most wished-for infra) |
| Website/CMS | Forum-traded PHP templates; MyRSPS (distrusted operator) | No polished website-in-a-box |
| Launchers | Per-server RuneLite forks; RuneList Launcher, RoeLite, RLaunch | None won; unsigned JARs the norm; documented malware-clone sites |
| Analytics | Nothing | Total gap, unproven standalone willingness-to-pay |

**Top owner pain points (frequency × money).** 1) Payments/chargebacks;
2) DDoS; 3) player acquisition (CAC ~$10–20/retained player; YouTube channel killed
by DMCA wave; toplists distrusted); 4) hiring/buying trust (no escrow, documented
scams); 5) client distribution safety; 6) turnkey ops (~800k leaked RSPS passwords
circulating); 7) analytics (absent).

## 2. Cross-ecosystem comparison

| Scene | Size & money | Legal | Owner reachability | Tooling | Verdict |
|---|---|---|---|---|---|
| FiveM/RedM | 20k+ servers, 250k CCU, Rockstar-owned | Fully legal | High (forum.cfx.re) | High (Tebex exclusive 15%) | Crowded; payments walled off |
| **Tibia OT** | 600+ live servers, ~26k CCU | Good — CipSoft *licenses* OTs (30% >€500/mo) | Excellent (OTLand canonical) | Low (volunteer PHP AACs) | **Best single-scene fit** |
| Metin2/MU/Silkroad/KO | Large launch-spike economies; MU owners buy commercial emulators (IGCN) | Moderate (passive publishers) | Good (ElitePvPers/RaGEZONE) | Low-medium | Strong #2 |
| Lineage 2 | L2 Reborn 7–11.5k CCU; growing | Moderate | Good (toplists) | Low | Strong |
| WoW | Biggest audience (~1M players) | Poor/worsening: Blizzard killed Turtle WoW (May 2026) & Project Epoch (2025) | Medium (fragmented) | Low | Monetization tooling = selling to defendants |
| Ragnarok Online | Medium; SEA/BR | Moderate | Excellent (RateMyServer) | Low (FluxCP frozen; Gepard anti-cheat dev unreachable) | Solid second expansion |
| MapleStory | Medium | Poor (Nexon sues; subpoenas Discord) | Good | Low | Marginal |
| UO / EQ / CoH / SWG | Small | Good-to-licensed, but EQ contracts mandate non-commercial | Good | Low | Low spend capacity |
| Disney/Nintendo scenes | Small | Hunted | High | None | Avoid |
| Minecraft/Rust | Enormous | Full | High | Saturated (Tebex, Pterodactyl) | Reference market only |

**The structural lesson (25 years of crackdowns):** publishers sue server
*operators* — Turtle WoW, Stormforge, The Heroes' Journey ($100k/mo revenue revealed
in court; $3.5M hanging damages) — but have targeted tooling vendors exactly once
ever (bnetd, 2002, an emulator itself). Hosts, toplists, CMS authors, and payment
intermediaries (Paymentwall→Warmane, GamePoints→Turtle WoW) have never been touched.
**IP-neutral picks-and-shovels is structurally safe; the merchant-of-record layer is
the landmine.**

**Multi-scene viability by layer:** discovery, DDoS proxying, launcher/auto-update
infrastructure, and escrow generalize across scenes (no game IP). Panels/CMS/webstores
are per-scene (the integrations are the product).

## 3. Strategic conclusion

Build a scene-agnostic chassis (SDK + dashboard + payments orchestration +
verified-count directory), launch **RSPS-first** for founder–market fit (credibility
and fluency in a scam-scarred, trust-poor scene outweigh OT's larger server count),
then expand to Tibia OT and Ragnarok Online with a proven product.

Design constraints regardless of scene: neutral branding (no "Rune"/"-scape"/game
names); never host game assets (caches/clients pooled) on platform infrastructure;
never be merchant of record for donations (owners connect their own processors);
monthly pricing tuned to edition-cycle churn; free tier as the telemetry funnel.

## Confidence notes

Scene sizes, CAC (~$10–20/player), and revenue benchmarks are single-source
community numbers. Several community sites block crawling (Cloudflare), so some data
comes from search snippets. Toplist "news" sites are competing commercial actors —
their coverage of rivals should be read adversarially. The only audited revenue
number in any scene is THJ's court-revealed $100k/mo. High-confidence items: legal
events (court dockets, mainstream outlets), GitHub-verified framework activity,
published pricing pages.
