package api.rent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.validation.constraints.*;
public record RecordExternalPaymentRequest(@NotNull UUID requestId,
        @NotNull @DecimalMin("0.01") @Digits(integer=8,fraction=2) BigDecimal amount,
        @NotNull LocalDate paidOn, @NotNull ExternalPaymentMethod method,
        @Size(max=120) String reference, @NotNull @PositiveOrZero Long revision, @AssertTrue boolean confirmed) { }
