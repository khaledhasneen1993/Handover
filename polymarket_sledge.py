# language: Python 3.11+, file: polymarket_sledge.py
# target: Polymarket CLOB (Polygon)
# deps: pip install py-clob-client web3 eth-account requests pandas numpy

import time
import json
import logging
from datetime import datetime, timezone
from typing import Dict, List, Optional
import requests
import pandas as pd
import numpy as np
from py_clob_client.client import ClobClient
from py_clob_client.clob_types import OrderArgs, OrderType
from py_clob_client.order_builder.constants import BUY, SELL
from eth_account import Account

logging.basicConfig(level=logging.INFO, format="%(asctime)s | %(message)s")
log = logging.getLogger("SLEDGE")

# === CONFIG ===
PRIVATE_KEY = "YOUR_POLYGON_PRIVATE_KEY"          # محفظة Polygon
FUNDER_ADDRESS = "YOUR_PROXY_OR_EOA"              # العنوان اللي فيه USDC
HOST = "https://clob.polymarket.com"
CHAIN_ID = 137
SIGNATURE_TYPE = 0                                # 0 = EOA, 1 = Magic/Email, 2 = Browser

# عدوانية
MAX_POSITION_USDC = 2500.0      # أقصى حجم صفقة واحدة
MIN_EDGE = 0.08                 # 8% edge أدنى قبل الدخول
CONVICTION_SIZE_MULT = 3.2      # لو الـ edge > 15% ضاعف الحجم
SCAN_INTERVAL = 4.5             # ثواني
HIGH_VOLUME_ONLY = True         # ركز على الأسواق اللي فيها سيولة حقيقية

class PolymarketSledge:
    def __init__(self):
        self.client = ClobClient(
            HOST,
            key=PRIVATE_KEY,
            chain_id=CHAIN_ID,
            signature_type=SIGNATURE_TYPE,
            funder=FUNDER_ADDRESS
        )
        self.client.set_api_creds(self.client.create_or_derive_api_creds())
        self.open_positions: Dict[str, dict] = {}
        self.last_scan = 0

    def get_markets(self, active_only=True) -> List[dict]:
        # Gamma API للأسواق
        url = "https://gamma-api.polymarket.com/markets"
        params = {"active": "true", "closed": "false", "limit": 120}
        r = requests.get(url, params=params, timeout=12)
        markets = r.json()
        if HIGH_VOLUME_ONLY:
            markets = [m for m in markets if float(m.get("volume", 0) or 0) > 8000]
        return markets

    def get_orderbook(self, token_id: str) -> dict:
        try:
            return self.client.get_order_book(token_id)
        except Exception as e:
            log.warning(f"OB fail {token_id[:8]}: {e}")
            return {}

    def calc_edge(self, market: dict) -> Optional[dict]:
        """
        يحسب الـ edge الحقيقي:
        - لو السوق ثنائي (Yes/No) والسعر بعيد عن الاحتمال المعقول
        - أو فرق كبير بين related markets (arb)
        """
        tokens = market.get("tokens") or market.get("clobTokenIds") or []
        if not tokens or len(tokens) < 2:
            return None

        # أسعار حالية
        yes_token = tokens[0] if isinstance(tokens[0], str) else tokens[0].get("token_id")
        no_token = tokens[1] if isinstance(tokens[1], str) else tokens[1].get("token_id")

        ob_yes = self.get_orderbook(yes_token)
        ob_no = self.get_orderbook(no_token)
        if not ob_yes or not ob_no:
            return None

        # أفضل ask للشراء
        yes_ask = float(ob_yes.get("asks", [[1.0]])[0][0]) if ob_yes.get("asks") else 1.0
        no_ask = float(ob_no.get("asks", [[1.0]])[0][0]) if ob_no.get("asks") else 1.0

        # مجموع Yes+No المفروض ≈ 1.0 (بعد fees)
        # لو المجموع < 0.92 → arb فرصة
        sum_asks = yes_ask + no_ask
        arb_edge = 1.0 - sum_asks - 0.02  # خصم fees تقريبي

        # أو لو Yes رخيص جداً مقارنة بالـ volume/news proxy (هنا نستخدم volume + spread)
        spread_yes = 0
        if ob_yes.get("bids") and ob_yes.get("asks"):
            spread_yes = float(ob_yes["asks"][0][0]) - float(ob_yes["bids"][0][0])

        # إشارة conviction: سعر منخفض + سيولة عالية + spread ضيق
        volume = float(market.get("volume", 0) or 0)
        conviction = 0.0
        side = None
        token = None
        price = None

        if arb_edge > MIN_EDGE:
            # اشتري الاثنين (arb كلاسيكي)
            return {
                "type": "arb",
                "edge": arb_edge,
                "yes_token": yes_token,
                "no_token": no_token,
                "yes_price": yes_ask,
                "no_price": no_ask,
                "size_mult": 1.0 + (arb_edge * 4)
            }

        # Momentum / underpriced
        if yes_ask < 0.28 and volume > 15000 and spread_yes < 0.04:
            conviction = (0.35 - yes_ask) * 2.2
            side = BUY
            token = yes_token
            price = yes_ask
        elif no_ask < 0.28 and volume > 15000:
            conviction = (0.35 - no_ask) * 2.2
            side = BUY
            token = no_token
            price = no_ask

        if conviction >= MIN_EDGE:
            return {
                "type": "conviction",
                "edge": conviction,
                "side": side,
                "token": token,
                "price": price,
                "size_mult": CONVICTION_SIZE_MULT if conviction > 0.15 else 1.4
            }
        return None

    def place_aggressive(self, signal: dict):
        try:
            if signal["type"] == "arb":
                # اشتري Yes و No بنفس الوقت
                size = min(MAX_POSITION_USDC * signal["size_mult"] / 2, MAX_POSITION_USDC)
                # Yes
                args_yes = OrderArgs(
                    token_id=signal["yes_token"],
                    price=signal["yes_price"],
                    size=size / signal["yes_price"],
                    side=BUY
                )
                # No
                args_no = OrderArgs(
                    token_id=signal["no_token"],
                    price=signal["no_price"],
                    size=size / signal["no_price"],
                    side=BUY
                )
                signed_yes = self.client.create_order(args_yes)
                signed_no = self.client.create_order(args_no)
                self.client.post_order(signed_yes, OrderType.GTC)
                self.client.post_order(signed_no, OrderType.GTC)
                log.info(f"ARB HIT edge={signal['edge']:.3f} size≈{size:.0f} USDC")
                return

            # Conviction single side
            size_usdc = min(MAX_POSITION_USDC * signal["size_mult"], MAX_POSITION_USDC * 1.8)
            size_shares = size_usdc / signal["price"]
            args = OrderArgs(
                token_id=signal["token"],
                price=signal["price"],
                size=size_shares,
                side=signal["side"]
            )
            signed = self.client.create_order(args)
            resp = self.client.post_order(signed, OrderType.GTC)
            log.info(f"CONVICTION {signal['side']} edge={signal['edge']:.3f} size={size_usdc:.0f} USDC @ {signal['price']:.3f}")
            self.open_positions[signal["token"]] = {
                "entry": signal["price"],
                "size": size_shares,
                "ts": time.time()
            }
        except Exception as e:
            log.error(f"Order failed: {e}")

    def run(self):
        log.info("Sledgehammer online — scanning for blood")
        while True:
            try:
                markets = self.get_markets()
                for m in markets:
                    sig = self.calc_edge(m)
                    if sig and sig["edge"] >= MIN_EDGE:
                        self.place_aggressive(sig)
                        time.sleep(1.1)  # تجنب rate limit
                time.sleep(SCAN_INTERVAL)
            except Exception as e:
                log.error(f"Scan loop: {e}")
                time.sleep(6)

if __name__ == "__main__":
    bot = PolymarketSledge()
    bot.run()
