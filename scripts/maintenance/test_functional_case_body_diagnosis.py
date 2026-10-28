import importlib.util
import io
import unittest
import zipfile
from pathlib import Path

spec = importlib.util.spec_from_file_location("body_diagnosis", Path(__file__).with_name("diagnose-functional-case-body.py"))
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


def pack(data):
    output = io.BytesIO()
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        archive.writestr("zip", data)
    return output.getvalue()


class BodyDiagnosisTests(unittest.TestCase):
    def test_plain_and_nested_valid_steps(self):
        value = b'[{"description":"step"}]'
        for layer, expected in enumerate(["PLAIN_VALID", "SINGLE_ZIP_VALID", "NESTED_RECOVERABLE", "NESTED_RECOVERABLE"]):
            result = module.diagnose(value)
            self.assertEqual(expected, result["classification"])
            self.assertEqual(layer, result["layers"])
            self.assertNotIn("content", result)
            value = pack(value)
        self.assertEqual("NEEDS_SOURCE", module.diagnose(value)["classification"])

    def test_invalid_content_and_utf8(self):
        for raw in [b"not-json", b"[null]", b"{}", bytes([255]), b"PK\x03\x04\x00"]:
            self.assertEqual("NEEDS_SOURCE", module.diagnose(raw)["classification"])
        self.assertEqual("PLAIN_VALID", module.diagnose(b"ordinary text", steps=False)["classification"])

    def test_real_corrupt_record_is_not_recoverable(self):
        root = Path(__file__).resolve().parents[2]
        value = bytes.fromhex((root / "backend/framework/sdk/src/test/resources/functional-case-corrupt-steps.hex").read_text())
        result = module.diagnose(value)
        self.assertEqual("NEEDS_SOURCE", result["classification"])
        self.assertEqual(370, result["bytes"])
        self.assertEqual(1, result["layers"])

    def test_expansion_limit(self):
        self.assertEqual("NEEDS_SOURCE", module.diagnose(pack(b" " * (module.MAX_BYTES + 1)))["classification"])


if __name__ == "__main__":
    unittest.main()
