package com.suitefolk.api.rent;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

@Entity
@Table(name = "paypal_webhook_event")
public class PayPalWebhookEvent {

	@Id
	@Column(nullable = false, length = 128, updatable = false)
	private String id;

	@Column(name = "event_type", nullable = false, length = 128, updatable = false)
	private String eventType;

	@Column(name = "resource_id", length = 128, updatable = false)
	private String resourceId;

	@CreationTimestamp
	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;

	protected PayPalWebhookEvent() { }

	public PayPalWebhookEvent(String id, String eventType, String resourceId) {
		this.id = id;
		this.eventType = eventType;
		this.resourceId = resourceId;
	}
}
