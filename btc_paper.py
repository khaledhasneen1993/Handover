"""BTC 5m/15m paired-order paper simulation."""

import argparse
import json
import time
from datetime import datetime, timezone
from decimal import Decimal as D, ROUND_CEILING, ROUND_DOWN
from pathlib import Path
from threading import Thread
from zoneinfo import ZoneInfo

from polymarket_sledge_paper import GAMMA, as_list, get_json, log
from sledge_dashboard import DashboardBot, DashboardLog, PAGE, ThreadingHTTPServer, make_handler, now

TRADE_CAP = D("5")
FEE_RATE = D("0.07")
CENT = D("0.01")
FEE_UNIT = D("0.00001")
AMMAN = ZoneInfo("Asia/Amman")


def quote(book, shares):
    """Walk available asks and return cost including estimated taker fees."""
    remaining = shares
    cost = D(0)
    for price, depth in book.get("asks", []):
        quantity = min(depth, remaining)
        fee = (quantity * FEE_RATE * price * (1 - price)).quantize(
            FEE_UNIT, rounding=ROUND_CEILING
        )
        cost += quantity * price + fee
        remaining -= quantity
        if remaining == 0:
            return cost
    return None


def pair_size(up, down, budget, minimum):
    """Find the largest profitable size within the simulated budget."""
    if not up.get("asks") or not down.get("asks"):
        return None
    first_pair_price = up["asks"][0][0] + down["asks"][0][0]
    low = int((minimum / CENT).to_integral_value(rounding=ROUND_CEILING))
    high = int((budget / first_pair_price / CENT).to_integral_value(rounding=ROUND_DOWN))
    best = None
    while low <= high:
        middle = (low + high) // 2
        shares = middle * CENT
        costs = [quote(book, shares) for book in (up, down)]
        if None not in costs and sum(costs) <= budget and sum(costs) < shares:
            best = shares, costs
            low = middle + 1
        else:
            high = middle - 1
    return best


class BtcPaper(DashboardBot):
    def load_market(self, slug):
        markets = get_json(GAMMA, slug=slug, closed="any")
        return next((market for market in markets if market.get("slug") == slug), None)

    def save(self, state):
        temp = self.state_path.with_suffix(".tmp")
        temp.write_text(json.dumps(state, indent=2) + "\n")
        temp.replace(self.state_path)
        with self.lock:
            self.state = state

    def settle(self):
        for trade in self.state.get("trades", []):
            if trade.get("settled") or time.time() < trade["end"]:
                continue
            market = self.load_market(trade["slug"])
            if not market or not market.get("closed") or market.get("umaResolutionStatus") != "resolved":
                continue
            prices = dict(zip(as_list(market["outcomes"]), map(D, as_list(market["outcomePrices"]))))
            if set(prices) != {"Up", "Down"} or min(prices.values()) < 0 or sum(prices.values()) != 1:
                continue
            amount = sum(D(fill["shares"]) * prices[fill["side"]] for fill in trade["fills"])
            closed = {**trade, "settled": now(), "pnl": str(amount - D(trade["cost"]))}
            self.save({
                **self.state,
                "cash": str(D(self.state["cash"]) + amount),
                "trades": [closed if item["slug"] == trade["slug"] else item for item in self.state["trades"]],
            })

    def scan_once(self):
        with self.lock:
            self.started_scan = now()
            self.selected = 2
            self.checked = self.books_ok = self.books_failed = 0
            self.status = "جاري مسح BTC"
        self.settle()
        timestamp = int(time.time())
        for seconds in (300, 900):
            start = timestamp // seconds * seconds
            minutes = seconds // 60
            slug = f"btc-updown-{minutes}m-{start}"
            market = self.load_market(slug)
            if market and market.get("acceptingOrders") and not market.get("closed"):
                self.inspect(market, slug, start + seconds)
            with self.lock:
                self.checked += 1
        with self.lock:
            self.status = "اكتمل المسح" if not self.books_failed else "اكتمل المسح مع أخطاء"

    def inspect(self, market, slug, end):
        tokens = dict(zip(as_list(market["outcomes"]), as_list(market["clobTokenIds"])))
        if set(tokens) != {"Up", "Down"} or time.time() >= end:
            return
        up, down = (self.get_orderbook(tokens[side]) for side in ("Up", "Down"))
        budget = min(D(self.state["cash"]), TRADE_CAP)
        minimum = D(str(market.get("orderMinSize") or 5))
        pair = pair_size(up, down, budget, minimum)
        if not pair or time.time() >= end or any(t["slug"] == slug for t in self.state.get("trades", [])):
            return
        shares, (up_cost, down_cost) = pair
        # Simulate sequential fills. Down must still fit its quoted cost on a fresh book.
        fills = [self.fill(tokens["Up"], "Up", shares, up_cost)]
        fresh_down = self.get_orderbook(tokens["Down"])
        actual_down = quote(fresh_down, shares)
        if actual_down is not None and actual_down <= down_cost and time.time() < end:
            fills.append(self.fill(tokens["Down"], "Down", shares, actual_down))
        cost = sum(D(fill["cost"]) for fill in fills)
        trade = {"slug": slug, "end": end, "cost": str(cost), "fills": fills}
        self.save({
            "cash": str(D(self.state["cash"]) - cost),
            "fills": [*self.state["fills"], *fills],
            "trades": [*self.state.get("trades", []), trade],
        })
        with self.lock:
            self.signals.appendleft({
                "time": now(), "market": market.get("question") or slug,
                "type": "arb", "edge": float((shares - up_cost - down_cost) / shares),
                "yes": str(up["asks"][0][0]), "no": str(down["asks"][0][0]),
                "outcome": "تعبّأ الطرفان" if len(fills) == 2 else "طرف واحد",
            })

    @staticmethod
    def fill(token, side, shares, cost):
        return {"token": token, "side": side, "shares": str(shares),
                "cost": str(cost), "price": str((cost / shares).quantize(D("0.000001")))}

    def snapshot(self):
        data = super().snapshot()
        trades = self.state.get("trades", [])
        today = datetime.now(AMMAN).date()
        data["daily"] = str(sum(
            D(trade["pnl"]) for trade in trades if trade.get("settled")
            and datetime.fromisoformat(trade["settled"]).astimezone(AMMAN).date() == today
        ))
        data["pending"] = sum(not trade.get("settled") for trade in trades)
        data["spent"] = str(sum(D(t["cost"]) for t in trades if not t.get("settled")))
        return data


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--state", type=Path, default=Path("btc-paper-state.json"))
    args = parser.parse_args()
    bot = BtcPaper(args.state)
    handler = DashboardLog(bot)
    log.addHandler(handler)
    page = PAGE.read_text().replace("<th>Yes</th><th>No</th>", "<th>Up</th><th>Down</th>")
    page = page.replace('id="fill_count">—</strong></div>',
                        'id="fill_count">—</strong></div>'
                        '<div class="card">صافي اليوم المحقق<strong id="daily">—</strong></div>'
                        '<div class="card">بانتظار التسوية<strong id="pending">—</strong></div>')
    page = page.replace('money(Number(s.initial) - Number(s.cash))',
                        'money(s.spent); el("daily").textContent = money(s.daily); '
                        'el("pending").textContent = s.pending')
    server = ThreadingHTTPServer(("127.0.0.1", args.port), make_handler(bot, page.encode()))
    Thread(target=server.serve_forever, daemon=True).start()
    print(f"Dashboard: http://127.0.0.1:{server.server_port}", flush=True)
    try:
        while True:
            try:
                bot.scan_once()
            except (OSError, ValueError, KeyError, TypeError, ArithmeticError) as error:
                log.error("Scan loop: %s", error)
            time.sleep(4.5)
    except KeyboardInterrupt:
        pass
    finally:
        server.shutdown()
        server.server_close()
        log.removeHandler(handler)


if __name__ == "__main__":
    main()
