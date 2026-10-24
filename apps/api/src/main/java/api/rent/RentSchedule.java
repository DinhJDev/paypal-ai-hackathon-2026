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
@Table(name = "rent_schedule")
public class RentSchedule {

	@Id
	@Column(nullable = false, updatable = false)
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID id;

	@Column(name = "owner_id", nullable = false, updatable = false)
	@JdbcTypeCode(SqlTypes.UUID)
	private UUID ownerId;

	@Column(name = "tenant_label", nullable = false, length = 120)
	private String tenantLabel;

	@Column(nullable = false, precision = 10, scale = 2)
	private BigDecimal amount;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Frequency frequency;

	@Column(name = "next_due_date", nullable = false)
	private LocalDate nextDueDate;

	@Column(name = "monthly_anchor_day", nullable = false)
	private int monthlyAnchorDay;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 24)
	private RentScheduleStatus status;

	@Column(name = "paypal_setup_token_id", length = 64, unique = true)
	private String paypalSetupTokenId;

	@Column(name = "paypal_payment_token_id", length = 255)
	private String paypalPaymentTokenId;

	@Column(name = "approval_url", columnDefinition = "text")
	private String approvalUrl;

	protected RentSchedule() { }

	public RentSchedule(UUID id, UUID ownerId, String tenantLabel, BigDecimal amount, Frequency frequency,
			LocalDate nextDueDate) {
		this.id = id;
		this.ownerId = ownerId;
		this.tenantLabel = tenantLabel;
		this.amount = amount;
		this.frequency = frequency;
		this.nextDueDate = nextDueDate;
		this.monthlyAnchorDay = nextDueDate.getDayOfMonth();
		this.status = RentScheduleStatus.SCHEDULED;
	}

	public UUID getId() { return id; }
	public UUID getOwnerId() { return ownerId; }
	public String getTenantLabel() { return tenantLabel; }
	public BigDecimal getAmount() { return amount; }
	public Frequency getFrequency() { return frequency; }
	public LocalDate getNextDueDate() { return nextDueDate; }
	public int getMonthlyAnchorDay() { return monthlyAnchorDay; }
	public RentScheduleStatus getStatus() { return status; }
	public String getPaypalSetupTokenId() { return paypalSetupTokenId; }
	public String getPaypalPaymentTokenId() { return paypalPaymentTokenId; }
	public String getApprovalUrl() { return approvalUrl; }

	public void attachSetupToken(String setupTokenId, String approvalUrl) {
		this.paypalSetupTokenId = setupTokenId;
		this.approvalUrl = approvalUrl;
		this.status = RentScheduleStatus.PENDING_APPROVAL;
	}

	public void markSetupFailed() {
		this.status = RentScheduleStatus.SETUP_FAILED;
	}

	public void activate(String paymentTokenId) {
		this.paypalPaymentTokenId = paymentTokenId;
		this.status = RentScheduleStatus.ACTIVE;
		this.approvalUrl = null;
	}

	public void pause() {
		this.status = RentScheduleStatus.PAUSED;
	}

	public void advanceDueDate() {
		this.nextDueDate = RentScheduleDates.nextDueDate(frequency, nextDueDate, monthlyAnchorDay);
	}

	public void markPaymentFailed() {
		this.status = RentScheduleStatus.PAYMENT_FAILED;
	}

	public void markPastDue() {
		this.status = RentScheduleStatus.PAST_DUE;
	}
}
