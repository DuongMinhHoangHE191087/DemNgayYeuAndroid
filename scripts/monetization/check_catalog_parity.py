"""Fail when iosApp/StoreKit/InLove.storekit drifts from the golden plan catalog GV-060 (M-71).

Usage: python scripts/monetization/check_catalog_parity.py
Prints one line per mismatch and exits 1; prints OK and exits 0 when they agree.
Checks product ids, product types, the single subscription group, the free trial and
Family Sharing. planCatalog.js is not parsed here: M-10's test ties it to the same golden.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
GOLDEN = ROOT / "shared/src/commonTest/resources/golden/GV-060-plan-catalog.json"
STOREKIT = ROOT / "iosApp/StoreKit/InLove.storekit"
# ponytail: .storekit key names are UNVERIFIED (#100); no Xcode here. Check them against the Xcode-generated file on a Mac and fix the reads below.
STOREKIT_TYPE = {"subscription": "RecurringSubscription", "lifetime": "NonConsumable"}


def load(path):
    with Path(path).open(encoding="utf-8") as f:
        return json.load(f)


def intro_offer(item):
    """Return paymentMode and period of the introductory offer, or None when there is none."""
    offer = item.get("introductoryOffer")
    if not offer:
        return None
    return {"paymentMode": offer.get("paymentMode"), "subscriptionPeriod": offer.get("subscriptionPeriod")}


def compare(golden, storekit):
    """Return the mismatches between the two catalogs; an empty list means they agree."""
    errors = []
    groups = storekit.get("subscriptionGroups", [])
    if len(groups) != 1:
        errors.append(f"expected 1 subscription group, found {len(groups)}")
    elif groups[0].get("name") != golden["subscriptionGroup"]:
        errors.append(f"subscription group {groups[0].get('name')!r}, expected {golden['subscriptionGroup']!r}")

    subscriptions = {s.get("productID") for g in groups for s in g.get("subscriptions", [])}
    items = storekit.get("products", []) + [s for g in groups for s in g.get("subscriptions", [])]
    ids = [item.get("productID") for item in items]
    errors += [f"duplicate product: {pid}" for pid in sorted({pid for pid in ids if ids.count(pid) > 1})]
    by_id = {item.get("productID"): item for item in items}

    for plan in golden["products"]:
        pid = plan["productId"]
        item = by_id.get(pid)
        if item is None:
            errors.append(f"missing product: {pid}")
            continue
        want_type = STOREKIT_TYPE[plan["kind"]]
        if item.get("type") != want_type:
            errors.append(f"{pid}: type {item.get('type')!r}, expected {want_type!r}")
        if plan["kind"] == "subscription" and pid not in subscriptions:
            errors.append(f"{pid}: not inside the subscription group")
        if item.get("familyShareable"):
            errors.append(f"{pid}: Family Sharing must be off")
        want_offer = {"paymentMode": "free", "subscriptionPeriod": f"P{plan['trialDays']}D"} if plan["trialDays"] else None
        if intro_offer(item) != want_offer:
            errors.append(f"{pid}: intro offer {intro_offer(item)}, expected {want_offer}")

    expected_ids = {plan["productId"] for plan in golden["products"]}
    errors += [f"unexpected product: {pid}" for pid in sorted(set(by_id) - expected_ids)]
    return errors


def main():
    if not STOREKIT.is_file():
        print(f"missing: {STOREKIT.relative_to(ROOT).as_posix()}")
        return 1
    errors = compare(load(GOLDEN), load(STOREKIT))
    for line in errors:
        print(line)
    if errors:
        return 1
    print("OK: InLove.storekit matches GV-060")
    return 0


if __name__ == "__main__":
    sys.exit(main())
