package yju.danawa.com.dto;

import java.time.LocalDateTime;

public record ClickLogDto(
        Long clickId,
        String isbn,
        String targetChannel,
        Double sliderValue,
        LocalDateTime createdAt
) {}
