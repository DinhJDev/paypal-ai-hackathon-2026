package com.suitefolk.api.lease;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
public record SaveLeaseDraftRequest(@NotNull @PositiveOrZero Long revision,@NotNull @Valid LeaseTerms terms) { }
