# M0 Validation — RSPS Owner Interview Guide

Goal: before writing platform code, validate willingness to pay with 10–15 owners of
mid-tier servers (~150–400 concurrent players). Recruit via Rune-Server DMs, server
Discords, and warm intros. 20–30 minutes each, voice or chat. Offer early access +
lifetime discount as thanks.

## Who to talk to

- 8–10 owners of live mid-tier servers (the paying sweet spot)
- 2–3 "serial relaunchers" (multiple launches — they feel setup pain most)
- 2–3 owners who recently shut down (churn/lifecycle insight, no sales pressure)

Avoid over-indexing on top-5 servers (custom everything, in-house devs) and pure
hobby 317s (no budget).

## Script

### Warm-up (context)
1. Tell me about your server — base, how long running, roughly how many players?
2. Walk me through your current web stack: site, store, vote system, hiscores. What
   did each piece cost to set up, and who maintains it?

### Pain discovery (don't pitch yet)
3. What broke most recently in your store/vote/site setup? How long did it take to fix?
4. How do you handle donations today? What happens when a chargeback hits?
5. How did you get your first 100 players? What did that cost? What's working now?
6. Do you trust the toplists? Would a rank based on provably-real player counts help
   you or hurt you? (listen for spoofing admissions/attitudes)
7. How do players get your client? Ever had malware accusations or fake-domain clones?
8. What do you know about your players that you wish you knew? (retention, whales,
   where they came from)

### Solution probe (now describe the platform, one sentence per module)
9. Hosted site + store that credits in-game automatically via a drop-in JAR — you
   connect your own PayPal/crypto. Useful? What's missing?
10. Verified-players badge + directory ranked by real telemetry. Would you list?
    Would you *want* competitors' counts verified?
11. Signed launcher + auto-update CDN for your client. Worth paying for vs your
    current setup?
12. Owner mobile app: push on donations, live counts, moderation. Care?

### Pricing (last, always)
13. What do you spend monthly today across hosting, DDoS, ads, dev work?
14. Of the modules above, which single one would you pay for first?
15. Straight up: $29/mo for site+store+votes, $79/mo adding launcher CDN + analytics.
    In, out, or depends-on-what?
16. Who else should I talk to? (referral loop)

## What validates M1–M2 (decision criteria)

- ≥8 of 15 say they'd pay $29+ for the store/site wedge, OR
- ≥5 name the same single module as "would pay first" (build that wedge instead)
- Red flags to respect: "I'd only use it if free" majority; "my dev handles it" from
  most mid-tier owners; hostility to verified counts from *honest* owners

## Log results

One row per interview: server, size, current stack, monthly spend, top pain,
would-pay module, price reaction, referrals. Keep verbatim quotes — they become
landing-page copy.
