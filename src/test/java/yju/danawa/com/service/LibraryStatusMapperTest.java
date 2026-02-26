package yju.danawa.com.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LibraryStatusMapperTest {

    private final LibraryStatusMapper mapper = new LibraryStatusMapper();

    @Test
    void normalize_available_returnsSojang() {
        LibraryStatusMapper.NormalizedStatus s = mapper.normalize(false, true, "NOT_OWNED", "");
        assertTrue(s.holding());
        assertTrue(s.loanable());
        assertEquals("AVAILABLE", s.statusCode());
        assertEquals("\uC18C\uC7A5", s.statusText());
    }

    @Test
    void normalize_availableCode_returnsSojang() {
        LibraryStatusMapper.NormalizedStatus s = mapper.normalize(false, false, "AVAILABLE", "");
        assertTrue(s.holding());
        assertTrue(s.loanable());
        assertEquals("AVAILABLE", s.statusCode());
        assertEquals("\uC18C\uC7A5", s.statusText());
    }

    @Test
    void normalize_onLoan_returnsDaeculjung() {
        LibraryStatusMapper.NormalizedStatus s = mapper.normalize(false, false, "ON_LOAN", "");
        assertFalse(s.loanable());
        assertEquals("ON_LOAN", s.statusCode());
        assertEquals("\uB300\uCD9C\uC911", s.statusText());
    }

    @Test
    void normalize_reserved_returnsDaeculjung() {
        LibraryStatusMapper.NormalizedStatus s = mapper.normalize(false, false, "RESERVED", "");
        assertFalse(s.loanable());
        assertEquals("ON_LOAN", s.statusCode());
        assertEquals("\uB300\uCD9C\uC911", s.statusText());
    }

    @Test
    void normalize_notOwned_returnsDaeculjung() {
        LibraryStatusMapper.NormalizedStatus s = mapper.normalize(false, false, "NOT_OWNED", "");
        assertFalse(s.loanable());
        assertEquals("NOT_OWNED", s.statusCode());
        assertEquals("\uBBF8\uC18C\uC7A5", s.statusText());
    }

    @Test
    void normalize_error_returnsDaeculjung() {
        LibraryStatusMapper.NormalizedStatus s = mapper.normalize(true, false, "ERROR", "parse failed");
        assertFalse(s.holding());
        assertFalse(s.loanable());
        assertEquals("ERROR", s.statusCode());
        assertEquals("\uC815\uBCF4 \uC5C6\uC74C", s.statusText());
    }

    @Test
    void normalize_unknown_returnsDaeculjung() {
        LibraryStatusMapper.NormalizedStatus s = mapper.normalize(false, false, "UNKNOWN", "");
        assertFalse(s.holding());
        assertFalse(s.loanable());
        assertEquals("UNKNOWN", s.statusCode());
        assertEquals("\uC815\uBCF4 \uC5C6\uC74C", s.statusText());
    }

    @Test
    void normalize_nullCode_returnsDaeculjung() {
        LibraryStatusMapper.NormalizedStatus s = mapper.normalize(false, false, null, "");
        assertFalse(s.holding());
        assertFalse(s.loanable());
        assertEquals("UNKNOWN", s.statusCode());
        assertEquals("\uC815\uBCF4 \uC5C6\uC74C", s.statusText());
    }

    @Test
    void normalize_foundTrue_notAvailable_returnsDaeculjung() {
        LibraryStatusMapper.NormalizedStatus s = mapper.normalize(true, false, "NOT_OWNED", "");
        assertFalse(s.holding());
        assertFalse(s.loanable());
        assertEquals("NOT_OWNED", s.statusCode());
        assertEquals("\uBBF8\uC18C\uC7A5", s.statusText());
    }
}
