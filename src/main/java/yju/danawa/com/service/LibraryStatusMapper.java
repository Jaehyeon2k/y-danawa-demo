package yju.danawa.com.service;

import org.springframework.stereotype.Component;

@Component
public class LibraryStatusMapper {

    public NormalizedStatus normalize(boolean found, boolean available, String statusCodeRaw, String errorMessage) {
        String code = statusCodeRaw == null ? "" : statusCodeRaw.trim().toUpperCase();

        if (available || "AVAILABLE".equals(code)) {
            return new NormalizedStatus(true, true, "AVAILABLE", "\uC18C\uC7A5");
        }

        if ("ON_LOAN".equals(code) || "RESERVED".equals(code)) {
            return new NormalizedStatus(true, false, "ON_LOAN", "\uB300\uCD9C\uC911");
        }

        if ("UNAVAILABLE".equals(code)) {
            return new NormalizedStatus(true, false, "UNAVAILABLE", "\uC774\uC6A9\uBD88\uAC00");
        }

        if ("NOT_OWNED".equals(code)) {
            return new NormalizedStatus(false, false, "NOT_OWNED", "\uBBF8\uC18C\uC7A5");
        }

        if ("ERROR".equals(code)) {
            return new NormalizedStatus(false, false, "ERROR", "\uC815\uBCF4 \uC5C6\uC74C");
        }

        // UNKNOWN, empty, or contradictory states -> do not infer ON_LOAN.
        return new NormalizedStatus(false, false, "UNKNOWN", "\uC815\uBCF4 \uC5C6\uC74C");
    }

    public record NormalizedStatus(
            boolean holding,
            boolean loanable,
            String statusCode,
            String statusText
    ) {
    }
}
