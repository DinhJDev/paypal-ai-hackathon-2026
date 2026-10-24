package api.rent;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import jakarta.persistence.*;

@Entity
@Table(name="rent_external_payment")
public class RentExternalPayment {
    @Id private UUID id;
    @Column(name="period_id", nullable=false, updatable=false) private UUID periodId;
    @Column(nullable=false, precision=10, scale=2, updatable=false) private BigDecimal amount;
    @Column(name="paid_on", nullable=false, updatable=false) private LocalDate paidOn;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=24, updatable=false) private ExternalPaymentMethod method;
    @Column(nullable=false, length=120, updatable=false) private String reference;
    @Column(name="recorded_by", nullable=false, updatable=false) private UUID recordedBy;
    @Column(name="recorded_at", nullable=false, updatable=false) private Instant recordedAt;
    @Column(name="reversed_at") private Instant reversedAt;
    @Column(name="reversed_by") private UUID reversedBy;
    @Column(name="reversal_reason", length=240) private String reversalReason;
    protected RentExternalPayment() { }
    public RentExternalPayment(UUID id, UUID periodId, BigDecimal amount, LocalDate paidOn,
            ExternalPaymentMethod method, String reference, UUID ownerId) {
        this.id=id; this.periodId=periodId; this.amount=amount; this.paidOn=paidOn;
        this.method=method; this.reference=reference; this.recordedBy=ownerId; this.recordedAt=Instant.now();
    }
    public UUID getId() { return id; }
    public UUID getPeriodId() { return periodId; }
    public BigDecimal getAmount() { return amount; }
    public LocalDate getPaidOn() { return paidOn; }
    public ExternalPaymentMethod getMethod() { return method; }
    public String getReference() { return reference; }
    public Instant getRecordedAt() { return recordedAt; }
    public Instant getReversedAt() { return reversedAt; }
    public String getReversalReason() { return reversalReason; }
    public void reverse(UUID ownerId, String reason) {
        reversedAt=Instant.now(); reversedBy=ownerId; reversalReason=reason;
    }
    public boolean matches(UUID period, BigDecimal amount, LocalDate paidOn, ExternalPaymentMethod method, String reference) {
        return periodId.equals(period) && this.amount.compareTo(amount)==0 && this.paidOn.equals(paidOn)
            && this.method==method && this.reference.equals(reference);
    }
}
