package api.rent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RentPeriodView(UUID id, LocalDate dueDate, BigDecimal amountDue, BigDecimal amountReceived,
        BigDecimal balance, String paymentStatus, boolean collectionHeld, boolean reviewRequired,
        boolean reconciliationRequired, String holdReason, long revision, Instant reviewedAt,
        List<ExternalPaymentView> externalPayments, List<RentPaymentView> paypalPayments) {
    public record ExternalPaymentView(UUID id, BigDecimal amount, LocalDate paidOn, ExternalPaymentMethod method,
        String reference, Instant recordedAt, Instant reversedAt, String reversalReason) { }
}
