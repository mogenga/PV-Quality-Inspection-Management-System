package com.pvqims.standard.dto;

import jakarta.validation.constraints.NotNull;

public record SetEnabledRequest(@NotNull Boolean isEnabled) {
}
