import importlib.util
from pathlib import Path
import tempfile
import unittest
import xml.etree.ElementTree as ET

spec = importlib.util.spec_from_file_location("collector", Path(__file__).with_name("collect-failed-reports.py"))
collector = importlib.util.module_from_spec(spec)
spec.loader.exec_module(collector)


class FailedReportCollectionTests(unittest.TestCase):
    def test_preserves_failed_details_and_excludes_properties_and_passing_logs(self):
        with tempfile.TemporaryDirectory() as folder:
            root=Path(folder)/"backend"; output=Path(folder)/"out"
            report=root/"module/target/surefire-reports/TEST-example.xml"
            report.parent.mkdir(parents=True)
            report.write_text('<testsuite tests="2"><properties><property name="secret"/></properties>'
                              '<testcase name="pass"><system-out>passing-session</system-out></testcase>'
                              '<testcase name="fail"><failure message="expected status"/>'
                              '<system-out>failure detail</system-out></testcase></testsuite>')
            self.assertEqual(1,collector.collect(root,output))
            result=ET.parse(output/report.relative_to(root)).getroot()
            self.assertIsNone(result.find("properties"))
            self.assertEqual(["fail"],[c.get("name") for c in result.findall("testcase")])
            self.assertEqual("failure detail",result.findtext("testcase/system-out"))

    def test_empty_reports_do_not_create_a_false_failure_artifact(self):
        with tempfile.TemporaryDirectory() as folder:
            self.assertEqual(0,collector.collect(Path(folder),Path(folder)/"out"))


if __name__ == "__main__":
    unittest.main()
