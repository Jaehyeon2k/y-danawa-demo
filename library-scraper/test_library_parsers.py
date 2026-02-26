import unittest

from library_search_engine import clean_title, hybrid_encode_query, parse_ebook_holding_count


class LibraryParserTest(unittest.TestCase):
    def test_clean_title_removes_subtitle(self):
        self.assertEqual(clean_title("\uC790\uBC14\uC758 \uC815\uC11D - \uAE30\uCD08\uD3B8"), "\uC790\uBC14\uC758 \uC815\uC11D")
        self.assertEqual(clean_title("Operating Systems: Three Easy Pieces"), "Operating Systems")
        self.assertEqual(clean_title("\uB370\uC774\uD130\uBCA0\uC774\uC2A4(\uAC1C\uC815\uD310)"), "\uB370\uC774\uD130\uBCA0\uC774\uC2A4")

    def test_hybrid_encode_query_keeps_ascii_and_cp949_for_korean(self):
        encoded = hybrid_encode_query("\uC6B4\uC601\uCCB4\uC81C OS 101")
        self.assertIn("OS", encoded)
        self.assertIn("101", encoded)
        self.assertIn("%BF", encoded)

    def test_parse_ebook_holding_count_variants(self):
        html_1 = "<li>\uC804\uC790\uCC45 \uBCF4\uC720 0 \uB300\uCD9C 0</li>"
        html_2 = "<li>\uC804\uC790\uCC45 \uBCF4\uC720: 2</li>"
        html_3 = "<li>\uC804\uC790\uCC45 \uBCF4\uC720(1)</li>"
        self.assertEqual(parse_ebook_holding_count(html_1), 0)
        self.assertEqual(parse_ebook_holding_count(html_2), 2)
        self.assertEqual(parse_ebook_holding_count(html_3), 1)


if __name__ == "__main__":
    unittest.main()
