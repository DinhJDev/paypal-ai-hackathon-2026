package api.rent;

import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RentPaymentScheduler {

	private static final ZoneId BUSINESS_ZONE = ZoneId.of("America/Chicago");
	private final RentScheduleService schedules;
	private final PayPalClient paypal;
	private final PayPalProperties properties;

	public RentPaymentScheduler(RentScheduleService schedules, PayPalClient paypal, PayPalProperties properties) {
		this.schedules = schedules;
		this.paypal = paypal;
		this.properties = properties;
	}

	@Scheduled(cron = "0 0 9 * * *", zone = "America/Chicago")
	public void runDuePayments() {
		if (!properties.autopayEnabled() || !paypal.isConfigured()) return;
		schedules.processDuePayments(LocalDate.now(BUSINESS_ZONE));
	}
}
