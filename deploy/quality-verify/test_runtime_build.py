import importlib.util
from pathlib import Path
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location('runtime', Path(__file__).with_name('verify-runtime-build.py'))
runtime = importlib.util.module_from_spec(spec)
spec.loader.exec_module(runtime)


class RuntimeBuildTests(unittest.TestCase):
    def setUp(self):
        self.record = dict(sourceCommit='commit', sourceRun='18', backendTree='backend',
                           frontendTree='frontend', buildInputsHash='inputs', jarSha256='jar')
        self.summary = dict(passed=True, modules={name: dict(tests=1, failures=0, errors=0, skipped=0)
                                     for name in runtime.MODULES}, missingModules=[], reportIntegrityErrors=[])

    def check(self):
        runtime.validate(self.record, self.summary, 'commit', '18', 'backend', 'frontend', 'inputs', 'jar')

    def test_verified_package(self):
        self.check()

    def test_changed_package_or_source_is_rejected(self):
        for field in self.record:
            with self.subTest(field=field):
                old = self.record[field]
                self.record[field] = 'different'
                with self.assertRaises(ValueError): self.check()
                self.record[field] = old

    def test_failed_skipped_or_missing_module_is_rejected(self):
        for key in ('failures', 'errors', 'skipped'):
            self.summary['modules']['app'][key] = 1
            with self.assertRaises(ValueError): self.check()
            self.summary['modules']['app'][key] = 0
        del self.summary['modules']['app']
        with self.assertRaises(ValueError): self.check()

    def test_inconsistent_report_is_rejected(self):
        self.summary['reportIntegrityErrors'] = ['count mismatch']
        with self.assertRaises(ValueError): self.check()

    def test_tracked_outer_build_changes_cannot_reuse_cached_package(self):
        with patch.object(runtime.subprocess, 'check_output', return_value='same'):
            runtime.validate_source_inputs('source')
        with patch.object(runtime.subprocess, 'check_output', side_effect=['old', 'new']):
            with self.assertRaisesRegex(ValueError, 'Build input changed'):
                runtime.validate_source_inputs('source')

    def test_current_frontend_must_have_matching_build_source(self):
        record = dict(frontendTree='current', sourceCommit='a' * 40)
        with patch.object(runtime.subprocess, 'check_output', return_value='current'):
            runtime.validate_frontend_artifact(record, 'current')
        with patch.object(runtime.subprocess, 'check_output', return_value='stale'):
            with self.assertRaises(ValueError): runtime.validate_frontend_artifact(record, 'current')
        with self.assertRaises(ValueError): runtime.validate_frontend_artifact(record, 'different')


if __name__ == '__main__': unittest.main()
