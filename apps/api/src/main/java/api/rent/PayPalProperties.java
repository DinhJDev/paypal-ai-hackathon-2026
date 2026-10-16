package api.rent;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "paypal")
public record PayPalProperties(String clientId, String clientSecret, String webhookId,
		boolean autopayEnabled, String returnUrl, String cancelUrl, String webBaseUrl) {

	public boolean configured() {
		return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
	}
}
