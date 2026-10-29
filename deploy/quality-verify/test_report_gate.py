import importlib.util
from pathlib import Path
import tempfile
import unittest


spec = importlib.util.spec_from_file_location("report_gate", Path(__file__).with_name("check-test-reports.py"))
gate = importlib.util.module_from_spec(spec)
spec.loader.exec_module(gate)


class ReportGateTests(unittest.TestCase):
    def inspect(self, xml, required=("sample",)):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            reports = root / "sample/target/surefire-reports"
            reports.mkdir(parents=True)
            (reports / "TEST-sample.xml").write_text(xml, encoding="utf-8")
            return gate.inspect_reports(root, required)

    def test_all_executed_and_passed(self):
        result = self.inspect('<testsuite tests="1"><testcase name="ok"/></testsuite>')
        self.assertTrue(result["passed"])
        self.assertEqual([("ok", "passed")], [(c["test"], c["state"]) for c in result["executedTests"]])

    def test_missing_module_is_not_success(self):
        result = self.inspect('<testsuite tests="1"/>', ("sample", "missing"))
        self.assertFalse(result["passed"])
        self.assertEqual(result["missingModules"], ["missing"])

    def test_failure_error_and_skip_each_fail(self):
        for state, attr in (("failure", "failures"), ("error", "errors"), ("skipped", "skipped")):
            with self.subTest(state=state):
                result = self.inspect(f'<testsuite tests="1" {attr}="1"><testcase name="bad"><{state} message="secret"/></testcase></testsuite>')
                self.assertFalse(result["passed"])
                self.assertNotIn("secret", str(result))

    def test_empty_suite_is_not_coverage(self):
        self.assertFalse(self.inspect('<testsuite tests="0"/>')["passed"])

    def test_missing_testcase_body_is_not_execution_evidence(self):
        result = self.inspect('<testsuite tests="1"/>')
        self.assertFalse(result["passed"])
        self.assertTrue(result["reportIntegrityErrors"])

    def test_incorrect_success_counters_cannot_hide_a_failure(self):
        result = self.inspect('<testsuite tests="1" failures="0"><testcase name="bad"><failure message="secret"/></testcase></testsuite>')
        self.assertFalse(result["passed"])
        self.assertEqual("failure", result["executedTests"][0]["state"])
        self.assertNotIn("secret", str(result))


if __name__ == "__main__":
    unittest.main()
