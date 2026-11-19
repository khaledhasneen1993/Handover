# BTC paper execution

Run inside the existing Debian environment with the repository dependencies installed.

```bash
python btc_realistic.py --balance 15 --trade-cap 5
```

Open http://127.0.0.1:8765 on the same device. The default state file is
`btc-paper-realistic-15.json`. Reusing the command resumes its saved balance and positions.
Export that JSON for a complete record of fills, attempts, fees and settlements.

This mode uses public HTTP GET requests. It requires no signing key or account.

## Execution model

- BTC current 5-minute and 15-minute Up/Down windows.
- Equal shares at the minimum permitted size, rounded up to 0.01 shares.
- Maximum 5 collateral units per pair, including conservative fee reservation.
- Fixed limit price and quantity at decision time.
- Each simulated FOK order requires sufficient depth at or below its original limit
  after the outbound delay, a fresh book, and an accepting market.
- Model uses 80% of displayed depth by default. This is an adjustable competition
  assumption, not a measured queue position.
- Added outbound delay is the greater of 250 ms and half the most recent HTTP round
  trip. Real observation requests add their own elapsed time. This estimates delivery
  latency and does not measure real signed-order latency.
- The market's enabled 250 ms taker delay, when reported, is added separately.
- Book tick and minimum size are checked again at simulated arrival.
- The first leg is saved and its cash deducted immediately. Failure of the second
  leg leaves actual simulated directional exposure until resolution.
- Fees use the published BTC category curve, conservatively rounded upward per price
  level. Coefficient is 0.07 before 2026-11-20 23:59 UTC and 0.05 from then.
- Cash is released after official binary resolution and at least 60 seconds after
  the window ends. This is a simulated automatic redemption assumption.
- State uses atomic replacement and fsync, with a single-process file lock.
  Restart cancels an interrupted simulated submission and retains saved fills.

HTTP snapshots cannot reproduce exchange queues, competing orders between snapshots,
signed-order acceptance, on-chain trade failures, or actual redemption execution.
This model improves the treatment of these uncertainties through fixed limits,
delays and a conservative depth assumption. It cannot certify executable returns.

Official references

- https://docs.polymarket.com/trading/place-orders
- https://docs.polymarket.com/api-reference/markets/get-clob-market-info
- https://docs.polymarket.com/trading/fees
- https://docs.polymarket.com/programs/fee-and-rebate-changes-november-2026
