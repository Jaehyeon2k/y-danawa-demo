package yju.danawa.com.dto;

import java.time.LocalDateTime;

public record SearchLogDto(
        Long logId,
        String keyword,
        String userDept,
        LocalDateTime searchTime
) {}
