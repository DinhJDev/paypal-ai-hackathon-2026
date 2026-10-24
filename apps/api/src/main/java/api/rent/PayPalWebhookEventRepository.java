package api.rent;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PayPalWebhookEventRepository extends JpaRepository<PayPalWebhookEvent, String> {
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value="INSERT INTO paypal_webhook_event (id,event_type,resource_id) VALUES (:id,:type,:resource) ON CONFLICT (id) DO NOTHING",nativeQuery=true)
    int recordOnce(@org.springframework.data.repository.query.Param("id") String id,
        @org.springframework.data.repository.query.Param("type") String type,
        @org.springframework.data.repository.query.Param("resource") String resource); }
