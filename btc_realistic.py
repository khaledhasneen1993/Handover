"""Public-data BTC paper execution with fixed FOK limits and persistent fills."""

import argparse
import fcntl
import json
import os
import math
import time
from collections import Counter
from decimal import Decimal as D, ROUND_CEILING, ROUND_DOWN
from pathlib import Path
from threading import Thread

import requests

from btc_paper import BtcPaper
from polymarket_sledge_paper import HOST, as_list, log
from sledge_dashboard import DashboardLog, PAGE, ThreadingHTTPServer, make_handler, now

FEE_CHANGE = 1795219140  # 2026-11-20 23:59 UTC, published fee schedule
FEE_UNIT = D("0.00001")


def fee_rate(timestamp):
    return D("0.07") if timestamp < FEE_CHANGE else D("0.05")


def execution(book, shares, limit=None, depth_fraction=D("1")):
    """FOK: return an entire fill only when eligible visible depth covers it."""
    remaining, gross, fees = shares, D(0), D(0)
    levels = []
    for price, size in book.get("asks", []):
        if limit is not None and price > limit:
            break
        quantity = min(remaining, size * depth_fraction)
        if quantity <= 0:
            continue
        fee = (quantity * fee_rate(time.time()) * price * (1 - price)).quantize(
            FEE_UNIT, rounding=ROUND_CEILING
        )
        gross += quantity * price
        fees += fee
        levels.append({"price": str(price), "shares": str(quantity), "fee": str(fee)})
        remaining -= quantity
        if remaining == 0:
            return {"gross": gross, "fees": fees, "cost": gross + fees,
                    "limit": price, "levels": levels}
    return None


class RealisticPaper(BtcPaper):
    def __init__(self, path, args):
        super().__init__(path)
        self.args = args
        self.session = requests.Session()
        self.last_rtt = 0
        self.server_offset = 0
        self.last_sync = 0
        self.taker_delay = 0
        config = {
            "initial": str(args.balance), "trade_cap": str(args.trade_cap),
            "latency_floor": args.latency, "depth_fraction": str(args.depth_fraction),
            "settlement_delay": args.settlement_delay, "model": 1,
        }
        if path.exists() and self.state.get("config") != config:
            raise ValueError("State settings differ. Use the original settings or a new --state file.")
        self.save({**self.state, "cash": self.state["cash"] if path.exists() else str(args.balance),
                   "config": config, "attempts": self.state.get("attempts", []),
                   "trades": self.state.get("trades", [])})
        # Simulated pending submissions are cancelled on restart, never replayed.
        for index, attempt in enumerate(self.state["attempts"]):
            if attempt["status"] == "pending":
                self.update_attempt(index, "interrupted")

    def save(self, state):
        temp = self.state_path.with_suffix(".tmp")
        with temp.open("w") as stream:
            json.dump(state, stream, indent=2)
            stream.write("\n")
            stream.flush()
            os.fsync(stream.fileno())
        temp.replace(self.state_path)
        directory = os.open(self.state_path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
        with self.lock:
            self.state = state

    def request(self, url, **params):
        started = time.monotonic()
        response = self.session.get(url, params=params, timeout=8)
        response.raise_for_status()
        self.last_rtt = time.monotonic() - started
        return response.json()

    def sync_clock(self):
        started = time.time()
        server = float(self.request(f"{HOST}/time"))
        if server > 1e12:
            server /= 1000
        self.server_offset = server - (started + time.time()) / 2
        self.last_sync = time.monotonic()

    def get_orderbook(self, token):
        try:
            if time.monotonic() - self.last_sync > 60:
                self.sync_clock()
            raw = self.request(f"{HOST}/book", token_id=token)
            timestamp = float(raw["timestamp"])
            if timestamp > 1e12:
                timestamp /= 1000
            age = time.time() + self.server_offset - timestamp
            if not math.isfinite(age) or age > 3 or age < -3:
                raise ValueError("Snapshot outside the 3-second freshness window")
            book = {
                "asks": sorted((D(row["price"]), D(row["size"])) for row in raw["asks"]),
                "tick": D(str(raw["tick_size"])),
                "minimum": D(str(raw["min_order_size"])),
                "timestamp": timestamp, "rtt": self.last_rtt,
            }
            if book["tick"] <= 0 or book["minimum"] <= 0:
                raise ValueError("Invalid tick or minimum size")
            if any(not p.is_finite() or not q.is_finite() or not 0 < p < 1 or q < 0
                   for p, q in book["asks"]):
                raise ValueError("Invalid order-book level")
            with self.lock:
                self.books_ok += 1
            return book
        except (requests.RequestException, ValueError, KeyError, TypeError, ArithmeticError) as error:
            with self.lock:
                self.books_failed += 1
            log.warning("PAPER book failed %s: %s", token, error)
            return {}

    def settle(self):
        # Delay release of cash after the window, then require official resolution.
        if any(not t.get("settled") and time.time() >= t["end"] + self.args.settlement_delay
               for t in self.state["trades"]):
            original = self.load_market

            def matured(slug):
                trade = next(t for t in self.state["trades"] if t["slug"] == slug)
                if time.time() < trade["end"] + self.args.settlement_delay:
                    return None
                market = original(slug)
                if market:
                    prices = as_list(market.get("outcomePrices") or [])
                    if sorted(map(D, prices)) != [D(0), D(1)]:
                        return None
                return market

            self.load_market = matured
            try:
                super().settle()
            finally:
                self.load_market = original

    def update_attempt(self, index, status, **extra):
        attempts = list(self.state["attempts"])
        attempts[index] = {**attempts[index], "status": status, **extra}
        self.save({**self.state, "attempts": attempts})

    def simulate_order(self, token, side, shares, quoted, slug, end):
        index = len(self.state["attempts"])
        limit = quoted["limit"]
        self.save({**self.state, "attempts": [*self.state["attempts"], {
            "time": now(), "slug": slug, "side": side, "status": "pending",
            "shares": str(shares), "limit": str(limit),
        }]})
        # REST observation already has latency. Add an estimated outbound leg.
        delay = max(self.args.latency, self.last_rtt / 2) + self.taker_delay
        time.sleep(delay)
        market = self.load_market(slug) if time.time() < end else None
        fresh = self.get_orderbook(token) if market and market.get("acceptingOrders") and not market.get("closed") else {}
        if not fresh or time.time() >= end:
            self.update_attempt(index, "data_or_window_unavailable", delay=delay)
            return None
        if shares < fresh["minimum"] or limit % fresh["tick"] != 0:
            self.update_attempt(index, "tick_or_minimum_changed", delay=delay)
            return None
        fill = execution(fresh, shares, limit, self.args.depth_fraction)
        if fill is None:
            self.update_attempt(index, "fok_rejected", delay=delay)
            return None
        if fill["cost"] > D(self.state["cash"]):
            self.update_attempt(index, "balance_rejected", delay=delay)
            return None
        record = {
            "token": token, "side": side, "shares": str(shares),
            "price": str(fill["gross"] / shares), "cost": str(fill["cost"]),
            "fee": str(fill["fees"]), "time": now(), "levels": fill["levels"],
        }
        trade = next((t for t in self.state["trades"] if t["slug"] == slug), None)
        trade = {**(trade or {"slug": slug, "end": end, "fills": [], "cost": "0"}),
                 "fills": [*(trade["fills"] if trade else []), record],
                 "cost": str(D(trade["cost"] if trade else "0") + fill["cost"])}
        attempts = list(self.state["attempts"])
        attempts[index] = {**attempts[index], "status": "filled", "delay": delay}
        # One durable update includes the fill, cash debit and order status.
        self.save({
            **self.state, "cash": str(D(self.state["cash"]) - fill["cost"]),
            "fills": [*self.state["fills"], record], "attempts": attempts,
            "trades": [t for t in self.state["trades"] if t["slug"] != slug] + [trade],
        })
        return record

    def inspect(self, market, slug, end):
        if any(t["slug"] == slug for t in self.state["trades"]):
            return
        tokens = dict(zip(as_list(market["outcomes"]), as_list(market["clobTokenIds"])))
        if set(tokens) != {"Up", "Down"} or time.time() >= end:
            return
        info = self.request(f"{HOST}/clob-markets/{market['conditionId']}")
        self.taker_delay = 0.25 if info.get("itode") else 0
        up, down = [self.get_orderbook(tokens[side]) for side in ("Up", "Down")]
        if not up or not down:
            return
        budget = min(self.args.trade_cap, D(self.state["cash"]))
        minimum = max(up["minimum"], down["minimum"])
        shares = (minimum * 100).to_integral_value(rounding=ROUND_CEILING) / 100
        quotes = [execution(book, shares, depth_fraction=self.args.depth_fraction) for book in (up, down)]
        if not all(quotes):
            return
        cost = sum(q["cost"] for q in quotes)
        worst_cost = shares * (
            quotes[0]["limit"] + quotes[1]["limit"] + fee_rate(time.time()) / 2
        ) + FEE_UNIT * (len(quotes[0]["levels"]) + len(quotes[1]["levels"]))
        if cost >= shares or worst_cost > budget or worst_cost >= shares:
            return
        # Keep fixed equal size and limits from the decision, never reprice at arrival.
        first = self.simulate_order(tokens["Up"], "Up", shares, quotes[0], slug, end)
        outcome = "رُفض Up قبل الدخول"
        if first:
            second = self.simulate_order(tokens["Down"], "Down", shares, quotes[1], slug, end)
            outcome = "تعبّأ الطرفان" if second else "طرف واحد"
        with self.lock:
            self.signals.appendleft({
                "time": now(), "market": market.get("question") or slug, "type": "arb",
                "edge": float((shares - cost) / shares), "yes": str(quotes[0]["limit"]),
                "no": str(quotes[1]["limit"]), "outcome": outcome,
            })

    def snapshot(self):
        data = super().snapshot()
        with self.lock:
            state = self.state
        data["initial"] = state["config"]["initial"]
        data["attempts"] = state["attempts"][-50:][::-1]
        counts = Counter(a["status"] for a in state["attempts"])
        data["order_rejected"] = sum(v for k, v in counts.items() if k not in ("filled", "pending"))
        data["latency_ms"] = round(self.last_rtt * 1000)
        data["fees"] = str(sum(D(f["fee"]) for f in state["fills"]))
        return data


def dashboard_page(args):
    page = PAGE.read_text().replace("<th>Yes</th><th>No</th>", "<th>Up</th><th>Down</th>")
    page = page.replace("Polymarket Sledge", "BTC Paper Execution")
    page = page.replace('<div class="cards">', '<p class="muted">محاكاة FOK · سيولة محتسبة 80% افتراضيًا · تأخير إرسال تقديري</p><div class="cards">')
    cards = {"pair_pnl": "ربح المراجحة المكتملة", "single_pnl": "ربح أو خسارة طرف واحد",
             "pending": "صفقات بانتظار التسوية", "daily": "صافي اليوم المحقق",
             "order_rejected": "أوامر افتراضية رُفضت", "latency_ms": "زمن استجابة آخر طلب بالمللي ثانية",
             "fees": "رسوم التداول المحتسبة"}
    extra = "".join(f'<div class="card">{label}<strong id="{key}">—</strong></div>' for key, label in cards.items())
    page = page.replace('<div class="cards">', '<div class="cards">' + extra)
    page = page.replace("سيولة محتسبة 80% افتراضيًا",
                        f"سيولة محتسبة {args.depth_fraction * 100}%")
    page = page.replace("</main>", '<section><h2>الأوامر الافتراضية وأسباب الرفض</h2><div class="scroll"><table><thead><tr><th>الوقت</th><th>السوق</th><th>الطرف</th><th>سعر الأمر</th><th>النتيجة</th></tr></thead><tbody id="attempts"></tbody></table></div></section></main>')
    page = page.replace('rows("signals",', 'rows("attempts", s.attempts.map(x => [clock(x.time), x.slug, x.side, x.limit, x.status]), 5, "بانتظار أوامر افتراضية"); rows("signals",')
    js = ";".join(f'el("{key}").textContent = ' + (f'money(s.{key})' if key in ("pair_pnl", "single_pnl", "daily", "fees") else f's.{key}') for key in cards)
    page = page.replace('money(Number(s.initial) - Number(s.cash))', 'money(s.spent);' + js)
    return page.encode()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--balance", type=D, default=D("15"))
    parser.add_argument("--trade-cap", type=D, default=D("5"))
    parser.add_argument("--latency", type=float, default=0.25, help="Minimum outbound delay in seconds")
    parser.add_argument("--depth-fraction", type=D, default=D("0.8"), help="Usable fraction of displayed depth")
    parser.add_argument("--settlement-delay", type=float, default=60)
    parser.add_argument("--state", type=Path, default=Path("btc-paper-realistic-15.json"))
    parser.add_argument("--port", type=int, default=8765)
    args = parser.parse_args()
    if not all(x.is_finite() and x > 0 for x in (args.balance, args.trade_cap, args.depth_fraction)):
        parser.error("Balances and depth fraction must be finite and positive")
    if args.depth_fraction > 1 or not 0 <= args.latency <= 60 or not 0 <= args.settlement_delay <= 3600:
        parser.error("Invalid depth fraction or delay")
    with args.state.with_suffix(".lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        bot = RealisticPaper(args.state, args)
        handler = DashboardLog(bot)
        log.addHandler(handler)
        server = ThreadingHTTPServer(("127.0.0.1", args.port), make_handler(bot, dashboard_page(args)))
        Thread(target=server.serve_forever, daemon=True).start()
        print(f"Dashboard: http://127.0.0.1:{server.server_port}", flush=True)
        try:
            while True:
                try:
                    bot.scan_once()
                except (requests.RequestException, OSError, ValueError, KeyError, TypeError, ArithmeticError) as error:
                    log.error("Scan loop: %s", error)
                time.sleep(4.5)
        except KeyboardInterrupt:
            pass
        finally:
            server.shutdown()
            server.server_close()
            bot.session.close()
            log.removeHandler(handler)


if __name__ == "__main__":
    main()
