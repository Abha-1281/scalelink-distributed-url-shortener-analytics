package com.scalelink.urlservice.infrastructure.adapter.in.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateUrlRequest {

    @NotBlank(message = "Original URL cannot be blank")
    private String longUrl;

    @Pattern(regexp = "^[a-zA-Z0-9_-]{3,30}$", message = "Custom alias must be 3-30 alphanumeric characters, hyphens, or underscores")
    private String customAlias;

    private Instant expiresAt;
}
