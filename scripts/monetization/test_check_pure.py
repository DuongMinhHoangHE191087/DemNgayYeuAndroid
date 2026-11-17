"""Tests for check_pure_monetization.py (M-29).

Each case writes sample .kt/.java files into a temp tree, runs the checker on
that tree and checks the exit code and the printed file:line entries.
"""
import subprocess
import sys
import tempfile
import unittest
from pathlib import Path

HERE = Path(__file__).resolve().parent
CHECKER = HERE / "check_pure_monetization.py"
MAIN = Path("app/src/main/java/com/example")
TEST = Path("app/src/test/java/com/example")


class CheckPureTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)

    def write(self, rel, *imports):
        """Write rel with the given imports on lines 3, 4, ... (line 1 package, line 2 blank)."""
        path = self.root / rel
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text("package x\n\n" + "\n".join(imports) + "\n", encoding="utf-8")

    def run_check(self):
        proc = subprocess.run(
            [sys.executable, str(CHECKER), str(self.root)],
            capture_output=True, text=True, encoding="utf-8",
        )
        return proc.returncode, proc.stdout + proc.stderr

    def test_flags_android_import(self):
        self.write(MAIN / "monetization/ads/AdGate.kt",
                   "import android.content.Context",
                   "import com.android.billingclient.api.BillingClient")
        code, out = self.run_check()
        self.assertEqual(code, 1, out)
        self.assertIn("AdGate.kt:3", out)
        self.assertIn("AdGate.kt:4", out)

    def test_flags_java_import(self):
        self.write(MAIN / "monetization/billing/Bad.kt",
                   "import java.util.UUID",
                   "import javax.crypto.Cipher")
        self.write(TEST / "monetization/Legacy.java", "import android.os.Bundle")
        code, out = self.run_check()
        self.assertEqual(code, 1, out)
        self.assertIn("Bad.kt:3", out)
        self.assertIn("Bad.kt:4", out)
        self.assertIn("Legacy.java:3", out)

    def test_flags_gms_appplugin_androidx_imports(self):
        self.write(MAIN / "monetization/consent/Consent.kt",
                   "import com.google.android.gms.tasks.Tasks",
                   "import com.app.plugin.Plugin",
                   "import androidx.lifecycle.ViewModel")
        code, out = self.run_check()
        self.assertEqual(code, 1, out)
        for line in (3, 4, 5):
            self.assertIn(f"Consent.kt:{line}", out)

    def test_flags_junit_robolectric_mockk_imports(self):
        self.write(TEST / "monetization/entitlement/EntTest.kt",
                   "import org.junit.Test",
                   "import org.robolectric.RobolectricTestRunner",
                   "import io.mockk.mockk")
        code, out = self.run_check()
        self.assertEqual(code, 1, out)
        for line in (3, 4, 5):
            self.assertIn(f"EntTest.kt:{line}", out)

    def test_allows_kotlin_and_coroutines(self):
        self.write(MAIN / "monetization/entitlement/Ok.kt",
                   "import kotlin.collections.List",
                   "import kotlinx.coroutines.flow.Flow",
                   "import com.example.monetization.consent.Consent")
        self.write(TEST / "monetization/OkTest.kt",
                   "import kotlin.test.Test",
                   "import kotlinx.coroutines.test.runTest")
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_ignores_android_subpackage(self):
        self.write(TEST / "monetization/android/AndroidOnly.kt", "import android.os.Build")
        self.write(TEST / "monetization/Shared.kt", "import android.os.Build")
        code, out = self.run_check()
        self.assertEqual(code, 1, out)
        self.assertIn("Shared.kt:3", out)
        self.assertNotIn("AndroidOnly.kt", out)

    def test_missing_dirs_pass(self):
        code, out = self.run_check()
        self.assertEqual(code, 0, out)
        # one legacy file present, the other legacy files and all monetization dirs missing
        self.write(MAIN / "billing/PlanOffer.kt", "import kotlin.collections.List")
        code, out = self.run_check()
        self.assertEqual(code, 0, out)

    def test_scans_legacy_billing_files(self):
        self.write(MAIN / "billing/PlanOffer.kt", "import android.app.Activity")
        self.write(MAIN / "billing/PurchaseEvent.kt", "import androidx.core.Foo")
        self.write(MAIN / "billing/VipProductIds.kt", "import com.android.billingclient.api.X")
        self.write(TEST / "billing/PlanOfferTest.kt", "import org.junit.Test")
        # out of scope: BillingManager.kt is Android code by design and must not be flagged
        self.write(MAIN / "billing/BillingManager.kt", "import android.content.Context")
        code, out = self.run_check()
        self.assertEqual(code, 1, out)
        for name in ("PlanOffer.kt:3", "PurchaseEvent.kt:3", "VipProductIds.kt:3", "PlanOfferTest.kt:3"):
            self.assertIn(name, out)
        self.assertNotIn("BillingManager.kt", out)


if __name__ == "__main__":
    unittest.main()
