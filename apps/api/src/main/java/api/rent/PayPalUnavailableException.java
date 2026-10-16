package api.rent;

public class PayPalUnavailableException extends RuntimeException {

	public PayPalUnavailableException() {
		super("PayPal sandbox is unavailable or not configured.");
	}
}
