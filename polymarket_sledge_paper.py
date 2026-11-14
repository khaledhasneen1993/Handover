"""Run Sledge signals against public books with a simulated $50 wallet."""

import argparse
import json
from decimal import Decimal, ROUND_DOWN
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import urlopen

import requests

from polymarket_sledge import (
    HIGH_VOLUME_ONLY,
    HOST,
    MAX_POSITION_USDC,
    MIN_EDGE,
    PolymarketSledge,
    log,
)

STARTING_CASH = Decimal("50")
GAMMA = "https://gamma-api.polymarket.com/markets"


def get_json(url, **params):
    with urlopen(f"{url}?{urlencode(params)}", timeout=12) as response:
        return json.load(response)


def as_list(value):
    return json.loads(value) if isinstance(value, str) else value


class PaperSledge(PolymarketSledge):
    def __init__(self, state_path):
        self.state_path = state_path
        self.books = {}
        self.state = (
            json.loads(state_path.read_text())
            if state_path.exists()
            else {"cash": str(STARTING_CASH), "fills": []}
        )

    def get_markets(self, active_only=True):
        markets = get_json(GAMMA, active="true", closed="false", limit=120)
        selected = []
        for market in markets:
            try:
                tokens = as_list(market.get("tokens") or market.get("clobTokenIds"))
                outcomes = as_list(market.get("outcomes"))
                volume = float(market.get("volume") or 0)
            except (TypeError, ValueError):
                continue
            if not isinstance(tokens, list) or len(tokens) != 2:
                continue
            if outcomes and [str(item).lower() for item in outcomes] != ["yes", "no"]:
                continue
            if HIGH_VOLUME_ONLY and volume <= 8000:
                continue
            selected.append({**market, "tokens": tokens})
        self.books.clear()
        log.info("PAPER scan | markets=%d | cash=%s USDC", len(selected), self.state["cash"])
        return selected

    def get_orderbook(self, token_id):
        if not token_id:
            return {}
        try:
            response = requests.get(f"{HOST}/book", params={"token_id": token_id}, timeout=12)
            response.raise_for_status()
            raw = response.json()
            book = {}
            for side in ("asks", "bids"):
                levels = [(Decimal(row["price"]), Decimal(row["size"])) for row in raw[side]]
                book[side] = sorted(
                    ((price, size) for price, size in levels if 0 < price < 1 and size > 0),
                    key=lambda level: level[0],
                    reverse=side == "bids",
                )
        except (OSError, KeyError, ValueError, ArithmeticError, TypeError) as error:
            log.warning("PAPER book failed %s: %s", token_id, error)
            return {}
        self.books[token_id] = book
        return book

    def place_aggressive(self, signal):
        cash = Decimal(self.state["cash"])
        if cash <= 0:
            return
        multiplier = signal["size_mult"]
        if signal["type"] == "arb":
            size = min(MAX_POSITION_USDC * multiplier / 2, MAX_POSITION_USDC)
            orders = [(signal["yes_token"], size), (signal["no_token"], size)]
        else:
            size = min(MAX_POSITION_USDC * multiplier, MAX_POSITION_USDC * 1.8)
            orders = [(signal["token"], size)]

        # Keep the original allocation ratios and cap total simulated spend.
        scale = min(STARTING_CASH / Decimal(str(MAX_POSITION_USDC)),
                    cash / Decimal(str(sum(size for _, size in orders))))
        fills = []
        for token, size in orders:
            asks = self.books[token]["asks"]
            if not asks:
                continue
            price, _ = asks[0]
            depth = sum(quantity for level_price, quantity in asks if level_price == price)
            budget = (Decimal(str(size)) * scale).quantize(Decimal("0.000001"), rounding=ROUND_DOWN)
            if budget == 0:
                continue
            # Require the displayed best ask to cover the full simulated order.
            if depth * price < budget:
                log.info("PAPER skipped %s | insufficient best-ask depth", token)
                continue
            fills.append({
                "signal": signal["type"],
                "token": token,
                "price": str(price),
                "shares": str(budget / price),
                "cost": str(budget),
            })
        if not fills:
            return

        new_state = {
            "cash": str(cash - sum(Decimal(fill["cost"]) for fill in fills)),
            "fills": [*self.state["fills"], *fills],
        }
        temp_path = self.state_path.with_suffix(".tmp")
        temp_path.write_text(json.dumps(new_state, indent=2) + "\n")
        temp_path.replace(self.state_path)
        self.state = new_state
        for fill in fills:
            log.info(
                "PAPER %s | token=%s | cost=%s USDC | price=%s | cash=%s USDC",
                fill["signal"], fill["token"], fill["cost"], fill["price"], new_state["cash"],
            )

    def scan_once(self):
        for market in self.get_markets():
            signal = self.calc_edge(market)
            if signal and signal["edge"] >= MIN_EDGE:
                self.place_aggressive(signal)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--once", action="store_true", help="Scan one cycle and exit")
    parser.add_argument("--state", type=Path, default=Path("sledge-paper-state.json"))
    args = parser.parse_args()
    log.info("PAPER | simulated wallet | state=%s", args.state)
    bot = PaperSledge(args.state)
    if args.once:
        bot.scan_once()
    else:
        try:
            bot.run()
        except KeyboardInterrupt:
            log.info("PAPER stopped | cash=%s USDC", bot.state["cash"])


if __name__ == "__main__":
    main()
