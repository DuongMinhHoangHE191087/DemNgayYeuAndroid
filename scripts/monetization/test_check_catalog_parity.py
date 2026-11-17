"""Tests for check_catalog_parity.py (M-71).

The real InLove.storekit must agree with the golden GV-060 catalog. The drift cases
edit an in-memory copy of the real file and check that compare() reports the change.
"""
import copy
import unittest
from pathlib import Path

import check_catalog_parity as cp

ROOT = Path(__file__).resolve().parents[2]
GOLDEN = ROOT / "shared/src/commonTest/resources/golden/GV-060-plan-catalog.json"
STOREKIT = ROOT / "iosApp/StoreKit/InLove.storekit"


def find_item(storekit, product_id):
    """Return the product or subscription entry with this productID."""
    items = storekit["products"] + [s for g in storekit["subscriptionGroups"] for s in g["subscriptions"]]
    return next(item for item in items if item["productID"] == product_id)


class CatalogParityTest(unittest.TestCase):
    def setUp(self):
        self.golden = cp.load(GOLDEN)
        self.storekit = cp.load(STOREKIT)

    def mutated(self):
        return copy.deepcopy(self.storekit)

    def assertReports(self, storekit, text):
        errors = cp.compare(self.golden, storekit)
        self.assertTrue(any(text in e for e in errors), errors)

    def test_ids_match_golden(self):
        self.assertEqual(cp.compare(self.golden, self.storekit), [])

    def test_missing_product_fails(self):
        sk = self.mutated()
        group = sk["subscriptionGroups"][0]
        group["subscriptions"] = [s for s in group["subscriptions"] if s["productID"] != "vip_yearly"]
        self.assertReports(sk, "missing product: vip_yearly")

    def test_extra_product_fails(self):
        sk = self.mutated()
        sk["products"].append(dict(find_item(sk, "vip_lifetime"), productID="vip_extra"))
        self.assertReports(sk, "unexpected product: vip_extra")

    def test_yearly_has_three_day_free_trial(self):
        self.assertEqual(cp.intro_offer(find_item(self.storekit, "vip_yearly")),
                         {"paymentMode": "free", "subscriptionPeriod": "P3D"})
        sk = self.mutated()
        find_item(sk, "vip_yearly")["introductoryOffer"] = None
        self.assertReports(sk, "vip_yearly: intro offer")

    def test_lifetime_is_non_consumable(self):
        self.assertEqual(find_item(self.storekit, "vip_lifetime")["type"], "NonConsumable")
        sk = self.mutated()
        find_item(sk, "vip_lifetime")["type"] = "Consumable"
        self.assertReports(sk, "vip_lifetime: type")

    def test_single_subscription_group(self):
        self.assertEqual([g["name"] for g in self.storekit["subscriptionGroups"]], ["InLove VIP"])
        sk = self.mutated()
        sk["subscriptionGroups"].append({"id": "21000002", "name": "Other", "localizations": [], "subscriptions": []})
        self.assertReports(sk, "expected 1 subscription group")

    def test_renamed_subscription_group_fails(self):
        sk = self.mutated()
        sk["subscriptionGroups"][0]["name"] = "Other"
        self.assertReports(sk, "subscription group 'Other'")

    def test_subscription_outside_group_fails(self):
        sk = self.mutated()
        group = sk["subscriptionGroups"][0]
        monthly = find_item(sk, "vip_monthly")
        group["subscriptions"] = [s for s in group["subscriptions"] if s["productID"] != "vip_monthly"]
        sk["products"].append(monthly)
        self.assertReports(sk, "vip_monthly: not inside the subscription group")

    def test_duplicate_product_fails(self):
        sk = self.mutated()
        sk["products"].append(copy.deepcopy(find_item(sk, "vip_lifetime")))
        self.assertReports(sk, "duplicate product: vip_lifetime")

    def test_wrong_trial_payment_mode_or_period_fails(self):
        for field, value in (("paymentMode", "payAsYouGo"), ("subscriptionPeriod", "P7D")):
            sk = self.mutated()
            find_item(sk, "vip_yearly")["introductoryOffer"][field] = value
            self.assertReports(sk, "vip_yearly: intro offer")
        sk = self.mutated()
        find_item(sk, "vip_monthly")["introductoryOffer"] = {"paymentMode": "free", "subscriptionPeriod": "P3D"}
        self.assertReports(sk, "vip_monthly: intro offer")

    def test_no_family_sharing(self):
        for pid in ("vip_monthly", "vip_yearly", "vip_lifetime"):
            self.assertFalse(find_item(self.storekit, pid)["familyShareable"], pid)
        sk = self.mutated()
        find_item(sk, "vip_lifetime")["familyShareable"] = True
        self.assertReports(sk, "vip_lifetime: Family Sharing")


if __name__ == "__main__":
    unittest.main()
