package api.rent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import api.identity.OwnerIdentityService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
public class RentLedgerService {
    private final RentScheduleRepository schedules;
    private final RentPeriodRepository periods;
    private final RentExternalPaymentRepository receipts;
    private final RentPaymentRepository payments;
    private final OwnerIdentityService identities;
    public RentLedgerService(RentScheduleRepository schedules, RentPeriodRepository periods,
            RentExternalPaymentRepository receipts, RentPaymentRepository payments, OwnerIdentityService identities) {
        this.schedules=schedules; this.periods=periods; this.receipts=receipts; this.payments=payments; this.identities=identities;
    }
    static LocalDate today() { return LocalDate.now(ZoneId.of("America/Chicago")); }

    // All writers, including collection and webhook reconciliation, acquire the schedule lock first.
    @Transactional
    public List<RentPeriodView> list(Jwt jwt, UUID scheduleId) {
        RentSchedule schedule=requireOwnedLocked(jwt,scheduleId);
        ensurePeriods(schedule,today());
        return periods.findByScheduleIdOrderByDueDateAsc(scheduleId).stream().map(this::view).toList();
    }
    @Transactional
    public RentPeriodView record(Jwt jwt, UUID scheduleId, UUID periodId, RecordExternalPaymentRequest request) {
        RentSchedule schedule=requireOwnedLocked(jwt,scheduleId);
        RentPeriod period=requirePeriod(scheduleId,periodId);
        if(request.paidOn().isAfter(today())) throw error(HttpStatus.BAD_REQUEST,"Payment date cannot be in the future.");
        String reference=request.reference()==null ? "" : request.reference().trim();
        RentExternalPayment previous=receipts.findById(request.requestId()).orElse(null);
        if(previous!=null) {
            if(!previous.matches(periodId,request.amount(),request.paidOn(),request.method(),reference))
                throw error(HttpStatus.CONFLICT,"That confirmation has already been used. Refresh the ledger.");
            return view(period);
        }
        if(period.getRevision()!=request.revision()) throw error(HttpStatus.CONFLICT,"The balance changed. Refresh before recording this payment.");
        int inserted=receipts.recordOnce(request.requestId(),periodId,request.amount(),request.paidOn(),
            request.method().name(),reference,schedule.getOwnerId());
        if(inserted==0) throw error(HttpStatus.CONFLICT,"That confirmation has already been used. Refresh the ledger.");
        period.hold("EXTERNAL_PAYMENT");
        periods.flush();
        RentPeriodView result=view(period);
        if(result.balance().signum()<=0 && !result.reconciliationRequired() && schedule.getNextDueDate().equals(period.getDueDate())) {
            schedule.advanceDueDate(); ensurePeriods(schedule,today());
        }
        return result;
    }
    @Transactional
    public RentPeriodView reverse(Jwt jwt, UUID scheduleId, UUID periodId, UUID receiptId, ReverseExternalPaymentRequest request) {
        RentSchedule schedule=requireOwnedLocked(jwt,scheduleId); RentPeriod period=requirePeriod(scheduleId,periodId);
        RentExternalPayment receipt=receipts.findById(receiptId).filter(r->r.getPeriodId().equals(periodId))
            .orElseThrow(()->error(HttpStatus.NOT_FOUND,"Payment record was not found."));
        if(receipt.getReversedAt()==null) {
            receipt.reverse(schedule.getOwnerId(),request.reason().trim());
            receipts.flush(); period.hold("PAYMENT_CORRECTION"); periods.flush();
        }
        return view(period);
    }
    @Transactional
    public RentPeriodView review(Jwt jwt, UUID scheduleId, UUID periodId, ReviewRentPeriodRequest request) {
        RentSchedule schedule=requireOwnedLocked(jwt,scheduleId); RentPeriod period=requirePeriod(scheduleId,periodId);
        if(period.getRevision()!=request.revision()) throw error(HttpStatus.CONFLICT,"The balance changed. Refresh before confirming review.");
        if(view(period).reconciliationRequired()) throw error(HttpStatus.CONFLICT,"Reconcile the processing payment or excess payment first.");
        period.acknowledge(schedule.getOwnerId()); periods.flush();
        return view(period);
    }
    RentSchedule requireOwnedLocked(Jwt jwt, UUID scheduleId) {
        UUID ownerId=identities.requireOwner(jwt).ownerId();
        RentSchedule schedule=schedules.findByIdForUpdate(scheduleId).orElse(null);
        if(schedule==null || !schedule.getOwnerId().equals(ownerId)) throw error(HttpStatus.NOT_FOUND,"Rent schedule was not found.");
        return schedule;
    }
    private RentPeriod requirePeriod(UUID scheduleId, UUID periodId) {
        return periods.findByIdAndScheduleId(periodId,scheduleId).orElseThrow(()->error(HttpStatus.NOT_FOUND,"Rent period was not found."));
    }
    void ensurePeriods(RentSchedule schedule, LocalDate through) {
        LocalDate beforeNext=schedule.getNextDueDate().minusDays(1);
        if(beforeNext.isAfter(through)) through=beforeNext;
        List<RentPeriod> existing=periods.findByScheduleIdOrderByDueDateAsc(schedule.getId());
        for(RentPeriod period:existing) {
            if(period.getDueDate().isBefore(today()) && !period.isCollectionHeld() && view(period).balance().signum()>0)
                period.hold("MISSED_DATE");
        }
        LocalDate next=existing.isEmpty() ? schedule.getNextDueDate() : existing.getLast().getDueDate();
        if(!existing.isEmpty()) {
            if(next.isAfter(through)) return;
            next=RentScheduleDates.nextDueDate(schedule.getFrequency(),next,schedule.getMonthlyAnchorDay());
        }
        // Materialize rent obligations, including the next future period, even when automatic charges are off.
        do {
            RentPeriod period=new RentPeriod(schedule.getId(),next,schedule.getAmount());
            if(next.isBefore(through)) period.hold("MISSED_DATE");
            periods.saveAndFlush(period);
            if(next.isAfter(through)) break;
            next=RentScheduleDates.nextDueDate(schedule.getFrequency(),next,schedule.getMonthlyAnchorDay());
        } while(true);
    }
    RentPeriod duePeriod(RentSchedule schedule, LocalDate dueDate) {
        return periods.findByScheduleIdAndDueDate(schedule.getId(),dueDate).orElseGet(()->
            periods.saveAndFlush(new RentPeriod(schedule.getId(),dueDate,schedule.getAmount())));
    }
    RentPeriodView view(RentPeriod period) {
        List<RentExternalPayment> external=receipts.findByPeriodIdOrderByRecordedAtAscIdAsc(period.getId());
        BigDecimal received=external.stream().filter(r->r.getReversedAt()==null).map(RentExternalPayment::getAmount)
            .reduce(BigDecimal.ZERO,BigDecimal::add);
        RentPayment provider=payments.findByScheduleIdAndDueDate(period.getScheduleId(),period.getDueDate()).orElse(null);
        if(provider!=null && provider.getStatus()==RentPaymentStatus.COMPLETED) received=received.add(provider.getAmount());
        BigDecimal balance=period.getAmount().subtract(received);
        boolean uncertain=provider!=null && (provider.getStatus()==RentPaymentStatus.PROCESSING ||
            provider.getStatus()==RentPaymentStatus.FAILED && external.stream().anyMatch(r->r.getReversedAt()==null));
        String state=balance.signum()<0 ? "OVERPAID" : balance.signum()==0 ? "PAID" : received.signum()>0 ? "PARTIALLY_PAID" : "UNPAID";
        List<RentPaymentView> providerViews=provider==null ? List.of() :
            List.of(new RentPaymentView(provider.getDueDate(),provider.getAmount(),"USD",provider.getStatus()));
        return new RentPeriodView(period.getId(),period.getDueDate(),period.getAmount(),received,balance,state,
            period.isCollectionHeld(),period.isReviewRequired(),uncertain || balance.signum()<0,period.getHoldReason(),
            period.getRevision(),period.getReviewedAt(),external.stream().map(r->new RentPeriodView.ExternalPaymentView(
                r.getId(),r.getAmount(),r.getPaidOn(),r.getMethod(),r.getReference(),r.getRecordedAt(),r.getReversedAt(),r.getReversalReason())).toList(),providerViews);
    }
    void providerChanged(RentSchedule schedule, RentPayment payment) {
        RentPeriod period=duePeriod(schedule,payment.getDueDate());
        if(payment.getStatus()==RentPaymentStatus.FAILED || payment.getStatus()==RentPaymentStatus.REFUNDED ||
            view(period).balance().signum()<0) period.hold("PROVIDER_REVIEW");
        else period.recordProviderChange();
    }
    LocalDate nextAutopayDate(RentSchedule schedule) {
        LocalDate candidate=schedule.getNextDueDate();
        while(true) {
            RentPeriod period=periods.findByScheduleIdAndDueDate(schedule.getId(),candidate).orElse(null);
            if(candidate.isAfter(today()) && (period==null || !period.isCollectionHeld() && view(period).balance().signum()>0))
                return candidate;
            candidate=RentScheduleDates.nextDueDate(schedule.getFrequency(),candidate,schedule.getMonthlyAnchorDay());
        }
    }
    public record Summary(BigDecimal outstandingBalance, int reviewRequiredCount) { }
    Summary summary(UUID scheduleId) {
        List<RentPeriodView> views=periods.findByScheduleIdOrderByDueDateAsc(scheduleId).stream().map(this::view).toList();
        return new Summary(views.stream().map(RentPeriodView::balance).filter(b->b.signum()>0).reduce(BigDecimal.ZERO,BigDecimal::add),
            (int)views.stream().filter(p->p.reviewRequired() || p.reconciliationRequired()).count());
    }
    static ErrorResponseException error(HttpStatus status,String detail) {
        return new ErrorResponseException(status,ProblemDetail.forStatusAndDetail(status,detail),null);
    }
}
