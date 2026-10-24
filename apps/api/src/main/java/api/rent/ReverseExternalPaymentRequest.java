package api.rent;
import jakarta.validation.constraints.*;
public record ReverseExternalPaymentRequest(@NotBlank @Size(max=240) String reason, @AssertTrue boolean confirmed) { }
