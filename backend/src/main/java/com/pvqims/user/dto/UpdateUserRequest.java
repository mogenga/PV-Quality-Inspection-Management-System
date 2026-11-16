package com.pvqims.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
    @NotBlank @Size(max = 50) String displayName,
    @NotBlank @Pattern(regexp = "INSPECTOR|ENGINEER|ADMIN", message = "角色只能是 INSPECTOR、ENGINEER 或 ADMIN")
    String role,
    @NotNull Boolean isActive) {
}
