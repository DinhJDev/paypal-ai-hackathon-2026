package com.suitefolk.api.rent;
import java.time.LocalDate;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
public record StartRentAutopayRequest(@NotNull LocalDate firstChargeDate, @AssertTrue boolean confirmed) { }
