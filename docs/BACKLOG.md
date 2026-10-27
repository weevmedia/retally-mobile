# ReTally backlog

## Price-drop alerts (price adjustment reminders)

**Status: v1 built Oct 2026** — price source = ReTally receipts across shoppers (same chain, same item number/description), 30-day watch, daily scan `processPriceDrops`, shown as `price_drop` Home nudges. Retailer APIs / per-store policies remain future work.

Remind users when an item they bought gets cheaper while the store's price-adjustment
window is still open: "The TV you bought at Target dropped $40 — 6 days left to claim."

**Design (agreed Oct 2026, not started)**

1. **Identify items** — extend receipt parsing to capture SKU/UPC/store item code and unit
   price. Only watch items with a code, price ≥ ~$25, at stores with a policy. No fuzzy
   name matching.
2. **Policy table** — `store_policies/{storeKey}`: windowDays, memberWindowDays, minDrop,
   exclusions (clearance, marketplace, doorbusters), channels, claim steps, sourceUrl,
   lastVerifiedAt, contentHash. Editable in the admin webapp. Monthly job fetches
   sourceUrl, diffs the hash, Gemini summarises changes into an admin review queue (never
   auto-apply). Policy unverified > 90 days → pause alerts for that store.
3. **Watches** — `users/{uid}/price_watches/{id}`: storeKey, sku, name, paidPrice,
   purchaseDate, deadline, status (watching/dropped/claimed/expired/dismissed). Created on
   receipt sync.
4. **Price checker** — scheduled job (12–24h), deduped by store+SKU across users, one
   adapter per store using official/affiliate APIs or a licensed data provider (no
   scraping). Price history in `prices/{store}_{sku}`. Drop = price ≤ paid − minDrop,
   store's own price, not excluded, before deadline.
5. **Notify** — `price_drop` nudge kind (high priority, "$40 · 6 days left"), push + inbox
   entry at drop, again at 3 days and 1 day left unless dismissed. Card opens receipt with
   claim steps; "I claimed it" records savings → "ReTally saved you $X" stat.
   Copy: "Policy checked <month> — confirm with store."
6. **Rollout** — 1–2 stores with clean policy + price source, high-value categories;
   shadow mode (log drops, no alerts) for a few weeks to measure accuracy first.

Main risk: false positives sending users to the store for nothing.

**Update — "inform, then verify" approach (decided Oct 2026)**
- Alerts are informational: "We noticed the TV you bought at Target is now $40 cheaper.
  Stores often refund the difference within a set time — check with Target before you go."
- Every alert carries a "Verify with the store before you go" line and a tap-to-call /
  store-page link; never promise a refund or state a deadline as fact.
- Policy table becomes optional: when a store has a verified policy, add "Target usually
  allows N days — about X days left"; otherwise show only the price drop.
- Still required: an exact item match (SKU/UPC) and a trustworthy current price — the
  disclaimer covers policy uncertainty, not a wrong price.
- Lets v1 ship with a much smaller admin burden; the policy table can grow store by store.

**v1 scope — fixed 30-day bucket (decided Oct 2026)**
- No per-store policy table in v1: every eligible item is watched for 30 days after
  purchase, then the watch expires.
- Copy never mentions a store-specific deadline — only "bought N days ago" plus
  "check with <store> before you go".
- Per-store windows (shorter/longer than 30 days) come later via the optional policy table.

## Smarter nudges — step 2
Gemini wording/ranking of server nudges + feedback (dismiss/tap) to tune ranking.

## Budget — step 3
Cloud sync of monthly budgets; budget-pace nudge and month-end recap.
