import importlib.util
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location("retained",Path(__file__).with_name("retain-upstream-reports.py"))
retained=importlib.util.module_from_spec(spec);spec.loader.exec_module(retained)


class RetainedReportsTests(unittest.TestCase):
    def report(self, text):
        folder=tempfile.TemporaryDirectory();self.addCleanup(folder.cleanup)
        file=Path(folder.name)/"TEST.xml";file.write_text(text)
        return [file]

    def test_source_report_must_match_successful_nonzero_summary(self):
        totals=dict(tests=1,failures=0,errors=0,skipped=0)
        reports=self.report('<testsuite tests="1"><testcase name="ok"/></testsuite>')
        self.assertEqual(totals,retained.validate_module("sample",reports,totals))
        with self.assertRaises(ValueError):retained.validate_module("sample",reports,{**totals,"tests":2})
        with self.assertRaises(ValueError):retained.validate_module("sample",[],totals)

    def test_failed_body_cannot_hide_behind_zero_counters(self):
        reports=self.report('<testsuite tests="1"><testcase name="bad"><failure/></testcase></testsuite>')
        with self.assertRaises(ValueError):retained.validate_module("sample",reports,dict(tests=1,failures=0,errors=0,skipped=0))

    def test_changed_dependency_tree_rejects_reuse(self):
        with patch.object(retained.subprocess,"check_output",side_effect=["old","new"]):
            with self.assertRaisesRegex(ValueError,"full verification required"):
                retained.matching_trees("source")


if __name__=="__main__":unittest.main()
