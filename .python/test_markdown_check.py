import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location("generator", Path(__file__).with_name("generate_markdown.py"))
generator = importlib.util.module_from_spec(spec)
spec.loader.exec_module(generator)

class ReadOnlyCheckTest(unittest.TestCase):
    def test_missing_output_does_not_create_directory(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "missing" / "README.md"
            self.assertEqual(1, generator.finish_outputs({path: "expected\n"}, True))
            self.assertFalse(path.parent.exists())

    def test_drift_is_reported_without_overwriting(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "README.md"
            path.write_text("user edit\n", encoding="utf-8")
            before = path.stat().st_mtime_ns
            self.assertEqual(1, generator.finish_outputs({path: "expected\n"}, True))
            self.assertEqual("user edit\n", path.read_text(encoding="utf-8"))
            self.assertEqual(before, path.stat().st_mtime_ns)

    def test_write_then_check_matches(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory) / "README.md"
            self.assertEqual(0, generator.finish_outputs({path: "expected\n"}, False))
            before = path.stat().st_mtime_ns
            self.assertEqual(0, generator.finish_outputs({path: "expected\n"}, True))
            self.assertEqual(before, path.stat().st_mtime_ns)
