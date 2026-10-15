package api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import api.rent.Frequency;
import api.rent.PayPalClient;
import api.rent.RentPaymentScheduler;
import api.rent.RentScheduleService;
import fixture.JwtFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles({"test", "local"})
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.password=test-only-password")
@AutoConfigureMockMvc
class RentAutopayIntegrationTests extends JwtTestSupport {

	private static final String OWNER_SUBJECT = "auth0|rent-test-owner";
	private static final UUID OWNER_ID = UUID.fromString("f216e512-81d4-4f50-a0fc-317952e91f15");

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;
	@Autowired RentScheduleService schedules;
	@Autowired RentPaymentScheduler scheduler;
	@MockitoBean PayPalClient paypal;

	@BeforeEach
	void resetRentData() {
		jdbc.update("DELETE FROM paypal_webhook_event");
		jdbc.update("DELETE FROM rent_payment");
		jdbc.update("DELETE FROM rent_schedule");
		jdbc.update("DELETE FROM owner_access");
		jdbc.update("INSERT INTO owner_access (id, issuer, subject, enabled) VALUES (?, ?, ?, true)",
				OWNER_ID, JwtFixture.ISSUER, OWNER_SUBJECT);
		clearInvocations(paypal);
	}

	@Test
	void ownerCanCreateRentScheduleAndGetTenantApprovalLinkWithoutCharging() throws Exception {
		when(paypal.createSetupToken(any(UUID.class), eq(Frequency.WEEKLY), eq(new BigDecimal("425.00")), any(LocalDate.class)))
				.thenReturn(new PayPalClient.SetupToken("setup-demo", "https://www.sandbox.paypal.com/approve/demo"));

		mvc.perform(post("/api/v1/rent-schedules")
				.header("Authorization", "Bearer " + JwtFixture.token(OWNER_SUBJECT))
				.contentType("application/json")
				.content("""
					{"tenantLabel":"Demo suite 1","amount":425.00,"frequency":"WEEKLY","nextDueDate":"%s"}
					""".formatted(LocalDate.now().plusDays(3))))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.status").value("PENDING_APPROVAL"))
			.andExpect(jsonPath("$.approvalUrl").value("https://www.sandbox.paypal.com/approve/demo"))
			.andExpect(jsonPath("$.frequency").value("WEEKLY"))
			.andExpect(jsonPath("$.paypalPaymentToken").doesNotExist());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM rent_schedule WHERE owner_id = ?", Integer.class, OWNER_ID))
				.isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT paypal_setup_token_id FROM rent_schedule WHERE owner_id = ?", String.class, OWNER_ID))
				.isEqualTo("setup-demo");
		assertThat(jdbc.queryForObject("SELECT approval_url FROM rent_schedule WHERE owner_id = ?", String.class, OWNER_ID))
				.isEqualTo("https://www.sandbox.paypal.com/approve/demo");
		verify(paypal, never()).captureRent(anyString(), any(), anyString(), anyString());
	}

	@Test
	void paypalReturnExchangesOnlyARecordedApprovedSetupToken() throws Exception {
		when(paypal.createSetupToken(any(UUID.class), any(), any(), any(LocalDate.class)))
				.thenReturn(new PayPalClient.SetupToken("setup-demo", "https://www.sandbox.paypal.com/approve/demo"));
		when(paypal.getSetupTokenStatus("setup-demo")).thenReturn("APPROVED");
		when(paypal.createPaymentToken(eq("setup-demo"), any(UUID.class))).thenReturn("vault-demo");
		createSchedule();

		mvc.perform(get("/api/v1/rent-schedules/paypal/return").param("token", "setup-demo"))
				.andExpect(status().isFound())
				.andExpect(header().string("Location", "http://localhost:3000/rent/paypal/return?status=approved"));

		assertThat(jdbc.queryForObject("SELECT status FROM rent_schedule WHERE paypal_setup_token_id = ?", String.class, "setup-demo"))
				.isEqualTo("ACTIVE");
		verify(paypal).createPaymentToken(eq("setup-demo"), any(UUID.class));
	}

	@Test
	void scheduledChargeAdvancesRentOnceAndIsIdempotentForTheDueDate() {
		when(paypal.captureRent(eq("vault-demo"), eq(new BigDecimal("425.00")), anyString(), anyString()))
				.thenReturn(new PayPalClient.ChargeResult("order-demo", "capture-demo", "COMPLETED", "COMPLETED"));
		createActiveDueSchedule();

		schedules.processDuePayments(LocalDate.now());
		schedules.processDuePayments(LocalDate.now());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM rent_payment", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT status FROM rent_payment", String.class)).isEqualTo("COMPLETED");
		assertThat(jdbc.queryForObject("SELECT next_due_date FROM rent_schedule", LocalDate.class))
				.isEqualTo(LocalDate.now().plusWeeks(1));
		verify(paypal).captureRent(eq("vault-demo"), eq(new BigDecimal("425.00")), anyString(), anyString());
	}

	@Test
	void failedDueChargeMarksPaymentAndScheduleForReview() {
		UUID scheduleId = createActiveDueSchedule();
		LocalDate dueDate = LocalDate.now();
		when(paypal.captureRent(eq("vault-demo"), eq(new BigDecimal("425.00")), anyString(), anyString()))
				.thenThrow(new api.rent.PayPalUnavailableException());

		schedules.processDuePayments(dueDate);

		assertThat(jdbc.queryForObject("SELECT status FROM rent_payment WHERE schedule_id = ?", String.class, scheduleId))
				.isEqualTo("FAILED");
		assertThat(jdbc.queryForObject("SELECT failure_code FROM rent_payment WHERE schedule_id = ?", String.class, scheduleId))
				.isEqualTo("PAYPAL_UNAVAILABLE");
		assertThat(jdbc.queryForObject("SELECT status FROM rent_schedule WHERE id = ?", String.class, scheduleId))
				.isEqualTo("PAYMENT_FAILED");
		assertThat(jdbc.queryForObject("SELECT next_due_date FROM rent_schedule WHERE id = ?", LocalDate.class, scheduleId))
				.isEqualTo(dueDate);
		verify(paypal).captureRent(eq("vault-demo"), eq(new BigDecimal("425.00")), anyString(), anyString());
	}
	@Test
	void autopaySchedulerDoesNothingUntilExplicitlyEnabled() {
		createActiveDueSchedule();

		scheduler.runDuePayments();

		verify(paypal, never()).captureRent(anyString(), any(), anyString(), anyString());
		assertThat(jdbc.queryForObject("SELECT count(*) FROM rent_payment", Integer.class)).isZero();
	}

	@Test
	void webhookRequiresPayPalSignatureBeforeRecordingEvents() throws Exception {
		mvc.perform(post("/api/v1/rent-schedules/paypal/webhook")
				.header("paypal-transmission-id", "transmission-demo")
				.header("paypal-transmission-time", "2026-10-15T12:00:00Z")
				.header("paypal-cert-url", "https://api-m.sandbox.paypal.com/cert")
				.header("paypal-auth-algo", "SHA256withRSA")
				.header("paypal-transmission-sig", "signature-demo")
				.contentType("application/json")
				.content("""
					{"id":"event-demo","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"capture-demo"}}
					"""))
			.andExpect(status().isUnauthorized());

		assertThat(jdbc.queryForObject("SELECT count(*) FROM paypal_webhook_event", Integer.class)).isZero();
		verify(paypal).verifyWebhook(any(), any());
	}

	@Test
	void verifiedWebhookReconcilesALateCaptureOnceByInvoiceId() throws Exception {
		UUID scheduleId = createActiveDueSchedule();
		jdbc.update("UPDATE rent_schedule SET status = 'PAYMENT_FAILED' WHERE id = ?", scheduleId);
		String idempotencyKey = "rent-" + scheduleId + "-" + LocalDate.now();
		jdbc.update("""
			INSERT INTO rent_payment (id, schedule_id, due_date, amount, status, idempotency_key, failure_code)
			VALUES (?, ?, ?, 425.00, 'FAILED', ?, 'PAYPAL_UNAVAILABLE')
			""", UUID.randomUUID(), scheduleId, LocalDate.now(), idempotencyKey);
		when(paypal.verifyWebhook(any(), any())).thenReturn(true);
		String event = """
			{"id":"event-late-capture","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"capture-late","invoice_id":"%s"}}
			""".formatted(idempotencyKey);

		for (int attempt = 0; attempt < 2; attempt++) {
			mvc.perform(post("/api/v1/rent-schedules/paypal/webhook")
					.header("paypal-transmission-id", "transmission-demo")
					.header("paypal-transmission-time", "2026-10-15T12:00:00Z")
					.header("paypal-cert-url", "https://api-m.sandbox.paypal.com/cert")
					.header("paypal-auth-algo", "SHA256withRSA")
					.header("paypal-transmission-sig", "signature-demo")
					.contentType("application/json").content(event))
				.andExpect(status().isAccepted());
		}

		assertThat(jdbc.queryForObject("SELECT count(*) FROM paypal_webhook_event", Integer.class)).isEqualTo(1);
		assertThat(jdbc.queryForObject("SELECT status FROM rent_payment", String.class)).isEqualTo("COMPLETED");
		assertThat(jdbc.queryForObject("SELECT next_due_date FROM rent_schedule", LocalDate.class))
				.isEqualTo(LocalDate.now().plusWeeks(1));
		assertThat(jdbc.queryForObject("SELECT status FROM rent_schedule", String.class)).isEqualTo("PAYMENT_FAILED");
	}

	@Test
	void rentRoutesRequireEnabledOwnerIdentity() throws Exception {
		mvc.perform(get("/api/v1/rent-schedules")).andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/rent-schedules")
				.header("Authorization", "Bearer " + JwtFixture.token("auth0|not-enabled")))
			.andExpect(status().isForbidden());
	}

	private void createSchedule() {
		jdbc.update("""
			INSERT INTO rent_schedule (id, owner_id, tenant_label, amount, frequency, next_due_date,
			 monthly_anchor_day, status, paypal_setup_token_id, approval_url)
			VALUES (?, ?, 'Demo suite 1', 425.00, 'WEEKLY', ?, 15, 'PENDING_APPROVAL', 'setup-demo',
			 'https://www.sandbox.paypal.com/approve/demo')
			""", UUID.randomUUID(), OWNER_ID, LocalDate.now().plusDays(3));
	}

	private UUID createActiveDueSchedule() {
		UUID id = UUID.randomUUID();
		jdbc.update("""
			INSERT INTO rent_schedule (id, owner_id, tenant_label, amount, frequency, next_due_date,
			 monthly_anchor_day, status, paypal_setup_token_id, paypal_payment_token_id)
			VALUES (?, ?, 'Demo suite 1', 425.00, 'WEEKLY', ?, 15, 'ACTIVE', 'setup-demo', 'vault-demo')
			""", id, OWNER_ID, LocalDate.now());
		return id;
	}
}
