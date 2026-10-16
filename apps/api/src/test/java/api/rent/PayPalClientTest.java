package api.rent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PayPalClientTest {

	private static final String SANDBOX = "https://api-m.sandbox.paypal.com";

	@Test
	void createsARecurringSetupTokenForTheTenantToApprove() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PayPalClient client = new PayPalClient(builder, properties(), JsonMapper.builder().build());
		expectAccessToken(server);
		server.expect(requestTo(SANDBOX + "/v3/vault/setup-tokens"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.payment_source.paypal.usage_pattern").value("SUBSCRIPTION_PREPAID"))
				.andExpect(jsonPath("$.payment_source.paypal.usage_type").value("MERCHANT"))
				.andExpect(jsonPath("$.payment_source.paypal.billing_plan.billing_cycles[0].frequency.interval_unit").value("MONTH"))
				.andExpect(jsonPath("$.payment_source.paypal.billing_plan.billing_cycles[0].pricing_scheme.price.value").value("425.00"))
				.andExpect(jsonPath("$.payment_source.paypal.billing_plan.billing_cycles[0].start_date").value("2026-11-01"))
				.andRespond(withSuccess("""
					{"id":"setup-demo","links":[{"rel":"approve","href":"https://www.sandbox.paypal.com/checkoutnow?token=setup-demo"}]}
					""", MediaType.APPLICATION_JSON));

		PayPalClient.SetupToken setup = client.createSetupToken(UUID.fromString("f216e512-81d4-4f50-a0fc-317952e91f15"),
				Frequency.MONTHLY, new BigDecimal("425.00"), LocalDate.parse("2026-11-01"));

		assertThat(setup.id()).isEqualTo("setup-demo");
		assertThat(setup.approvalUrl()).isEqualTo("https://www.sandbox.paypal.com/checkoutnow?token=setup-demo");
		server.verify();
	}

	@Test
	void sendsTheOriginalWebhookEventJsonToPayPalSignatureVerification() throws Exception {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PayPalClient client = new PayPalClient(builder, webhookProperties(), JsonMapper.builder().build());
		expectAccessToken(server);
		String eventJson = "{\n  \"id\": \"event-demo\",\n  \"event_type\": \"PAYMENT.CAPTURE.COMPLETED\"\n}";
		server.expect(requestTo(SANDBOX + "/v1/notifications/verify-webhook-signature"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string(containsString("\"webhook_event\":" + eventJson)))
				.andRespond(withSuccess("{\"verification_status\":\"SUCCESS\"}", MediaType.APPLICATION_JSON));

		assertThat(client.verifyWebhook(Map.of(
				"paypal-transmission-id", "transmission-demo",
				"paypal-transmission-time", "2026-10-15T12:00:00Z",
				"paypal-cert-url", "https://api-m.sandbox.paypal.com/cert",
				"paypal-auth-algo", "SHA256withRSA",
				"paypal-transmission-sig", "signature-demo"), eventJson.getBytes(java.nio.charset.StandardCharsets.UTF_8))).isTrue();
		server.verify();
	}

	@Test
	void chargesAStoredTokenInOneSandboxOrderRequestAndReadsItsCapture() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		PayPalClient client = new PayPalClient(builder, properties(), JsonMapper.builder().build());
		expectAccessToken(server);
		server.expect(requestTo(SANDBOX + "/v2/checkout/orders"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(jsonPath("$.intent").value("CAPTURE"))
				.andExpect(jsonPath("$.payment_source.paypal.vault_id").value("vault-demo"))
				.andExpect(jsonPath("$.payment_source.paypal.stored_credential.payment_initiator").value("MERCHANT"))
				.andExpect(jsonPath("$.payment_source.paypal.stored_credential.usage").value("SUBSEQUENT"))
				.andExpect(jsonPath("$.payment_source.paypal.stored_credential.usage_pattern").value("SUBSCRIPTION_PREPAID"))
				.andExpect(jsonPath("$.purchase_units[0].amount.value").value("425.00"))
				.andRespond(withSuccess("""
					{"id":"order-demo","status":"COMPLETED","purchase_units":[{"payments":{"captures":[{"id":"capture-demo","status":"COMPLETED"}]}}]}
					""", MediaType.APPLICATION_JSON));

		PayPalClient.ChargeResult result = client.captureRent("vault-demo", new BigDecimal("425.00"),
				"rent-invoice-demo", "rent-request-demo");

		assertThat(result).isEqualTo(new PayPalClient.ChargeResult("order-demo", "capture-demo", "COMPLETED", "COMPLETED"));
		server.verify();
	}

	private void expectAccessToken(MockRestServiceServer server) {
		server.expect(requestTo(SANDBOX + "/v1/oauth2/token"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(content().string("grant_type=client_credentials"))
				.andRespond(withSuccess("{\"access_token\":\"sandbox-token\",\"expires_in\":300}", MediaType.APPLICATION_JSON));
	}

	private PayPalProperties properties() {
		return new PayPalProperties("sandbox-client-id", "sandbox-client-secret", "", false,
				"http://localhost:8080/api/v1/rent-schedules/paypal/return",
				"http://localhost:3000/rent/paypal/return?status=cancelled", "http://localhost:3000");
	}

	private PayPalProperties webhookProperties() {
		return new PayPalProperties("sandbox-client-id", "sandbox-client-secret", "webhook-demo", false,
				"http://localhost:8080/api/v1/rent-schedules/paypal/return",
				"http://localhost:3000/rent/paypal/return?status=cancelled", "http://localhost:3000");
	}
}
