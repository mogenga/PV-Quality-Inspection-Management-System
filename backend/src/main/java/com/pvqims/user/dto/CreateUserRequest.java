package com.pvqims.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
    @NotBlank @Size(max = 50) String username,
    @NotBlank @Size(min = 8, max = 64) String password,
    @NotBlank @Size(max = 50) String displayName,
    @NotBlank @Pattern(regexp = "INSPECTOR|ENGINEER|ADMIN", message = "角色只能是 INSPECTOR、ENGINEER 或 ADMIN")
    String role) {
}
