package com.suitefolk.api;

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
@SpringBootTest(properties="spring.datasource.password=test-only-password")
@AutoConfigureMockMvc
class LeaseEndDateTests extends JwtTestSupport {
    private static final UUID OWNER=UUID.fromString("77133288-18dd-4394-951d-6ecb51a962f9");
    private static final LocalDate TODAY=LocalDate.now(ZoneId.of("America/Chicago"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired RentScheduleService schedules;
    @MockitoBean PayPalClient paypal;

    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM rent_schedule");
        jdbc.update("DELETE FROM owner_access");
        jdbc.update("INSERT INTO owner_access(id,issuer,subject,enabled) VALUES (?,?,?,true)",OWNER,JwtFixture.ISSUER,"auth0|lease-end-owner");
        reset(paypal);
    }
    @Test void endDateIsReturnedAndCannotPrecedeTheFirstDueDate() throws Exception {
        mvc.perform(post("/api/v1/rent-schedules").header("Authorization",bearer()).contentType("application/json")
            .content(body(TODAY,TODAY)))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.endDate").value(TODAY.toString()));
        mvc.perform(post("/api/v1/rent-schedules").header("Authorization",bearer()).contentType("application/json")
            .content(body(TODAY.plusDays(1),TODAY))).andExpect(status().isBadRequest());
        verify(paypal,never()).createSetupToken(any(),any(),any(),any());
        verify(paypal,never()).captureRent(anyString(),any(),anyString(),anyString());
    }
    @Test void lastDueDateIsIncludedButTheNextPeriodAndCaptureAreBounded() throws Exception {
        UUID id=create();
        jdbc.update("UPDATE rent_schedule SET status='ACTIVE',paypal_payment_token_id='test-vault' WHERE id=?",id);
        when(paypal.captureRent(anyString(),any(),anyString(),anyString()))
            .thenReturn(new PayPalClient.ChargeResult("test-order","test-capture","COMPLETED","COMPLETED"));
        schedules.processDuePayments(TODAY);
        verify(paypal,times(1)).captureRent(anyString(),any(),anyString(),anyString());
        JsonNode periods=ledger(id);
        assertThat(periods.size()).isEqualTo(1);
        assertThat(periods.get(0).path("dueDate").asText()).isEqualTo(TODAY.toString());
        assertThat(periods.get(0).path("paymentStatus").asText()).isEqualTo("PAID");
        mvc.perform(get("/api/v1/rent-schedules/"+id).header("Authorization",bearer()))
            .andExpect(status().isOk()).andExpect(jsonPath("$.scheduleEnded").value(true))
            .andExpect(jsonPath("$.autopayStartDate").doesNotExist());
        schedules.processDuePayments(TODAY.plusMonths(2));
        verify(paypal,times(1)).captureRent(anyString(),any(),anyString(),anyString());
    }
    @Test void endedTermKeepsUnpaidRentButNeverCreatesMoreObligations() throws Exception {
        UUID id=create();
        jdbc.update("UPDATE rent_schedule SET status='ACTIVE',paypal_payment_token_id='test-vault' WHERE id=?",id);
        schedules.processDuePayments(TODAY.plusDays(1));
        JsonNode periods=ledger(id);
        assertThat(periods.size()).isEqualTo(1);
        assertThat(periods.get(0).path("balance").asInt()).isEqualTo(1200);
        verify(paypal,never()).createSetupToken(any(),any(),any(),any());
        verify(paypal,never()).captureRent(anyString(),any(),anyString(),anyString());
    }
    @Test void firstReadAfterExpirationHoldsTheFinalUnmaterializedPeriodForReview() throws Exception {
        UUID id=create();
        jdbc.update("DELETE FROM rent_period WHERE schedule_id=?",id);
        jdbc.update("UPDATE rent_schedule SET frequency='WEEKLY',next_due_date=?,end_date=? WHERE id=?",TODAY.minusDays(14),TODAY.minusDays(7),id);
        JsonNode periods=ledger(id);
        assertThat(periods.size()).isEqualTo(2);
        assertThat(periods.get(1).path("dueDate").asText()).isEqualTo(TODAY.minusDays(7).toString());
        assertThat(periods.get(1).path("collectionHeld").asBoolean()).isTrue();
        assertThat(periods.get(1).path("reviewRequired").asBoolean()).isTrue();
    }
    @Test void setupCannotAuthorizeAFutureDateOutsideTheTerm() throws Exception {
        UUID id=create();
        when(paypal.createSetupToken(any(),any(),any(),any()))
            .thenReturn(new PayPalClient.SetupToken("test-setup","https://sandbox.paypal.com/approve/test"));
        mvc.perform(post("/api/v1/rent-schedules/"+id+"/autopay-setup").header("Authorization",bearer())
            .contentType("application/json").content("{\"firstChargeDate\":\""+TODAY.plusMonths(1)+"\",\"confirmed\":true}"))
            .andExpect(status().isConflict());
        verify(paypal,never()).createSetupToken(any(),any(),any(),any());
        verify(paypal,never()).captureRent(anyString(),any(),anyString(),anyString());
    }
    @Test void expiredApprovalIsBlockedAndALateCaptureStillReconcilesTheOriginalPeriod() throws Exception {
        UUID id=create();LocalDate last=TODAY.minusDays(1);
        jdbc.update("UPDATE rent_schedule SET status='PENDING_APPROVAL',paypal_setup_token_id='test-expired',next_due_date=?,end_date=? WHERE id=?",last,last,id);
        jdbc.update("UPDATE rent_period SET due_date=? WHERE schedule_id=?",last,id);
        assertThat(schedules.completeApproval("test-expired")).isFalse();
        verify(paypal,never()).getSetupTokenStatus(anyString());
        String key="rent-"+id+"-"+last;
        jdbc.update("INSERT INTO rent_payment(id,schedule_id,due_date,amount,status,idempotency_key) VALUES (?,?,?,1200,'PROCESSING',?)",UUID.randomUUID(),id,last,key);
        schedules.recordVerifiedWebhook(json.readTree("""
            {"id":"test-late-capture","event_type":"PAYMENT.CAPTURE.COMPLETED","resource":{"id":"test-late-capture-id","invoice_id":"%s"}}
            """.formatted(key)));
        JsonNode periods=ledger(id);
        assertThat(periods.size()).isEqualTo(1);
        assertThat(periods.get(0).path("balance").asInt()).isZero();
        assertThat(jdbc.queryForObject("SELECT paypal_capture_id FROM rent_payment WHERE schedule_id=?",String.class,id)).isEqualTo("test-late-capture-id");
        verify(paypal,never()).captureRent(anyString(),any(),anyString(),anyString());
    }
    private UUID create() throws Exception {
        return UUID.fromString(json.readTree(mvc.perform(post("/api/v1/rent-schedules").header("Authorization",bearer())
            .contentType("application/json").content(body(TODAY,TODAY))).andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString()).path("id").asText());
    }
    private JsonNode ledger(UUID id) throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/rent-schedules/"+id+"/ledger").header("Authorization",bearer()))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
    private String bearer() {return "Bearer "+JwtFixture.token("auth0|lease-end-owner");}
    private String body(LocalDate first,LocalDate end) {return """
        {"tenantLabel":"Suite final","amount":1200,"frequency":"MONTHLY","nextDueDate":"%s","endDate":"%s"}
        """.formatted(first,end);}
}
