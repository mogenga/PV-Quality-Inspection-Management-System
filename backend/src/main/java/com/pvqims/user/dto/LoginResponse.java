package com.pvqims.user.dto;

public record LoginResponse(String token, UserSummary user) {
}
