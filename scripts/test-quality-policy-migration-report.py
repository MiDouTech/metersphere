import importlib.util
import json
import unittest
from pathlib import Path

spec=importlib.util.spec_from_file_location("report",Path(__file__).with_name("quality-policy-migration-report.py"))
report=importlib.util.module_from_spec(spec);spec.loader.exec_module(report)

class ReportTests(unittest.TestCase):
    def item(self, name="first", maximum=100, roles=None):
        return {"id":name,"projectId":"p1","versionNo":1,"status":"DRAFT","contentHash":"original",
                "rulesJson":json.dumps({"name":name,"rules":[{"ruleId":"evidence","parameters":{"maxArtifactBytes":maximum,"requiredRoles":roles or ["AFTER","BEFORE"]}}]})}
    def test_metadata_and_array_order_do_not_create_false_rule_differences(self):
        text=report.render({"items":[self.item(),self.item("second",roles=["BEFORE","AFTER"])],"projects":["p1","p2"]})
        self.assertIn("distinct normalized rule sets: 1",text)
        self.assertIn("p2",text);self.assertIn("second",text);self.assertIn("original",text)
    def test_changed_parameter_and_invalid_document_are_reported(self):
        broken=self.item("broken");broken["rulesJson"]="invalid"
        text=report.render({"items":[self.item(),self.item("different",200),broken]})
        self.assertIn("distinct normalized rule sets: 2; invalid sources: 1",text)
        self.assertIn("maxArtifactBytes",text);self.assertIn("200",text)
    def test_partial_archive_is_rejected(self):
        with self.assertRaises(ValueError): report.render({"items":[self.item()],"total":2})

if __name__=="__main__": unittest.main()
