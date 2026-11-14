"""Run paper trading and a local dashboard in one process."""

import argparse
import json
import logging
from collections import deque
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from threading import Lock, Thread

from polymarket_sledge_paper import STARTING_CASH, PaperSledge, log

PAGE = Path(__file__).with_name("sledge_dashboard.html")


def now():
    return datetime.now(timezone.utc).isoformat(timespec="seconds")


class DashboardBot(PaperSledge):
    def __init__(self, state_path):
        super().__init__(state_path)
        self.lock = Lock()
        self.activity = deque(maxlen=60)
        self.signals = deque(maxlen=60)
        self.status = "بانتظار أول مسح"
        self.started_scan = None
        self.selected = self.checked = self.books_ok = self.books_failed = 0
        self.market = {}

    def get_markets(self, active_only=True):
        with self.lock:
            self.status = "جاري تحميل الأسواق"
            self.started_scan = now()
            self.selected = self.checked = self.books_ok = self.books_failed = 0
        markets = super().get_markets(active_only)
        with self.lock:
            self.selected = len(markets)
            self.status = "جاري المسح" if markets else "لا توجد أسواق تطابق الفلتر"
        return markets

    def get_orderbook(self, token_id):
        book = super().get_orderbook(token_id)
        with self.lock:
            if book:
                self.books_ok += 1
            else:
                self.books_failed += 1
        return book

    def calc_edge(self, market):
        self.market = market
        self.books.clear()
        try:
            return super().calc_edge(market)
        finally:
            with self.lock:
                self.checked += 1
                if self.checked == self.selected:
                    self.status = "اكتمل المسح" if not self.books_failed else "اكتمل المسح مع أخطاء"

    def place_aggressive(self, signal):
        tokens = self.market["tokens"]
        ids = [token if isinstance(token, str) else token.get("token_id") for token in tokens]
        prices = [
            str(self.books[token]["asks"][0][0]) if self.books.get(token, {}).get("asks") else None
            for token in ids
        ]
        before = len(self.state["fills"])
        outcome = "خطأ أثناء المحاكاة"
        try:
            super().place_aggressive(signal)
            count = len(self.state["fills"]) - before
            outcome = {0: "بدون تعبئة", 1: "طرف واحد", 2: "تعبّأ الطرفان"}[count]
            if signal["type"] != "arb" and count == 1:
                outcome = "تعبئة تجريبية"
        finally:
            with self.lock:
                self.signals.appendleft({
                    "time": now(), "market": self.market.get("question") or self.market.get("slug") or "—",
                    "type": signal["type"], "edge": signal["edge"], "yes": prices[0],
                    "no": prices[1], "outcome": outcome,
                    "fills": self.state["fills"][before:],
                })

    def snapshot(self):
        with self.lock:
            state = self.state
            return {
                "initial": str(STARTING_CASH), "cash": state["cash"],
                "fills": state["fills"][-50:][::-1], "fill_count": len(state["fills"]),
                "status": self.status, "started_scan": self.started_scan,
                "selected": self.selected, "checked": self.checked,
                "books_ok": self.books_ok, "books_failed": self.books_failed,
                "signals": list(self.signals), "activity": list(self.activity),
            }


class DashboardLog(logging.Handler):
    def __init__(self, bot):
        super().__init__(logging.WARNING)
        self.bot = bot

    def emit(self, record):
        message = record.getMessage()
        with self.bot.lock:
            self.bot.activity.appendleft({"time": now(), "message": message})
            if message.startswith("Scan loop:"):
                self.bot.status = "تعذّر إكمال دورة المسح"


def make_handler(bot, page):
    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path == "/":
                body, mime = page, "text/html"
            elif self.path == "/api/status":
                body = json.dumps(bot.snapshot(), ensure_ascii=False).encode()
                mime = "application/json"
            else:
                self.send_error(404)
                return
            self.send_response(200)
            self.send_header("Content-Type", f"{mime}; charset=utf-8")
            self.send_header("Cache-Control", "no-store")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, format, *args):
            pass

    return Handler


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--port", type=int, default=8765)
    parser.add_argument("--state", type=Path, default=Path("sledge-paper-state.json"))
    args = parser.parse_args()
    bot = DashboardBot(args.state)
    handler = DashboardLog(bot)
    server = ThreadingHTTPServer(("127.0.0.1", args.port), make_handler(bot, PAGE.read_bytes()))
    log.addHandler(handler)
    thread = Thread(target=server.serve_forever, daemon=True)
    thread.start()
    print(f"Dashboard: http://127.0.0.1:{server.server_port}", flush=True)
    try:
        bot.run()
    except KeyboardInterrupt:
        log.info("PAPER stopped | cash=%s USDC", bot.state["cash"])
    finally:
        server.shutdown()
        server.server_close()
        log.removeHandler(handler)


if __name__ == "__main__":
    main()
