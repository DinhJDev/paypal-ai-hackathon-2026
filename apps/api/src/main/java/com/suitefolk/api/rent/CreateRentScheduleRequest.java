package com.suitefolk.api.rent;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRentScheduleRequest(
		@NotBlank @Size(max = 120) String tenantLabel,
		@NotNull @DecimalMin("0.01") @Digits(integer = 8, fraction = 2) BigDecimal amount,
		@NotNull Frequency frequency,
		@NotNull @FutureOrPresent LocalDate nextDueDate) { }
