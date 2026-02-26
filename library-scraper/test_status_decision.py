import unittest
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from scraper_service import LibraryScraper


class LibraryStatusDecisionTest(unittest.TestCase):
    def test_not_owned_requires_container_and_explicit_signal(self):
        status, _ = LibraryScraper.decide_status_from_evidence(
            {
                "page_guard_ok": True,
                "result_container_ready": True,
                "not_owned_signal": True,
                "body_text": "검색 결과가 없습니다",
            }
        )
        self.assertEqual("NOT_OWNED", status)

    def test_not_owned_forbidden_when_container_not_ready(self):
        status, _ = LibraryScraper.decide_status_from_evidence(
            {
                "page_guard_ok": True,
                "result_container_ready": False,
                "not_owned_signal": True,
                "body_text": "검색 결과가 없습니다",
            }
        )
        self.assertIn(status, {"UNKNOWN", "ERROR"})
        self.assertNotEqual("NOT_OWNED", status)

    def test_available_signal_never_downgrades_to_not_owned(self):
        status, _ = LibraryScraper.decide_status_from_evidence(
            {
                "page_guard_ok": True,
                "result_container_ready": True,
                "not_owned_signal": True,
                "row_count": 1,
                "row_has_available": True,
                "body_text": "대출가능",
            }
        )
        self.assertEqual("AVAILABLE", status)

    def test_body_token_without_row_must_not_be_on_loan(self):
        status, _ = LibraryScraper.decide_status_from_evidence(
            {
                "page_guard_ok": True,
                "result_container_ready": True,
                "row_count": 0,
                "row_has_available": False,
                "row_has_on_loan": False,
                "row_has_reserved": False,
                "not_owned_signal": False,
                "body_text": "대출중",
            }
        )
        self.assertNotEqual("ON_LOAN", status)

    def test_guard_failure_becomes_error(self):
        status, _ = LibraryScraper.decide_status_from_evidence(
            {
                "page_guard_ok": False,
                "result_container_ready": True,
                "not_owned_signal": True,
                "body_text": "로그인 필요",
            }
        )
        self.assertEqual("ERROR", status)

    def test_ebook_only_returns_not_owned(self):
        status, _ = LibraryScraper.decide_status(
            {
                "page_guard_ok": True,
                "row_count": 1,
                "monograph_row_found": False,
                "content_ready": True,
                "not_owned": False,
            }
        )
        self.assertEqual("NOT_OWNED", status)

    def test_monograph_mixed_picks_available(self):
        status, _ = LibraryScraper.decide_status(
            {
                "page_guard_ok": True,
                "row_count": 2,
                "monograph_row_found": True,
                "has_available": True,
                "has_on_loan": True,
                "content_ready": True,
            }
        )
        self.assertEqual("AVAILABLE", status)


if __name__ == "__main__":
    unittest.main()
