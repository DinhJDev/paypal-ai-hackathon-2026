package com.suitefolk.api.rent;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** PayPal REST client pinned to the sandbox host. There is intentionally no live-mode option. */
@Component
public class PayPalClient {

	private static final String SANDBOX_API = "https://api-m.sandbox.paypal.com";
	private final RestClient client;
	private final PayPalProperties properties;
	private final ObjectMapper objectMapper;
	private volatile CachedAccessToken cachedAccessToken;

	public PayPalClient(RestClient.Builder builder, PayPalProperties properties, ObjectMapper objectMapper) {
		this.client = builder.baseUrl(SANDBOX_API).build();
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	public record SetupToken(String id, String approvalUrl) { }
	public record ChargeResult(String orderId, String captureId, String orderStatus, String captureStatus) { }

	private record OAuthResponse(String access_token, long expires_in) { }
	private record CachedAccessToken(String value, Instant expiresAt) { }

	public boolean isConfigured() { return properties.configured(); }
	public boolean isAutopayEnabled() { return properties.configured() && properties.autopayEnabled(); }

	public SetupToken createSetupToken(UUID scheduleId, Frequency frequency, BigDecimal amount, LocalDate nextDueDate) {
		return call(() -> {
			Map<String, Object> frequencyBody = Map.of(
					"interval_unit", frequency == Frequency.WEEKLY ? "WEEK" : "MONTH",
					"interval_count", 1);
			Map<String, Object> price = Map.of("currency_code", "USD", "value", amount.setScale(2).toPlainString());
			Map<String, Object> cycle = Map.of(
					"frequency", frequencyBody,
					"tenure_type", "REGULAR",
					"sequence", 1,
					"total_cycles", 0,
					"start_date", nextDueDate.toString(),
					"pricing_scheme", Map.of("pricing_model", "FIXED", "price", price));
			Map<String, Object> charges = Map.of("total_amount", Map.of("currency_code", "USD", "value", "0.00"));
			Map<String, Object> billingPlan = Map.of(
					"name", frequency == Frequency.WEEKLY ? "Suitefolk Weekly Rent" : "Suitefolk Monthly Rent",
					"billing_cycles", List.of(cycle),
					"one_time_charges", charges);
			Map<String, Object> experience = Map.of(
					"return_url", properties.returnUrl(),
					"cancel_url", properties.cancelUrl(),
					"shipping_preference", "NO_SHIPPING",
					"user_action", "CONTINUE");
			Map<String, Object> paypalWallet = Map.of(
					"description", "Suitefolk rent autopay",
					"usage_pattern", "SUBSCRIPTION_PREPAID",
					"usage_type", "MERCHANT",
					"customer_type", "CONSUMER",
					"billing_plan", billingPlan,
					"experience_context", experience);
			JsonNode response = client.post().uri("/v3/vault/setup-tokens")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
				.header("PayPal-Request-Id", "rent-setup-" + scheduleId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("payment_source", Map.of("paypal", paypalWallet)))
				.retrieve().body(JsonNode.class);
			if (response == null || response.path("id").asText().isBlank()) {
				throw new PayPalUnavailableException();
			}
			String approvalUrl = null;
			for (JsonNode link : response.path("links")) {
				if ("approve".equals(link.path("rel").asText())) {
					approvalUrl = safeApprovalUrl(link.path("href").asText());
					break;
				}
			}
			if (approvalUrl == null) throw new PayPalUnavailableException();
			return new SetupToken(response.path("id").asText(), approvalUrl);
		});
	}

	public String getSetupTokenStatus(String setupTokenId) {
		return call(() -> {
			JsonNode response = client.get().uri("/v3/vault/setup-tokens/{id}", setupTokenId)
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
					.retrieve().body(JsonNode.class);
			if (response == null) throw new PayPalUnavailableException();
			return response.path("status").asText("");
		});
	}

	public String createPaymentToken(String setupTokenId, UUID scheduleId) {
		return call(() -> {
			JsonNode response = client.post().uri("/v3/vault/payment-tokens")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
				.header("PayPal-Request-Id", "rent-token-" + scheduleId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("payment_source", Map.of("token", Map.of("id", setupTokenId, "type", "SETUP_TOKEN"))))
				.retrieve().body(JsonNode.class);
			if (response == null || response.path("id").asText().isBlank()) {
				throw new PayPalUnavailableException();
			}
			return response.path("id").asText();
		});
	}

	public ChargeResult captureRent(String paymentTokenId, BigDecimal amount, String invoiceId, String requestId) {
		return call(() -> {
			Map<String, Object> storedCredential = Map.of(
					"payment_initiator", "MERCHANT",
					"usage", "SUBSEQUENT",
					"usage_pattern", "SUBSCRIPTION_PREPAID");
			Map<String, Object> paypalWallet = Map.of(
					"vault_id", paymentTokenId,
					"stored_credential", storedCredential);
			Map<String, Object> purchaseUnit = Map.of(
					"invoice_id", invoiceId,
					"description", "Suitefolk rent payment",
					"amount", Map.of("currency_code", "USD", "value", amount.setScale(2).toPlainString()));
			JsonNode response = client.post().uri("/v2/checkout/orders")
				.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
				.header("PayPal-Request-Id", requestId)
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("intent", "CAPTURE", "purchase_units", List.of(purchaseUnit),
						"payment_source", Map.of("paypal", paypalWallet)))
				.retrieve().body(JsonNode.class);
			if (response == null) throw new PayPalUnavailableException();
			JsonNode captures = response.path("purchase_units").path(0).path("payments").path("captures");
			JsonNode capture = captures.isArray() && !captures.isEmpty() ? captures.path(0) : null;
			return new ChargeResult(response.path("id").asText(""),
					capture == null ? "" : capture.path("id").asText(""),
					response.path("status").asText(""),
					capture == null ? "" : capture.path("status").asText(""));
		});
	}

	public boolean verifyWebhook(Map<String, String> transmissionHeaders, byte[] rawEventJson) {
		if (!properties.configured() || properties.webhookId() == null || properties.webhookId().isBlank() ||
				rawEventJson == null || rawEventJson.length == 0) return false;
		return call(() -> {
			Map<String, Object> request = new LinkedHashMap<>();
			request.put("transmission_id", transmissionHeaders.get("paypal-transmission-id"));
			request.put("transmission_time", transmissionHeaders.get("paypal-transmission-time"));
			request.put("cert_url", transmissionHeaders.get("paypal-cert-url"));
			request.put("auth_algo", transmissionHeaders.get("paypal-auth-algo"));
			request.put("transmission_sig", transmissionHeaders.get("paypal-transmission-sig"));
			request.put("webhook_id", properties.webhookId());
			byte[] metadata;
			try {
				metadata = objectMapper.writeValueAsBytes(request);
			} catch (JacksonException exception) {
				throw new PayPalUnavailableException();
			}
			if (metadata.length == 0 || metadata[metadata.length - 1] != '}') throw new PayPalUnavailableException();
			byte[] separator = ",\"webhook_event\":".getBytes(StandardCharsets.UTF_8);
			ByteArrayOutputStream body = new ByteArrayOutputStream(metadata.length + separator.length + rawEventJson.length + 1);
			body.write(metadata, 0, metadata.length - 1);
			body.writeBytes(separator);
			body.writeBytes(rawEventJson);
			body.write('}');
			JsonNode response = client.post().uri("/v1/notifications/verify-webhook-signature")
					.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken())
					.contentType(MediaType.APPLICATION_JSON).body(body.toByteArray()).retrieve().body(JsonNode.class);
			return response != null && "SUCCESS".equals(response.path("verification_status").asText());
		});
	}

	private String accessToken() {
		if (!properties.configured()) throw new PayPalUnavailableException();
		CachedAccessToken cached = cachedAccessToken;
		if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) return cached.value();
		synchronized (this) {
			cached = cachedAccessToken;
			if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(30))) return cached.value();
			OAuthResponse response = client.post().uri("/v1/oauth2/token")
					.headers(headers -> headers.setBasicAuth(properties.clientId(), properties.clientSecret(), StandardCharsets.UTF_8))
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body("grant_type=client_credentials").retrieve().body(OAuthResponse.class);
			if (response == null || response.access_token() == null || response.access_token().isBlank()) {
				throw new PayPalUnavailableException();
			}
			long lifetime = Math.max(60, response.expires_in() - 60);
			cachedAccessToken = new CachedAccessToken(response.access_token(), Instant.now().plusSeconds(lifetime));
			return cachedAccessToken.value();
		}
	}

	private String safeApprovalUrl(String value) {
		try {
			URI uri = URI.create(value);
			String host = uri.getHost();
			if ("https".equalsIgnoreCase(uri.getScheme()) && uri.getUserInfo() == null &&
					(uri.getPort() == -1 || uri.getPort() == 443) && host != null &&
					(host.equalsIgnoreCase("sandbox.paypal.com") || host.toLowerCase().endsWith(".sandbox.paypal.com"))) {
				return uri.toString();
			}
		} catch (IllegalArgumentException ignored) { }
		throw new PayPalUnavailableException();
	}

	private <T> T call(Supplier<T> action) {
		try {
			return action.get();
		} catch (PayPalUnavailableException exception) {
			throw exception;
		} catch (RestClientException | IllegalArgumentException exception) {
			throw new PayPalUnavailableException();
		}
	}
}
