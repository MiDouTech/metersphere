"""Producer/consumer path parity is part of the Actions cache version contract."""
from pathlib import Path
import re
import unittest

ROOT = Path(__file__).resolve().parents[2]
MAIN = ROOT / '.github/workflows/msp-remediation-verification.yml'
RUNTIME = ROOT / '.github/workflows/msp-runtime-verification.yml'


def paths(text, operation, marker):
    found = []
    for block in text.split('\n      - '):
        if f'uses: actions/cache/{operation}@v4' not in block:
            continue
        match = re.search(r'(?m)^          path: \|\n((?:            [^\n]+\n?)+)', block)
        if match:
            values = {line.strip() for line in match[1].splitlines()}
            if marker in values:
                found.append(values)
    if len(found) != 1:
        raise ValueError(f'Exactly one {operation} cache with {marker} is required')
    return found[0]


class WorkflowCacheContractTests(unittest.TestCase):
    def test_backend_package_paths_match_the_actual_producer(self):
        self.assertEqual(paths(MAIN.read_text(), 'save', '.ci-backend-build.json'),
                         paths(RUNTIME.read_text(), 'restore', '.ci-backend-build.json'))

    def test_frontend_and_image_producer_consumer_paths_match(self):
        text = RUNTIME.read_text()
        for marker in ('.ci-frontend-build.json', '.ci-app-image.json'):
            self.assertEqual(paths(text, 'save', marker), paths(text, 'restore', marker))

    def test_adding_frontend_metadata_to_backend_paths_is_detected(self):
        original = paths(MAIN.read_text(), 'save', '.ci-backend-build.json')
        broken = RUNTIME.read_text().replace('            backend/app/target/app-3.x.jar\n',
                    '            backend/app/target/app-3.x.jar\n            .ci-frontend-build.json\n')
        self.assertNotEqual(original, paths(broken, 'restore', '.ci-backend-build.json'))


if __name__ == '__main__': unittest.main()
