package api.rent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record RentScheduleView(UUID id, String tenantLabel, BigDecimal amount, String currency,
		Frequency frequency, LocalDate nextDueDate, RentScheduleStatus status, String approvalUrl,
		List<RentPaymentView> payments) { }
