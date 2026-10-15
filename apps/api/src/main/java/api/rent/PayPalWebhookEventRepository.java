package api.rent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayPalWebhookEventRepository extends JpaRepository<PayPalWebhookEvent, String> { }
