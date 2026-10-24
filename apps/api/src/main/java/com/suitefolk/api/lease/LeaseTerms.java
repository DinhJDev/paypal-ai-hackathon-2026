package com.suitefolk.api.lease;

import java.math.BigDecimal;
import java.time.LocalDate;
import com.suitefolk.api.rent.Frequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Size;

public record LeaseTerms(
    @Size(max=120) String tenantName,
    @Size(max=120) String suiteLabel,
    LocalDate startDate, LocalDate endDate,
    @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal amount,
    Frequency frequency, LocalDate firstDueDate,
    @Size(max=2000) String lateFeeTerms,
    @Size(max=4000) String furnishedItems) {
    public LeaseTerms normalized() {
        return new LeaseTerms(clean(tenantName),clean(suiteLabel),startDate,endDate,amount==null ? null : amount.stripTrailingZeros(),frequency,
            firstDueDate,clean(lateFeeTerms),clean(furnishedItems));
    }
    private static String clean(String value) { return value==null ? "" : value.trim(); }
}
