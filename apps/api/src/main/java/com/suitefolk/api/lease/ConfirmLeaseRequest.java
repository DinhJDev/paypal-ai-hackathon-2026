package com.suitefolk.api.lease;
import jakarta.validation.constraints.*;
public record ConfirmLeaseRequest(@NotNull @PositiveOrZero Long revision,@NotNull @AssertTrue Boolean confirmed) { }
