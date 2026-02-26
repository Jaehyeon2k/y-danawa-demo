package yju.danawa.com.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ClickLogRequest(
        @NotBlank String isbn,
        @NotBlank String target_channel,
        @NotNull @DecimalMin("0.0") @DecimalMax("1.0") Double slider_value
) {
}
