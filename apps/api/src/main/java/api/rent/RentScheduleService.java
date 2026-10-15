package api.rent;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import api.identity.OwnerIdentity;
import api.identity.OwnerIdentityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;
import tools.jackson.databind.JsonNode;

@Service
public class RentScheduleService {

	private final RentScheduleRepository schedules;
	private final RentPaymentRepository payments;
	private final PayPalWebhookEventRepository webhookEvents;
	private final OwnerIdentityService identities;
	private final PayPalClient paypal;

	public RentScheduleService(RentScheduleRepository schedules, RentPaymentRepository payments,
			PayPalWebhookEventRepository webhookEvents, OwnerIdentityService identities, PayPalClient paypal) {
		this.schedules = schedules;
		this.payments = payments;
		this.webhookEvents = webhookEvents;
		this.identities = identities;
		this.paypal = paypal;
	}

	@Transactional
	public RentScheduleView create(Jwt jwt, CreateRentScheduleRequest request) {
		OwnerIdentity owner = identities.requireOwner(jwt);
		RentSchedule schedule = new RentSchedule(UUID.randomUUID(), owner.ownerId(), request.tenantLabel().trim(),
				request.amount(), request.frequency(), request.nextDueDate());
		schedule = schedules.save(schedule);
		PayPalClient.SetupToken setup = paypal.createSetupToken(schedule.getId(), schedule.getFrequency(),
				schedule.getAmount(), schedule.getNextDueDate());
		schedule.attachSetupToken(setup.id(), setup.approvalUrl());
		return view(schedule);
	}

	@Transactional(readOnly = true)
	public List<RentScheduleView> list(Jwt jwt) {
		UUID ownerId = identities.requireOwner(jwt).ownerId();
		return schedules.findAllByOwnerIdOrderByNextDueDateAsc(ownerId).stream().map(this::view).toList();
	}

	@Transactional(readOnly = true)
	public RentScheduleView get(Jwt jwt, UUID scheduleId) {
		return view(requireOwned(jwt, scheduleId));
	}

	@Transactional
	public RentScheduleView checkApproval(Jwt jwt, UUID scheduleId) {
		RentSchedule schedule = requireOwned(jwt, scheduleId);
		if (schedule.getPaypalSetupTokenId() != null) completeApproval(schedule.getPaypalSetupTokenId());
		return view(schedule);
	}

	@Transactional
	public RentScheduleView pause(Jwt jwt, UUID scheduleId) {
		RentSchedule schedule = requireOwned(jwt, scheduleId);
		if (schedule.getStatus() == RentScheduleStatus.ACTIVE || schedule.getStatus() == RentScheduleStatus.PENDING_APPROVAL) {
			schedule.pause();
		}
		return view(schedule);
	}

	@Transactional
	public boolean completeApproval(String setupTokenId) {
		if (setupTokenId == null || setupTokenId.isBlank()) return false;
		RentSchedule located = schedules.findByPaypalSetupTokenId(setupTokenId).orElse(null);
		if (located == null) return false;
		RentSchedule schedule = schedules.findByIdForUpdate(located.getId()).orElse(null);
		if (schedule == null || !setupTokenId.equals(schedule.getPaypalSetupTokenId())) return false;
		if (schedule.getStatus() == RentScheduleStatus.ACTIVE) return true;
		if (schedule.getStatus() != RentScheduleStatus.PENDING_APPROVAL) return false;
		if (!"APPROVED".equals(paypal.getSetupTokenStatus(setupTokenId))) return false;
		String paymentToken = paypal.createPaymentToken(setupTokenId, schedule.getId());
		schedule.activate(paymentToken);
		return true;
	}

	@Transactional
	public void processDuePayments(LocalDate today) {
		for (UUID id : schedules.findDueIds(RentScheduleStatus.ACTIVE, today)) {
			processDuePayment(id, today);
		}
	}

	private void processDuePayment(UUID id, LocalDate today) {
		RentSchedule schedule = schedules.findByIdForUpdate(id).orElse(null);
		if (schedule == null || schedule.getStatus() != RentScheduleStatus.ACTIVE || schedule.getNextDueDate().isAfter(today)) {
			return;
		}
		LocalDate dueDate = schedule.getNextDueDate();
		String idempotencyKey = "rent-" + schedule.getId() + "-" + dueDate;
		RentPayment payment = payments.findByScheduleIdAndDueDate(schedule.getId(), dueDate).orElse(null);
		if (payment != null && payment.getStatus() != RentPaymentStatus.PROCESSING) return;
		if (payment == null) {
			payment = payments.saveAndFlush(new RentPayment(UUID.randomUUID(), schedule.getId(), dueDate,
					schedule.getAmount(), idempotencyKey));
		}
		try {
			PayPalClient.ChargeResult result = paypal.captureRent(schedule.getPaypalPaymentTokenId(), schedule.getAmount(),
					idempotencyKey, idempotencyKey);
			if ("COMPLETED".equals(result.orderStatus()) && "COMPLETED".equals(result.captureStatus()) &&
					!result.orderId().isBlank() && !result.captureId().isBlank()) {
				payment.complete(result.orderId(), result.captureId());
				schedule.advanceDueDate();
			} else {
				payment.recordExternalIds(result.orderId(), result.captureId());
				payment.fail("PAYMENT_NOT_COMPLETED");
				schedule.markPaymentFailed();
			}
		} catch (PayPalUnavailableException exception) {
			payment.fail("PAYPAL_UNAVAILABLE");
			schedule.markPaymentFailed();
		}
	}

	@Transactional
	public boolean recordVerifiedWebhook(JsonNode event) {
		String id = event.path("id").asText("");
		String type = event.path("event_type").asText("");
		JsonNode resource = event.path("resource");
		String resourceId = resource.path("id").asText("");
		if (id.isBlank() || type.isBlank() || id.length() > 128 || type.length() > 128 || resourceId.length() > 128) return false;
		if (webhookEvents.existsById(id)) return true;
		webhookEvents.saveAndFlush(new PayPalWebhookEvent(id, type, resourceId.isBlank() ? null : resourceId));
		String orderId = resource.path("supplementary_data").path("related_ids").path("order_id").asText("");
		String invoiceId = resource.path("invoice_id").asText("");
		RentPayment payment = orderId.isBlank() ? null : payments.findByPaypalOrderId(orderId).orElse(null);
		if (payment == null && !resourceId.isBlank()) payment = payments.findByPaypalCaptureId(resourceId).orElse(null);
		if (payment == null && !invoiceId.isBlank()) payment = payments.findByIdempotencyKey(invoiceId).orElse(null);
		if (payment == null) return true;
		switch (type) {
			case "PAYMENT.CAPTURE.COMPLETED" -> {
				boolean alreadyCompleted = payment.getStatus() == RentPaymentStatus.COMPLETED;
				payment.complete(orderId.isBlank() ? payment.getPaypalOrderId() : orderId,
						resourceId.isBlank() ? payment.getPaypalCaptureId() : resourceId);
				if (!alreadyCompleted) {
					RentSchedule schedule = schedules.findByIdForUpdate(payment.getScheduleId()).orElse(null);
					if (schedule != null && schedule.getNextDueDate().equals(payment.getDueDate())) schedule.advanceDueDate();
				}
			}
			case "PAYMENT.CAPTURE.DENIED" -> {
				payment.fail("PAYMENT_DENIED");
				RentSchedule schedule = schedules.findByIdForUpdate(payment.getScheduleId()).orElse(null);
				if (schedule != null && schedule.getStatus() == RentScheduleStatus.ACTIVE) schedule.markPaymentFailed();
			}
			case "PAYMENT.CAPTURE.REFUNDED" -> payment.markRefunded();
			default -> { }
		}
		return true;
	}

	private RentSchedule requireOwned(Jwt jwt, UUID scheduleId) {
		UUID ownerId = identities.requireOwner(jwt).ownerId();
		return schedules.findByIdAndOwnerId(scheduleId, ownerId).orElseThrow(() ->
				new ErrorResponseException(HttpStatus.NOT_FOUND,
						ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "Rent schedule was not found."), null));
	}

	private RentScheduleView view(RentSchedule schedule) {
		List<RentPaymentView> history = payments.findTop5ByScheduleIdOrderByDueDateDesc(schedule.getId()).stream()
				.map(payment -> new RentPaymentView(payment.getDueDate(), payment.getAmount(), "USD", payment.getStatus()))
				.toList();
		return new RentScheduleView(schedule.getId(), schedule.getTenantLabel(), schedule.getAmount(), "USD",
				schedule.getFrequency(), schedule.getNextDueDate(), schedule.getStatus(), schedule.getApprovalUrl(), history);
	}
}
