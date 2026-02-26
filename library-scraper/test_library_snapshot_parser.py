import unittest
from pathlib import Path

from scraper_service import LibraryScraper


class LibrarySnapshotParserTest(unittest.TestCase):
    def _read_fixture(self, name: str) -> str:
        root = Path(__file__).resolve().parent
        return (root / "tests" / "fixtures" / name).read_text(encoding="utf-8")

    def test_pick_monograph_available_when_mixed(self):
        html = self._read_fixture("library_mixed_results.html")
        items = LibraryScraper.parse_snapshot_items(html)
        picked = LibraryScraper.pick_best_snapshot_item(items, "클린 코드")
        self.assertEqual("monograph", picked.get("record_type"))
        self.assertEqual("AVAILABLE", picked.get("loan_badge"))

    def test_aggregate_available_if_any_monograph_available(self):
        html = self._read_fixture("library_mixed_results.html")
        items = LibraryScraper.parse_snapshot_items(html)
        mono = [i for i in items if i.get("record_type") == "monograph"]
        self.assertTrue(any(i.get("loan_badge") == "AVAILABLE" for i in mono))

    def test_ebook_only_should_not_be_physical_available(self):
        html = self._read_fixture("library_ebook_only.html")
        items = LibraryScraper.parse_snapshot_items(html)
        picked = LibraryScraper.pick_best_snapshot_item(items, "도메인 주도 설계")
        self.assertEqual("ebook", picked.get("record_type"))
        self.assertNotEqual("AVAILABLE", picked.get("loan_badge"))


if __name__ == "__main__":
    unittest.main()
