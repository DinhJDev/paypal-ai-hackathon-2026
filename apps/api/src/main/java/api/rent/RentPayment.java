package api.rent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "rent_payment")
public class RentPayment {

	@Id
	@Column(nullable = false, updatable = false)
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID id;

	@Column(name = "schedule_id", nullable = false, updatable = false)
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID scheduleId;

	@Column(name = "due_date", nullable = false, updatable = false)
	private LocalDate dueDate;

	@Column(nullable = false, precision = 10, scale = 2, updatable = false)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private RentPaymentStatus status;

	@Column(name = "idempotency_key", nullable = false, length = 108, unique = true, updatable = false)
	private String idempotencyKey;

	@Column(name = "paypal_order_id", length = 64, unique = true)
	private String paypalOrderId;

	@Column(name = "paypal_capture_id", length = 64, unique = true)
	private String paypalCaptureId;

	@Column(name = "failure_code", length = 64)
	private String failureCode;

	protected RentPayment() { }

	public RentPayment(UUID id, UUID scheduleId, LocalDate dueDate, BigDecimal amount, String idempotencyKey) {
		this.id = id;
		this.scheduleId = scheduleId;
		this.dueDate = dueDate;
		this.amount = amount;
		this.idempotencyKey = idempotencyKey;
		this.status = RentPaymentStatus.PROCESSING;
	}

	public UUID getId() { return id; }
	public UUID getScheduleId() { return scheduleId; }
	public LocalDate getDueDate() { return dueDate; }
	public BigDecimal getAmount() { return amount; }
	public RentPaymentStatus getStatus() { return status; }
	public String getPaypalOrderId() { return paypalOrderId; }
	public String getPaypalCaptureId() { return paypalCaptureId; }
	public String getFailureCode() { return failureCode; }

	public void recordExternalIds(String orderId, String captureId) {
		if (orderId != null && !orderId.isBlank()) this.paypalOrderId = orderId;
		if (captureId != null && !captureId.isBlank()) this.paypalCaptureId = captureId;
	}

	public void complete(String orderId, String captureId) {
		recordExternalIds(orderId, captureId);
		this.status = RentPaymentStatus.COMPLETED;
		this.failureCode = null;
	}

	public void fail(String failureCode) {
		this.status = RentPaymentStatus.FAILED;
		this.failureCode = failureCode;
	}

	public void markRefunded() {
		this.status = RentPaymentStatus.REFUNDED;
	}
}
