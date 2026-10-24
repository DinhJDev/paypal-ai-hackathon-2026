package api.rent;

import java.time.LocalDate;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RentCollectionService {
    private final RentScheduleRepository schedules;
    private final RentPaymentRepository payments;
    private final RentLedgerService ledger;
    private final PayPalClient paypal;
    public RentCollectionService(RentScheduleRepository schedules, RentPaymentRepository payments, RentLedgerService ledger, PayPalClient paypal) {
        this.schedules=schedules; this.payments=payments; this.ledger=ledger; this.paypal=paypal;
    }
    @Transactional
    public void process(UUID scheduleId, LocalDate today) {
        RentSchedule schedule=schedules.findByIdForUpdate(scheduleId).orElse(null);
        if(schedule==null || schedule.getStatus()!=RentScheduleStatus.ACTIVE) return;
        ledger.ensurePeriods(schedule,today);
        while(!schedule.getNextDueDate().isAfter(today)) {
            LocalDate due=schedule.getNextDueDate();
            RentPeriod period=ledger.duePeriod(schedule,due);
            RentPeriodView balance=ledger.view(period);
            if(balance.reconciliationRequired()) { period.hold("PROVIDER_REVIEW"); return; }
            if(balance.balance().signum()<=0 || period.isCollectionHeld()) { schedule.advanceDueDate(); continue; }
            if(due.isBefore(today)) { period.hold("MISSED_DATE"); schedule.advanceDueDate(); continue; }
            RentPayment payment=payments.findByScheduleIdAndDueDate(scheduleId,due).orElse(null);
            if(payment!=null) { period.hold("PROVIDER_REVIEW"); return; }
            String key="rent-"+scheduleId+"-"+due;
            payment=payments.saveAndFlush(new RentPayment(UUID.randomUUID(),scheduleId,due,period.getAmount(),key));
            try {
                PayPalClient.ChargeResult result=paypal.captureRent(schedule.getPaypalPaymentTokenId(),period.getAmount(),key,key);
                payment.recordExternalIds(result.orderId(),result.captureId());
                if("COMPLETED".equals(result.orderStatus()) && "COMPLETED".equals(result.captureStatus()) &&
                        !result.orderId().isBlank() && !result.captureId().isBlank()) {
                    payment.complete(result.orderId(),result.captureId()); schedule.advanceDueDate();
                } else { payment.fail("PAYMENT_NOT_COMPLETED"); schedule.markPaymentFailed(); }
            } catch(PayPalUnavailableException exception) {
                payment.fail("PAYPAL_UNAVAILABLE"); schedule.markPaymentFailed();
            }
            ledger.providerChanged(schedule,payment);
            return;
        }
    }
}
