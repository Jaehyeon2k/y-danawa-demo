package yju.danawa.com.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class IsbnLinksTest {

    @Test
    void validatesIsbn10And13() {
        assertTrue(IsbnLinks.isValidIsbn10("0306406152"));
        assertTrue(IsbnLinks.isValidIsbn13("9780306406157"));
        assertFalse(IsbnLinks.isValidIsbn10("0306406153"));
        assertFalse(IsbnLinks.isValidIsbn13("9780306406158"));
    }

    @Test
    void buildsDeepLinks() {
        var links = IsbnLinks.buildDeepLinks("9780306406157");
        assertTrue(links.get("yes24").contains("9780306406157"));
        assertTrue(links.get("kyobo").contains("9780306406157"));
    }
}
