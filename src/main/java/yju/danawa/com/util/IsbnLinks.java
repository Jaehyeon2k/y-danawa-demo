package yju.danawa.com.util;

import java.util.LinkedHashMap;
import java.util.Map;

public final class IsbnLinks {

    private IsbnLinks() {
    }

    public static String normalizeIsbn(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replaceAll("[^0-9Xx]", "").toUpperCase();
    }

    public static boolean isValidIsbn(String isbn) {
        return isValidIsbn10(isbn) || isValidIsbn13(isbn);
    }

    public static boolean isValidIsbn10(String isbn) {
        if (isbn == null || isbn.length() != 10) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 10; i++) {
            char c = isbn.charAt(i);
            int value;
            if (c == 'X') {
                value = 10;
            } else if (Character.isDigit(c)) {
                value = Character.getNumericValue(c);
            } else {
                return false;
            }
            sum += value * (10 - i);
        }
        return sum % 11 == 0;
    }

    public static boolean isValidIsbn13(String isbn) {
        if (isbn == null || isbn.length() != 13) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 13; i++) {
            char c = isbn.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
            int digit = Character.getNumericValue(c);
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += digit * weight;
        }
        return sum % 10 == 0;
    }

    public static Map<String, String> buildDeepLinks(String rawIsbn) {
        String isbn = normalizeIsbn(rawIsbn);
        if (!isValidIsbn(isbn)) {
            throw new IllegalArgumentException("Invalid ISBN");
        }

        Map<String, String> links = new LinkedHashMap<>();
        links.put("yes24", "http://www.yes24.com/Product/Search?domain=BOOK&query=" + isbn);
        links.put("kyobo", "https://search.kyobobook.co.kr/search?keyword=" + isbn);
        links.put("yjcEbook", "https://ebook.yjc.ac.kr/search/?srch_order=total&src_key=" + isbn);
        links.put("yjcCentral", "https://lib.yju.ac.kr/Cheetah/Search/AdvenceSearch#/total/" + isbn);
        return links;
    }
}
