package com.suitefolk.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import com.suitefolk.api.rent.PayPalClient;
import com.suitefolk.api.rent.RentScheduleService;
import com.suitefolk.testing.JwtFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles({"test", "local"})
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.datasource.password=test-only-password")
@AutoConfigureMockMvc
class RentLedgerIntegrationTests extends JwtTestSupport {
    private static final UUID OWNER = UUID.fromString("f216e512-81d4-4f50-a0fc-317952e91f15");
    private static final String SUBJECT = "auth0|ledger-owner";
    private static final LocalDate TODAY = LocalDate.now(ZoneId.of("America/Chicago"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired RentScheduleService schedules;
    @MockitoBean PayPalClient paypal;

    @BeforeEach void resetData() {
        jdbc.update("DELETE FROM paypal_webhook_event");
        jdbc.update("DELETE FROM rent_payment");
        jdbc.update("DELETE FROM rent_schedule");
        jdbc.update("DELETE FROM owner_access");
        jdbc.update("INSERT INTO owner_access (id, issuer, subject, enabled) VALUES (?, ?, ?, true)", OWNER, JwtFixture.ISSUER, SUBJECT);
        reset(paypal);
    }

    @Test void scheduleCanExistWithoutPayPalSetup() throws Exception {
        mvc.perform(post("/api/v1/rent-schedules").header("Authorization", bearer()).contentType("application/json")
            .content("""
                {"tenantLabel":"Suite cash","amount":1200,"frequency":"MONTHLY","nextDueDate":"%s"}
                """.formatted(TODAY.plusDays(2))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.status").value("SCHEDULED"))
            .andExpect(jsonPath("$.approvalUrl").isEmpty());
        verify(paypal, never()).createSetupToken(any(), any(), any(), any());
    }

    @Test void partialOutsidePaymentHoldsOnlyThatPeriodAndNeverChargesTheRemainder() throws Exception {
        UUID schedule = schedule();
        String period = period(schedule);
        record(schedule, period, UUID.randomUUID(), "400.00", "CASH")
            .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(800))
            .andExpect(jsonPath("$.paymentStatus").value("PARTIALLY_PAID"))
            .andExpect(jsonPath("$.collectionHeld").value(true)).andExpect(jsonPath("$.reviewRequired").value(true));
        schedules.processDuePayments(TODAY);
        verify(paypal, never()).captureRent(anyString(), any(), anyString(), anyString());
        mvc.perform(get(base(schedule)).header("Authorization", bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].dueDate").value(TODAY.toString()))
            .andExpect(jsonPath("$[1].dueDate").value(TODAY.plusMonths(1).toString()));
        assertThat(jdbc.queryForObject("SELECT status FROM rent_schedule WHERE id=?", String.class, schedule)).isEqualTo("ACTIVE");
    }

    @Test void severalOutsidePaymentsSettleRentWithoutPayPalAndAdvanceTheCollectionDate() throws Exception {
        UUID schedule = schedule(); String period = period(schedule);
        record(schedule, period, UUID.randomUUID(), "400.00", "CASH").andExpect(status().isOk());
        record(schedule, period, UUID.randomUUID(), "800.00", "VENMO")
            .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(0))
            .andExpect(jsonPath("$.paymentStatus").value("PAID"))
            .andExpect(jsonPath("$.externalPayments.length()").value(2));
        schedules.processDuePayments(TODAY);
        verify(paypal, never()).captureRent(anyString(), any(), anyString(), anyString());
        assertThat(jdbc.queryForObject("SELECT next_due_date FROM rent_schedule WHERE id=?", LocalDate.class, schedule)).isEqualTo(TODAY.plusMonths(1));
    }

    @Test void duplicateConfirmationIsIdempotentButChangedPayloadIsRejected() throws Exception {
        UUID schedule = schedule(); String period = period(schedule); UUID request = UUID.randomUUID();
        record(schedule, period, request, "400.00", "CASH").andExpect(status().isOk());
        record(schedule, period, request, "400.00", "CASH").andExpect(status().isOk())
            .andExpect(jsonPath("$.externalPayments.length()").value(1)).andExpect(jsonPath("$.balance").value(800));
        record(schedule, period, request, "500.00", "CASH").andExpect(status().isConflict());
    }

    @Test void reversalPreservesTheReceiptAndDoesNotRearmAutopay() throws Exception {
        UUID schedule = schedule(); String period = period(schedule); UUID receipt = UUID.randomUUID();
        record(schedule, period, receipt, "400.00", "CASH").andExpect(status().isOk());
        mvc.perform(post(base(schedule)+"/"+period+"/payments/"+receipt+"/reversal").header("Authorization", bearer())
            .contentType("application/json").content("{\"reason\":\"Wrong amount entered\",\"confirmed\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.balance").value(1200))
            .andExpect(jsonPath("$.collectionHeld").value(true))
            .andExpect(jsonPath("$.externalPayments[0].amount").value(400))
            .andExpect(jsonPath("$.externalPayments[0].reversalReason").value("Wrong amount entered"));
        schedules.processDuePayments(TODAY);
        verify(paypal, never()).captureRent(anyString(), any(), anyString(), anyString());
    }

    @Test void reviewRejectsStaleBalancesAndAcknowledgementNeverInitiatesCollection() throws Exception {
        UUID schedule = schedule(); String period = period(schedule);
        long oldRevision = ledger(schedule).get(0).path("revision").asLong();
        record(schedule, period, UUID.randomUUID(), "400.00", "CASH").andExpect(status().isOk());
        mvc.perform(post(base(schedule)+"/"+period+"/review").header("Authorization", bearer()).contentType("application/json")
            .content("{\"revision\":"+oldRevision+",\"confirmed\":true}"))
            .andExpect(status().isConflict());
        long revision = ledger(schedule).get(0).path("revision").asLong();
        mvc.perform(post(base(schedule)+"/"+period+"/review").header("Authorization", bearer()).contentType("application/json")
            .content("{\"revision\":"+revision+",\"confirmed\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.reviewRequired").value(false))
            .andExpect(jsonPath("$.collectionHeld").value(true)).andExpect(jsonPath("$.reviewedAt").isNotEmpty());
        schedules.processDuePayments(TODAY);
        verify(paypal, never()).captureRent(anyString(), any(), anyString(), anyString());
    }

    @Test void pendingProviderPaymentIsNotCountedAsReceivedAndLateSettlementExposesOverpayment() throws Exception {
        UUID schedule = schedule(); String period = period(schedule);
        String key = "rent-"+schedule+"-"+TODAY;
        jdbc.update("INSERT INTO rent_payment (id,schedule_id,due_date,amount,status,idempotency_key) VALUES (?,?,?,1200,'PROCESSING',?)",
            UUID.randomUUID(), schedule, TODAY, key);
        record(schedule, period, UUID.randomUUID(), "400.00", "CASH")
            .andExpect(status().isOk()).andExpect(jsonPath("$.amountReceived").value(400))
            .andExpect(jsonPath("$.reconciliationRequired").value(true));
        JsonNode event=json.readTree("""
            {"id":"late-provider","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"capture-late","invoice_id":"%s"}}
            """.formatted(key));
        schedules.recordVerifiedWebhook(event); schedules.recordVerifiedWebhook(event);
        mvc.perform(get(base(schedule)).header("Authorization", bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].amountReceived").value(1600))
            .andExpect(jsonPath("$[0].balance").value(-400)).andExpect(jsonPath("$[0].paymentStatus").value("OVERPAID"))
            .andExpect(jsonPath("$[0].reviewRequired").value(true));
    }

    @Test void ledgerRejectsOtherOwnersAndUnconfirmedOrInvalidReceipts() throws Exception {
        UUID schedule = schedule(); String period = period(schedule);
        mvc.perform(get(base(schedule))).andExpect(status().isUnauthorized());
        jdbc.update("INSERT INTO owner_access (id,issuer,subject,enabled) VALUES (?,?,?,true)", UUID.randomUUID(), JwtFixture.ISSUER, "auth0|other-owner");
        mvc.perform(get(base(schedule)).header("Authorization", "Bearer "+JwtFixture.token("auth0|other-owner"))).andExpect(status().isNotFound());
        mvc.perform(post(base(schedule)+"/"+period+"/payments").header("Authorization", bearer()).contentType("application/json")
            .content("""
                {"requestId":"%s","amount":400,"paidOn":"%s","method":"CASH","revision":0,"confirmed":false}
                """.formatted(UUID.randomUUID(), TODAY))).andExpect(status().isBadRequest());
        record(schedule,period,UUID.randomUUID(),"0.00","CASH").andExpect(status().isBadRequest());
    }


    @Test void receiptConfirmationRejectsABalanceChangedSinceReview() throws Exception {
        UUID schedule=schedule(); String period=period(schedule);
        long revision=ledger(schedule).get(0).path("revision").asLong();
        record(schedule,period,UUID.randomUUID(),"400.00","CASH").andExpect(status().isOk());
        mvc.perform(post(base(schedule)+"/"+period+"/payments").header("Authorization",bearer()).contentType("application/json")
            .content("""
                {"requestId":"%s","amount":800,"paidOn":"%s","method":"CASH","revision":%s,"confirmed":true}
                """.formatted(UUID.randomUUID(),TODAY,revision))).andExpect(status().isConflict());
        assertThat(ledger(schedule).get(0).path("balance").asInt()).isEqualTo(800);
    }

    @Test void overduePeriodsStayVisibleWhileTodaysRentCanBeCollected() throws Exception {
        UUID schedule=schedule();
        jdbc.update("UPDATE rent_schedule SET frequency='WEEKLY',next_due_date=? WHERE id=?",TODAY.minusWeeks(2),schedule);
        when(paypal.captureRent(anyString(),any(),anyString(),anyString()))
            .thenReturn(new PayPalClient.ChargeResult("order-current","capture-current","COMPLETED","COMPLETED"));
        assertThat(ledger(schedule).size()).isEqualTo(4);
        schedules.processDuePayments(TODAY);
        JsonNode periods=ledger(schedule);
        assertThat(periods.get(0).path("balance").asInt()).isEqualTo(1200);
        assertThat(periods.get(0).path("collectionHeld").asBoolean()).isTrue();
        assertThat(periods.get(2).path("balance").asInt()).isZero();
        verify(paypal).captureRent(eq("vault-demo"),eq(new BigDecimal("1200.00")),anyString(),anyString());
    }

    @Test void lateDenialMustNotDowngradeAnAlreadyCompletedPaymentOrDisableFutureAutopay() throws Exception {
        UUID schedule=schedule(); String key="rent-"+schedule+"-"+TODAY;
        jdbc.update("INSERT INTO rent_payment (id,schedule_id,due_date,amount,status,idempotency_key) VALUES (?,?,?,1200,'COMPLETED',?)",
            UUID.randomUUID(),schedule,TODAY,key);
        schedules.recordVerifiedWebhook(json.readTree("""
            {"id":"late-denial","event_type":"PAYMENT.CAPTURE.DENIED","resource":{"id":"denied-capture","invoice_id":"%s"}}
            """.formatted(key)));
        assertThat(jdbc.queryForObject("SELECT status FROM rent_payment WHERE schedule_id=?",String.class,schedule)).isEqualTo("COMPLETED");
        assertThat(jdbc.queryForObject("SELECT status FROM rent_schedule WHERE id=?",String.class,schedule)).isEqualTo("ACTIVE");
    }

    @Test void aLateCompletionDoesNotUndoARecordedRefundOrAdvanceTheSchedule() throws Exception {
        UUID schedule=schedule(); String key="rent-"+schedule+"-"+TODAY;
        jdbc.update("INSERT INTO rent_payment (id,schedule_id,due_date,amount,status,idempotency_key) VALUES (?,?,?,1200,'REFUNDED',?)",
            UUID.randomUUID(),schedule,TODAY,key);
        schedules.recordVerifiedWebhook(json.readTree("""
            {"id":"completion-after-refund","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"capture-refunded","invoice_id":"%s"}}
            """.formatted(key)));
        assertThat(jdbc.queryForObject("SELECT status FROM rent_payment WHERE schedule_id=?",String.class,schedule)).isEqualTo("REFUNDED");
        assertThat(jdbc.queryForObject("SELECT next_due_date FROM rent_schedule WHERE id=?",LocalDate.class,schedule)).isEqualTo(TODAY);
    }

    @Test void concurrentDuplicateConfirmationsRecordOnlyOneReceipt() throws Exception {
        UUID schedule=schedule(); String period=period(schedule); UUID key=UUID.randomUUID();
        String body="""
            {"requestId":"%s","amount":400,"paidOn":"%s","method":"CASH","reference":"receipt-demo","revision":0,"confirmed":true}
            """.formatted(key,TODAY);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Integer> confirm=()->{
                start.await();
                return mvc.perform(post(base(schedule)+"/"+period+"/payments").header("Authorization",bearer())
                    .contentType("application/json").content(body)).andReturn().getResponse().getStatus();
            };
            var first=executor.submit(confirm); var second=executor.submit(confirm); start.countDown();
            assertThat(first.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
            assertThat(second.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(200);
        }
        assertThat(ledger(schedule).get(0).path("externalPayments").size()).isEqualTo(1);
    }

    @Test void captureAndExternalConfirmationSerializeWithoutBlindlyAcceptingAStaleBalance() throws Exception {
        UUID schedule=schedule(); String period=period(schedule);
        var capturing=new java.util.concurrent.CountDownLatch(1);
        var release=new java.util.concurrent.CountDownLatch(1);
        when(paypal.captureRent(anyString(),any(),anyString(),anyString())).thenAnswer(invocation->{
            capturing.countDown();
            if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS)) throw new IllegalStateException("Test capture timed out");
            return new PayPalClient.ChargeResult("order-race","capture-race","COMPLETED","COMPLETED");
        });
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var charge=executor.submit(()->schedules.processDuePayments(TODAY));
            assertThat(capturing.await(10,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var external=executor.submit(()->mvc.perform(post(base(schedule)+"/"+period+"/payments")
                .header("Authorization",bearer()).contentType("application/json").content("""
                    {"requestId":"%s","amount":400,"paidOn":"%s","method":"CASH","revision":0,"confirmed":true}
                    """.formatted(UUID.randomUUID(),TODAY))).andReturn().getResponse().getStatus());
            release.countDown(); charge.get(10,java.util.concurrent.TimeUnit.SECONDS);
            assertThat(external.get(10,java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(409);
        } finally { release.countDown(); }
        record(schedule,period,UUID.randomUUID(),"400.00","CASH").andExpect(status().isOk())
            .andExpect(jsonPath("$.balance").value(-400)).andExpect(jsonPath("$.reconciliationRequired").value(true));
        verify(paypal).captureRent(anyString(),any(),anyString(),anyString());
    }

    @Test void paymentMutationsCannotAccessAnotherOwnersLedger() throws Exception {
        UUID schedule=schedule(); String period=period(schedule); UUID receipt=UUID.randomUUID();
        record(schedule,period,receipt,"400.00","CASH").andExpect(status().isOk());
        jdbc.update("INSERT INTO owner_access (id,issuer,subject,enabled) VALUES (?,?,?,true)",UUID.randomUUID(),JwtFixture.ISSUER,"auth0|other-owner");
        String other="Bearer "+JwtFixture.token("auth0|other-owner");
        mvc.perform(post(base(schedule)+"/"+period+"/review").header("Authorization",other).contentType("application/json")
            .content("{\"revision\":1,\"confirmed\":true}")).andExpect(status().isNotFound());
        mvc.perform(post(base(schedule)+"/"+period+"/payments/"+receipt+"/reversal").header("Authorization",other).contentType("application/json")
            .content("{\"reason\":\"Wrong owner\",\"confirmed\":true}")).andExpect(status().isNotFound());
    }


    @Test void existingUnpaidPeriodsBecomeReviewHoldsEvenWhenAutopayIsOff() throws Exception {
        UUID schedule=schedule(); String period=period(schedule);
        jdbc.update("UPDATE rent_schedule SET status='SCHEDULED',next_due_date=? WHERE id=?",TODAY.minusDays(1),schedule);
        jdbc.update("UPDATE rent_period SET due_date=? WHERE id=?",TODAY.minusDays(1),UUID.fromString(period));
        JsonNode overdue=ledger(schedule).get(0);
        assertThat(overdue.path("collectionHeld").asBoolean()).isTrue();
        assertThat(overdue.path("holdReason").asText()).isEqualTo("MISSED_DATE");
        mvc.perform(post(base(schedule)+"/"+period+"/review").header("Authorization",bearer()).contentType("application/json")
            .content("{\"revision\":"+overdue.path("revision").asLong()+",\"confirmed\":true}"))
            .andExpect(status().isOk());
        assertThat(ledger(schedule).get(0).path("reviewRequired").asBoolean()).isFalse();
        verifyNoInteractions(paypal);
    }

    @Test void deferredAutopayRequiresOwnerConfirmationOfAFutureDateAndPreservesArrears() throws Exception {
        UUID schedule=schedule();
        jdbc.update("UPDATE rent_schedule SET status='SCHEDULED',frequency='WEEKLY',next_due_date=? WHERE id=?",TODAY.minusDays(1),schedule);
        LocalDate future=TODAY.plusDays(6);
        when(paypal.createSetupToken(eq(schedule),any(),eq(new BigDecimal("1200.00")),eq(future)))
            .thenReturn(new PayPalClient.SetupToken("setup-future","https://www.sandbox.paypal.com/approve/future"));
        mvc.perform(get("/api/v1/rent-schedules").header("Authorization",bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].autopayStartDate").value(future.toString()));
        mvc.perform(post("/api/v1/rent-schedules/"+schedule+"/autopay-setup").header("Authorization",bearer())
            .contentType("application/json").content("{\"firstChargeDate\":\""+future+"\",\"confirmed\":true}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.nextDueDate").value(future.toString()));
        JsonNode old=ledger(schedule).get(0);
        assertThat(old.path("balance").asInt()).isEqualTo(1200);
        assertThat(old.path("collectionHeld").asBoolean()).isTrue();
        verify(paypal).createSetupToken(eq(schedule),any(),eq(new BigDecimal("1200.00")),eq(future));
    }

    @Test void unconfirmedOrStaleAutopayStartDatesNeverReachPayPal() throws Exception {
        UUID schedule=schedule(); jdbc.update("UPDATE rent_schedule SET status='SCHEDULED' WHERE id=?",schedule);
        mvc.perform(post("/api/v1/rent-schedules/"+schedule+"/autopay-setup").header("Authorization",bearer())
            .contentType("application/json").content("{\"firstChargeDate\":\""+TODAY.plusMonths(1)+"\",\"confirmed\":false}"))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/rent-schedules/"+schedule+"/autopay-setup").header("Authorization",bearer())
            .contentType("application/json").content("{\"firstChargeDate\":\""+TODAY.plusDays(2)+"\",\"confirmed\":true}"))
            .andExpect(status().isConflict());
        verifyNoInteractions(paypal);
    }

    private String bearer() { return "Bearer "+JwtFixture.token(SUBJECT); }
    private String base(UUID id) { return "/api/v1/rent-schedules/"+id+"/ledger"; }
    private JsonNode ledger(UUID id) throws Exception {
        return json.readTree(mvc.perform(get(base(id)).header("Authorization",bearer())).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private String period(UUID id) throws Exception { return ledger(id).get(0).path("id").asText(); }
    private org.springframework.test.web.servlet.ResultActions record(UUID id,String period,UUID key,String amount,String method) throws Exception {
        long revision=0;
        for(JsonNode row:ledger(id)) if(row.path("id").asText().equals(period)) revision=row.path("revision").asLong();
        return mvc.perform(post(base(id)+"/"+period+"/payments").header("Authorization",bearer()).contentType("application/json")
            .content("""
                {"requestId":"%s","amount":%s,"paidOn":"%s","method":"%s","reference":"receipt-demo","revision":%s,"confirmed":true}
                """.formatted(key,amount,TODAY,method,revision)));
    }
    private UUID schedule() {
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO rent_schedule (id,owner_id,tenant_label,amount,frequency,next_due_date,monthly_anchor_day,status,paypal_payment_token_id)
            VALUES (?,?,'Suite 1',1200,'MONTHLY',?,?,'ACTIVE','vault-demo')
            """,id,OWNER,TODAY,TODAY.getDayOfMonth());
        return id;
    }
}
