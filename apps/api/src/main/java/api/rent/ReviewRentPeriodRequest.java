package api.rent;
import jakarta.validation.constraints.*;
public record ReviewRentPeriodRequest(@NotNull @PositiveOrZero Long revision, @AssertTrue boolean confirmed) { }
