package com.suitefolk.api.rent;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import com.suitefolk.api.identity.OwnerIdentity;
import com.suitefolk.api.identity.OwnerIdentityService;
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
	private final RentLedgerService ledger;
	private final RentCollectionService collection;
	@jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

	public RentScheduleService(RentScheduleRepository schedules, RentPaymentRepository payments,
			PayPalWebhookEventRepository webhookEvents, OwnerIdentityService identities, PayPalClient paypal, RentLedgerService ledger, RentCollectionService collection) {
		this.schedules = schedules;
		this.payments = payments;
		this.webhookEvents = webhookEvents;
		this.identities = identities;
		this.paypal = paypal;
		this.ledger = ledger;
		this.collection = collection;
	}

	@Transactional
	public RentScheduleView create(Jwt jwt, CreateRentScheduleRequest request) {
		OwnerIdentity owner = identities.requireOwner(jwt);
        RentSchedule schedule = createBounded(owner.ownerId(), request.tenantLabel(), request.amount(),
            request.frequency(), request.nextDueDate(), request.endDate());
        return view(schedule);
	}


    @Transactional
    public UUID createForLease(UUID ownerId, String label, java.math.BigDecimal amount, Frequency frequency,
            LocalDate firstDueDate, LocalDate endDate) {
        return createBounded(ownerId, label, amount, frequency, firstDueDate, endDate).getId();
    }

    private RentSchedule createBounded(UUID ownerId, String label, java.math.BigDecimal amount, Frequency frequency,
            LocalDate firstDueDate, LocalDate endDate) {
        if (endDate != null && (endDate.isBefore(firstDueDate) || endDate.getYear() > 2100))
            throw RentLedgerService.error(HttpStatus.BAD_REQUEST, "End date must include the first rent date and be no later than 2100.");
        RentSchedule schedule = schedules.save(new RentSchedule(UUID.randomUUID(), ownerId, label.trim(), amount,
            frequency, firstDueDate, endDate));
        ledger.ensurePeriods(schedule, RentLedgerService.today());
        return schedule;
    }

    @Transactional
    public RentScheduleView setupAutopay(Jwt jwt, UUID scheduleId, StartRentAutopayRequest request) {
        RentSchedule schedule=ledger.requireOwnedLocked(jwt,scheduleId);
        if(schedule.getStatus()==RentScheduleStatus.SCHEDULED || schedule.getStatus()==RentScheduleStatus.SETUP_FAILED) {
            ledger.ensurePeriods(schedule,RentLedgerService.today());
            java.time.LocalDate start=ledger.nextAutopayDate(schedule);
            if(start == null || schedule.isEndedOn(RentLedgerService.today())) throw RentLedgerService.error(HttpStatus.CONFLICT,"No future rent date remains within this term.");
            if(!start.equals(request.firstChargeDate())) throw RentLedgerService.error(HttpStatus.CONFLICT,"The future start date changed. Review autopay setup again.");
            while(schedule.getNextDueDate().isBefore(start)) {
                RentPeriod old=ledger.duePeriod(schedule,schedule.getNextDueDate());
                if(ledger.view(old).balance().signum()>0 && !old.isCollectionHeld()) old.hold("BEFORE_AUTOPAY_START");
                schedule.advanceDueDate();
            }
            ledger.ensurePeriods(schedule,RentLedgerService.today());
            PayPalClient.SetupToken setup=paypal.createSetupToken(schedule.getId(),schedule.getFrequency(),
                schedule.getAmount(),schedule.getNextDueDate());
            schedule.attachSetupToken(setup.id(),setup.approvalUrl());
        }
        return view(schedule);
    }

	@Transactional
	public List<RentScheduleView> list(Jwt jwt) {
		UUID ownerId = identities.requireOwner(jwt).ownerId();
		List<RentSchedule> owned=schedules.findAllByOwnerIdOrderByNextDueDateAsc(ownerId);
        owned.stream().map(RentSchedule::getId).sorted().forEach(id -> {
            RentSchedule locked=schedules.findByIdForUpdate(id).orElseThrow();
            ledger.ensurePeriods(locked,RentLedgerService.today());
        });
        return owned.stream().map(this::view).toList();
	}

	@Transactional(readOnly = true)
	public RentScheduleView get(Jwt jwt, UUID scheduleId) {
		return view(requireOwned(jwt, scheduleId));
	}

	@Transactional
	public RentScheduleView checkApproval(Jwt jwt, UUID scheduleId) {
		RentSchedule schedule = ledger.requireOwnedLocked(jwt, scheduleId);
		if (schedule.getPaypalSetupTokenId() != null) completeApproval(schedule.getPaypalSetupTokenId());
		return view(schedule);
	}

	@Transactional
	public RentScheduleView pause(Jwt jwt, UUID scheduleId) {
		RentSchedule schedule = ledger.requireOwnedLocked(jwt, scheduleId);
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
		if (schedule.isEndedOn(RentLedgerService.today())) return false;
		if (schedule.getStatus() == RentScheduleStatus.ACTIVE) return true;
		if (schedule.getStatus() != RentScheduleStatus.PENDING_APPROVAL) return false;
		if (!"APPROVED".equals(paypal.getSetupTokenStatus(setupTokenId))) return false;
		String paymentToken = paypal.createPaymentToken(setupTokenId, schedule.getId());
		schedule.activate(paymentToken);
		return true;
	}

    public void processDuePayments(LocalDate today) {
        for(UUID id:schedules.findDueIds(RentScheduleStatus.ACTIVE,today)) collection.process(id,today);
    }

	@Transactional
	public boolean recordVerifiedWebhook(JsonNode event) {
		String id = event.path("id").asText("");
		String type = event.path("event_type").asText("");
		JsonNode resource = event.path("resource");
		String resourceId = resource.path("id").asText("");
		if (id.isBlank() || type.isBlank() || id.length() > 128 || type.length() > 128 || resourceId.length() > 128) return false;
		if (webhookEvents.recordOnce(id,type,resourceId.isBlank() ? null : resourceId)==0) return true;
		String orderId = resource.path("supplementary_data").path("related_ids").path("order_id").asText("");
		String invoiceId = resource.path("invoice_id").asText("");
		RentPayment payment = orderId.isBlank() ? null : payments.findByPaypalOrderId(orderId).orElse(null);
		if (payment == null && !resourceId.isBlank()) payment = payments.findByPaypalCaptureId(resourceId).orElse(null);
		if (payment == null && !invoiceId.isBlank()) payment = payments.findByIdempotencyKey(invoiceId).orElse(null);
		if (payment == null) return true;
        RentSchedule locked=schedules.findByIdForUpdate(payment.getScheduleId()).orElse(null);
        if(locked==null) return true;
        entityManager.refresh(payment);
        RentPaymentStatus previousStatus=payment.getStatus();
		switch (type) {
			case "PAYMENT.CAPTURE.COMPLETED" -> {
				boolean alreadyCompleted = payment.getStatus() == RentPaymentStatus.COMPLETED;
				payment.complete(orderId.isBlank() ? payment.getPaypalOrderId() : orderId,
						resourceId.isBlank() ? payment.getPaypalCaptureId() : resourceId);
				if (!alreadyCompleted && payment.getStatus()==RentPaymentStatus.COMPLETED) {
					RentSchedule schedule = schedules.findByIdForUpdate(payment.getScheduleId()).orElse(null);
					if (schedule != null && schedule.getNextDueDate().equals(payment.getDueDate())) schedule.advanceDueDate();
				}
			}
			case "PAYMENT.CAPTURE.DENIED" -> {
				payment.fail("PAYMENT_DENIED");
				RentSchedule schedule = schedules.findByIdForUpdate(payment.getScheduleId()).orElse(null);
				if (payment.getStatus()==RentPaymentStatus.FAILED && schedule != null && schedule.getStatus() == RentScheduleStatus.ACTIVE) schedule.markPaymentFailed();
			}
			case "PAYMENT.CAPTURE.REFUNDED" -> payment.markRefunded();
			default -> { }
		}
		if(previousStatus!=payment.getStatus()) ledger.providerChanged(locked,payment);
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
		RentLedgerService.Summary summary=ledger.summary(schedule.getId());
		return new RentScheduleView(schedule.getId(), schedule.getTenantLabel(), schedule.getAmount(), "USD",
				schedule.getFrequency(), schedule.getNextDueDate(), schedule.getStatus(), paypal.isAutopayEnabled(),
				schedule.getApprovalUrl(), history, summary.outstandingBalance(), summary.reviewRequiredCount(), ledger.nextAutopayDate(schedule), schedule.getEndDate(), schedule.isEndedOn(RentLedgerService.today()));
	}
}
