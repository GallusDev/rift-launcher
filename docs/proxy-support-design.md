# Proxy support — design

**Date:** 2026-08-05
**Status:** proposed, awaiting approval
**Scope:** launcher (proxy store, UI, testing, launch wiring) + a small client-side startup hook

---

## Goal

Per-account SOCKS5 proxies, good enough that proxy handling is a reason to pick Rift.

Two clients set the bar. **Vortex**: a Proxies tab with a table (Nickname / Address / Auth), a single
"Connection String (`host:port` or `host:port:user:pass`)" field, and Add/Edit/Delete/Test.
**Detuks**: a dropdown on the main screen plus a Proxy Editor with separate Name/IP/Port/User/Pass
fields and Test All.

Both share the same three ceilings, and those are where we win:

| Their ceiling | What we do instead |
|---|---|
| One proxy at a time | **Bulk paste** — drop in 100 lines from a provider, parse them all |
| One input format | **Accept every format** people actually copy, including `scheme://user:pass@host:port` |
| Test = "did it connect?" | **Test = latency + the exit IP Jagex will actually see** |

Plus one they can't see: **proxy passwords encrypted at rest** and never on a command line.

---

## Why this is cheap for us

The launcher already spawns **one JVM per account**, so per-account proxying needs no clever routing —
each client process just gets different network settings. And we already have a **private stdin
channel** to that process (it carries the Supabase session), so credentials never need to touch the
command line.

Nothing here requires a new dependency: `java.net.Proxy`, the `socksProxy*` system properties and
`java.net.Authenticator` are all JDK built-ins.

---

## Components

### 1. `ProxyEntry` — one saved proxy

`id`, `nickname`, `host`, `port`, `username`, `password`, plus last-test results (`lastTestedAt`,
`lastLatencyMs`, `lastExitIp`, `lastStatus`).

### 2. `ProxyParser` — accept anything a provider hands you *(pure, heavily tested)*

The single highest-value piece. Providers emit wildly different formats and users paste them verbatim:

```
1.2.3.4:1080                        host:port
1.2.3.4:1080:user:pass              host:port:user:pass   (Vortex's format)
user:pass@1.2.3.4:1080              curl-style
socks5://user:pass@1.2.3.4:1080     scheme URI
socks5h://…  socks://…  http://…    other schemes
[2001:db8::1]:1080                  IPv6
  1.2.3.4 : 1080                    stray whitespace
```

Rules: trim; strip a `scheme://` prefix and remember it; if there's an `@`, everything before it is
credentials; otherwise split on `:` — 2 parts is host/port, 4 is host/port/user/pass. Bracketed IPv6
is handled before any colon splitting. **A password containing `:` is why `user:pass@host:port` must
be tried before the 4-part split** — that ordering is a real bug source, so it gets an explicit test.

Unparseable lines are **reported with their line number and content**, never silently dropped.

### 3. `ProxyStore` — encrypted persistence

`~/.rift/proxies.dat`, DPAPI-encrypted through the existing `Crypto` seam — same treatment as
`accounts.dat` and `devlicense.dat`. Proxy credentials are credentials. Both reference clients appear
to keep them in plain config; we won't.

### 4. `ProxyTester` — answer the question that matters

A binary "works/doesn't" is not useful. Each test reports:

- **Reachable** — SOCKS5 handshake completes and auth is accepted (auth failure is reported
  distinctly from unreachable — the fix is completely different)
- **Latency** — measured by connecting *through* the proxy to a real OSRS world on **port 43594**, not
  to a web endpoint. That is the connection the game actually makes, and some proxies allow 443 but
  block 43594. Testing the real port is the whole point.
- **Exit IP** — the address Jagex will see, via an IP-echo request through the proxy. This is the
  single most useful field and neither reference client shows it. It catches the case that silently
  ruins everything: a proxy that connects fine but exits from an IP you didn't expect.

**Test All** runs concurrently with a small thread pool, so 50 proxies take seconds.

### 5. UI — a **Proxies** tab

Table: **Nickname · Address · Auth · Status · Latency · Exit IP · Last tested**, with status coloured
(green/amber/red) so a bad proxy is visible without reading.

Buttons: **Add**, **Bulk add**, Edit, Delete, **Test**, **Test all**.

- **Add** — one field, "paste anything", with a live preview of what was parsed *before* saving.
  Vortex's single-field approach is right; we just parse more formats and show our interpretation.
- **Bulk add** — a textarea for a whole provider list. Shows *"parsed 48, 2 unreadable (lines 12, 31)"*
  and auto-names entries (`proxy-1`…) so a 50-proxy import is one paste, not 50 dialogs. **This is the
  feature neither competitor has.**

On the **Home** tab, accounts gain a **Proxy** column with a dropdown, plus **assign to all selected
accounts** so setting up 20 accounts isn't 20 interactions.

### 6. Launch wiring — credentials never hit the command line

`LaunchHandoff` gains an optional `proxy` object (host, port, username, password). The client reads it
from the **existing stdin handoff** and, early in startup before any socket exists, sets
`socksProxyHost`/`socksProxyPort`/`socksProxyVersion=5` and installs a `java.net.Authenticator`
supplying the credentials.

This is deliberately *not* done with `-Djava.net.socksPassword` on the command line, which would be
readable in any process listing — the same leak we already avoid for Jagex credentials. Host and port
would be safe there; the password is not, so the whole thing goes through the private channel.

**Client-side change is one small class plus a call in `RuneLite.main`.** No plugin or game-code
changes: the game client uses `java.net.Socket`, which honours these properties.

---

## Decisions to confirm

1. **Scope: all traffic, or game traffic only?** `socksProxyHost` is process-wide, so it also covers
   `runelite.net` and the Rift API. **Recommend: all of it.** If the aim is that this client looks
   like it comes from one IP, having the game on the proxy and everything else on the home connection
   is a split that defeats the purpose. `socksNonProxyHosts` can carve out exceptions later.
2. **Exit-IP lookup needs an external service** (e.g. an IP-echo endpoint) — a third-party call the
   launcher makes on demand, only when testing. **Recommend: include it**, since it is the most useful
   result; it can be omitted if you'd rather not depend on an outside host.
3. **Local only** — proxies live on the machine, not synced to the Rift account. Simpler, and avoids
   putting customer proxy credentials on our server. Revisit only if users ask.

## Deliberately out of scope

Proxy *rotation*, per-proxy account limits, and HTTP(S) proxies (SOCKS5 covers the use case; adding
HTTP means a second code path for no gain).

## Honest notes

- **Datacenter proxy IPs are widely flagged**; residential is the norm here. Showing the exit IP at
  least makes what you're using visible rather than a guess.
- **Java resolves DNS locally** on some paths, so a hostname can leak even when traffic is proxied.
  Recommend documenting "use an IP, or accept the DNS lookup happens locally" rather than implying
  the tunnel is total.
- A proxy that passes the test can still fail later — proxies die constantly. Status is a snapshot,
  which is why it is timestamped rather than shown as a permanent property.

## Testing

`ProxyParser` gets the heaviest coverage — every format above, the `:`-in-password ordering trap,
IPv6, whitespace, and garbage input reported rather than dropped. `ProxyStore` gets the same
round-trip/encrypted-at-rest/corrupt-file tests as `DevLicenseStore`. `ProxyTester` is exercised
against a stub SOCKS server for reachable / auth-failed / unreachable. The UI and a real proxy need
manual verification.
