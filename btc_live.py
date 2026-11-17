"""BTC 5m/15m live orders using the original paper signal."""

import argparse
import fcntl
import json
import os
import time
from pathlib import Path

import requests

from btc_paper import BtcPaper, D, FEE_RATE, TRADE_CAP, pair_size, quote
from polymarket_sledge_paper import HOST, as_list, log


def price_limit(book, shares):
    for price, depth in book.get("asks", []):
        shares -= depth
        if shares <= 0:
            return price
    return None


class LiveBtc(BtcPaper):
    def __init__(self, path, client, sdk, budget):
        super().__init__(path)
        self.client = client
        self.sdk = sdk
        if not path.exists():
            self.save({"cash": str(budget), "fills": [], "orders": {}})
        if "orders" not in self.state:
            raise SystemExit("Use a dedicated live state file.")
        if any(order["phase"] != "paired" for order in self.state["orders"].values()):
            raise SystemExit("Unreconciled live order in journal. Check Polymarket order and fill history before restarting.")

    def settle(self):
        # This journal tracks spending. Polymarket manages settlement and redemption.
        pass

    def record(self, slug, order, reserve=D(0)):
        self.save({
            **self.state,
            "cash": str(D(self.state["cash"]) - reserve),
            "orders": {**self.state["orders"], slug: order},
        })

    def submit(self, token, shares, price):
        sdk = self.sdk
        args = sdk.OrderArgs(token_id=token, side=sdk.Side.BUY, price=float(price), size=float(shares))
        signed = self.client.create_order(args)
        return self.client.post_order(signed, sdk.OrderType.FOK)

    def inspect(self, market, slug, end):
        if slug in self.state["orders"] or time.time() >= end:
            return
        tokens = dict(zip(as_list(market["outcomes"]), as_list(market["clobTokenIds"])))
        if set(tokens) != {"Up", "Down"} or not all(tokens.values()):
            return
        up, down = [self.get_orderbook(tokens[side]) for side in ("Up", "Down")]
        budget = min(D(self.state["cash"]), TRADE_CAP)
        minimum = D(str(market.get("orderMinSize") or 5))
        pair = pair_size(up, down, budget, minimum)
        if not pair:
            return
        shares, _ = pair
        while shares >= minimum:
            limits = [price_limit(book, shares) for book in (up, down)]
            # Reserve worst-case limit spend plus the maximum modeled fee per share.
            reserved = shares * (sum(limits) + FEE_RATE / 2) + D("0.00002")
            if reserved <= budget and reserved < shares:
                break
            shares -= D("0.01")
        else:
            return
        if time.time() >= end:
            return
        quoted_down = quote(down, shares)

        order = {"phase": "submitting_up", "shares": str(shares), "tokens": tokens,
                 "reserved": str(reserved), "end": end}
        self.record(slug, order, reserved)
        try:
            for side, limit in zip(("Up", "Down"), limits):
                if side == "Down":
                    fresh = self.get_orderbook(tokens[side])
                    refreshed = quote(fresh, shares)
                    if refreshed is None or refreshed > quoted_down or time.time() >= end:
                        raise RuntimeError("Down no longer meets original entry conditions.")
                    refreshed_limit = price_limit(fresh, shares)
                    if refreshed_limit > limit:
                        raise RuntimeError("Down depth moved beyond the reserved limit.")
                    limit = refreshed_limit
                    order = {**order, "phase": "submitting_down"}
                    self.record(slug, order)

                response = self.submit(tokens[side], shares, limit)
                order = {**order, side: response}
                self.record(slug, order)
                if (
                    not response.get("success") or response.get("status") != "matched"
                    or not response.get("orderID") or D(str(response.get("takingAmount"))) != shares
                ):
                    raise RuntimeError(f"{side} full fill was not confirmed.")
            self.record(slug, {**order, "phase": "paired"})
            log.info("LIVE matched %s | shares per side=%s | reserved=%s", slug, shares, reserved)
        except Exception as error:
            raise SystemExit(f"LIVE stopped for reconciliation: {slug}: {error}") from error


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--live", action="store_true", help="Enable real order submission")
    parser.add_argument("--budget", type=D, default=D("35"), help="Total spend reserved for this journal")
    args = parser.parse_args()
    geo = requests.get("https://polymarket.com/api/geoblock", timeout=12)
    geo.raise_for_status()
    result = geo.json()
    if result.get("blocked") is not False:
        raise SystemExit("Polymarket did not confirm this connection is eligible.")
    print("Polymarket geoblock check passed.", flush=True)
    if not args.live:
        return
    if not args.budget.is_finite() or args.budget <= 0:
        parser.error("--budget must be positive and finite")
    import py_clob_client_v2 as sdk

    path = Path(__file__).resolve().with_name("btc-live-state.json")
    with path.with_suffix(".lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        settings = {"host": HOST, "chain_id": 137, "key": os.environ["POLYMARKET_PRIVATE_KEY"]}
        if funder := os.environ.get("POLYMARKET_FUNDER"):
            settings["funder"] = funder
        if signature := os.environ.get("POLYMARKET_SIGNATURE_TYPE"):
            settings["signature_type"] = int(signature)
        client = sdk.ClobClient(**settings)
        client.set_api_creds(client.create_or_derive_api_key())
        bot = LiveBtc(path, client, sdk, args.budget)
        log.info("LIVE BTC | remaining local spending budget=%s", bot.state["cash"])
        while True:
            bot.scan_once()
            time.sleep(4.5)


if __name__ == "__main__":
    try:
        main()
    except KeyboardInterrupt:
        pass
