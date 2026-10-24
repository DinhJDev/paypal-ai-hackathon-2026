package com.suitefolk.api;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import com.suitefolk.api.rent.PayPalClient;
import com.suitefolk.testing.JwtFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles({"test", "local"})
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties="spring.datasource.password=test-only-password")
@AutoConfigureMockMvc
class LeaseWorkspaceIntegrationTests extends JwtTestSupport {
    private static final UUID OWNER=UUID.fromString("66f0c382-6991-4518-84da-0a31a3c4ddab");
    private static final LocalDate TODAY=LocalDate.now(ZoneId.of("America/Chicago"));
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @MockitoBean PayPalClient paypal;

    @BeforeEach void prepare() {
        jdbc.update("DELETE FROM lease_workspace");
        jdbc.update("DELETE FROM rent_schedule");
        jdbc.update("DELETE FROM owner_access");
        jdbc.update("INSERT INTO owner_access(id,issuer,subject,enabled) VALUES (?,?,?,true)",OWNER,JwtFixture.ISSUER,"auth0|lease-owner");
        reset(paypal);
    }
    @AfterEach void removeTestLeases() { jdbc.update("DELETE FROM lease_workspace"); }
    @Test void incompleteDraftSurvivesReloadAndCreationRetryKeepsTheSavedEdits() throws Exception {
        UUID id=create();
        save(id,0,"{\"tenantName\":\"Taylor\",\"suiteLabel\":\"Suite 8\",\"furnishedItems\":\"Chair and mirror\"}")
            .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("WORKING"))
            .andExpect(jsonPath("$.revision").value(1)).andExpect(jsonPath("$.leaseStatus").value("UNCONFIRMED"));
        JsonNode draft=getLease(id);
        assertThat(draft.path("terms").path("tenantName").asText()).isEqualTo("Taylor");
        assertThat(draft.path("terms").path("furnishedItems").asText()).isEqualTo("Chair and mirror");
        assertThat(draft.path("confirmationIssues").isEmpty()).isFalse();
        mvc.perform(post("/api/v1/leases").header("Authorization",bearer()).contentType("application/json")
            .content("{\"requestId\":\""+id+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.terms.tenantName").value("Taylor"));
        assertThat(countSchedules()).isZero();
        verifyNoInteractions(paypal);
    }
    @Test void confirmationRequiresCompleteTermsAndExplicitOwnerConsent() throws Exception {
        UUID id=create();
        confirm(id,0,true).andExpect(status().isBadRequest());
        save(id,0,terms()).andExpect(status().isOk());
        confirm(id,1,false).andExpect(status().isBadRequest());
        mvc.perform(post(path(id)+"/confirm").header("Authorization",bearer()).contentType("application/json")
            .content("{\"revision\":1}")).andExpect(status().isBadRequest());
        assertThat(getLease(id).path("status").asText()).isEqualTo("WORKING");
        assertThat(countSchedules()).isZero();
    }
    @Test void confirmationCreatesOneBoundedScheduleAndRetainsAnImmutableSnapshot() throws Exception {
        UUID id=create(); save(id,0,terms()).andExpect(status().isOk());
        JsonNode result=json.readTree(confirm(id,1,true).andExpect(status().isOk())
            .andExpect(header().string("Cache-Control","private, no-store"))
            .andReturn().getResponse().getContentAsString());
        assertThat(result.path("status").asText()).isEqualTo("CONFIRMED");
        assertThat(result.path("leaseStatus").asText()).isEqualTo("ACTIVE");
        assertThat(result.path("confirmedAt").asText()).isNotBlank();
        String scheduleId=result.path("scheduleId").asText();
        assertThat(UUID.fromString(scheduleId)).isNotNull();
        confirm(id,1,true).andExpect(status().isOk()).andExpect(jsonPath("$.scheduleId").value(scheduleId));
        assertThat(countSchedules()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT end_date FROM rent_schedule WHERE id=?",LocalDate.class,UUID.fromString(scheduleId))).isEqualTo(TODAY.plusMonths(2));
        assertThat(jdbc.queryForObject("SELECT status FROM rent_schedule WHERE id=?",String.class,UUID.fromString(scheduleId))).isEqualTo("SCHEDULED");
        assertThat(jdbc.queryForObject("SELECT confirmed_by FROM lease_workspace WHERE id=?",UUID.class,id)).isEqualTo(OWNER);
        save(id,1,"{\"tenantName\":\"Changed after review\"}").andExpect(status().isConflict());
        assertThat(getLease(id).path("terms").path("tenantName").asText()).isEqualTo("Taylor");
        verifyNoInteractions(paypal);
    }
    @Test void staleSaveAndConfirmationCannotOverwriteOrApproveNewerTerms() throws Exception {
        UUID id=create(); save(id,0,terms()).andExpect(status().isOk());
        save(id,0,"{\"tenantName\":\"Stale\"}").andExpect(status().isConflict());
        save(id,1,terms().replace("1200","1350")).andExpect(status().isOk());
        confirm(id,1,true).andExpect(status().isConflict());
        assertThat(getLease(id).path("terms").path("amount").asInt()).isEqualTo(1350);
        assertThat(countSchedules()).isZero();
    }
    @Test void retryingAnIdenticalSaveAfterALostResponseDoesNotCreateAConflictOrAnotherRevision() throws Exception {
        UUID id=create();
        save(id,0,terms()).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        save(id,0,terms()).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
        assertThat(getLease(id).path("terms").path("amount").asInt()).isEqualTo(1200);
    }
    @Test void previewPreservesMonthEndAnchorsAndIncludesTheFinalLeapYearDueDate() throws Exception {
        UUID id=create();int year=TODAY.getYear()+2;while(!java.time.Year.isLeap(year))year++;
        String value="""
            {"tenantName":"Taylor","suiteLabel":"Suite 8","startDate":"%s-01-01","endDate":"%s-04-30",
            "firstDueDate":"%s-01-31","amount":1200,"frequency":"MONTHLY"}
            """.formatted(year,year,year);
        save(id,0,value).andExpect(status().isOk());
        JsonNode preview=getLease(id).path("previewDueDates");
        assertThat(preview.toString()).isEqualTo("[\""+year+"-01-31\",\""+year+"-02-29\",\""+year+"-03-31\",\""+year+"-04-30\"]");
        confirm(id,1,true).andExpect(status().isOk()).andExpect(jsonPath("$.leaseStatus").value("UPCOMING"));
    }
    @Test void twoConcurrentConfirmationsCreateExactlyOneSchedule() throws Exception {
        UUID id=create(); save(id,0,terms()).andExpect(status().isOk());
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<String> action=()->{
                start.await();
                return confirm(id,1,true).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            };
            var one=pool.submit(action);var two=pool.submit(action);start.countDown();
            assertThat(json.readTree(one.get(10,java.util.concurrent.TimeUnit.SECONDS)).path("scheduleId").asText())
                .isEqualTo(json.readTree(two.get(10,java.util.concurrent.TimeUnit.SECONDS)).path("scheduleId").asText());
        }
        assertThat(countSchedules()).isEqualTo(1);
    }
    @Test void twoTabsSavingTheSameRevisionProduceAConflictInsteadOfLostEdits() throws Exception {
        UUID id=create();
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var start=new java.util.concurrent.CountDownLatch(1);
            var one=pool.submit(()->{start.await();return save(id,0,"{\"tenantName\":\"Tab A\"}").andReturn().getResponse().getStatus();});
            var two=pool.submit(()->{start.await();return save(id,0,"{\"tenantName\":\"Tab B\"}").andReturn().getResponse().getStatus();});
            start.countDown();
            assertThat(List.of(one.get(10,java.util.concurrent.TimeUnit.SECONDS),two.get(10,java.util.concurrent.TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder(200,409);
        }
        assertThat(getLease(id).path("revision").asLong()).isEqualTo(1);
    }
    @Test void ownerIsolationCoversListsReadsSavesCreationRetriesAndConfirmation() throws Exception {
        UUID id=create(); save(id,0,terms()).andExpect(status().isOk());
        String other="Bearer "+JwtFixture.token("auth0|other-lease-owner");
        jdbc.update("INSERT INTO owner_access(id,issuer,subject,enabled) VALUES (?,?,?,true)",UUID.randomUUID(),JwtFixture.ISSUER,"auth0|other-lease-owner");
        mvc.perform(get("/api/v1/leases").header("Authorization",other)).andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(get(path(id)).header("Authorization",other)).andExpect(status().isNotFound());
        mvc.perform(post(path(id)+"/draft").header("Authorization",other).contentType("application/json").content("{\"revision\":1,\"terms\":{}}"))
            .andExpect(status().isNotFound());
        mvc.perform(post(path(id)+"/confirm").header("Authorization",other).contentType("application/json").content("{\"revision\":1,\"confirmed\":true}"))
            .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/leases").header("Authorization",other).contentType("application/json").content("{\"requestId\":\""+id+"\"}"))
            .andExpect(status().isNotFound());
        mvc.perform(get(path(id))).andExpect(status().isUnauthorized());
    }
    @Test void previewIdentifiesInvalidDatesAndExpiredFirstRentWithoutCreatingASchedule() throws Exception {
        UUID id=create();
        save(id,0,terms().replace(TODAY.plusMonths(2).toString(),TODAY.minusDays(1).toString())).andExpect(status().isOk());
        assertThat(getLease(id).path("confirmationIssues").isEmpty()).isFalse();
        confirm(id,1,true).andExpect(status().isBadRequest());
        assertThat(countSchedules()).isZero();
        save(id,1,terms().replace(TODAY.plusDays(2).toString(),TODAY.minusDays(1).toString())).andExpect(status().isOk());
        confirm(id,2,true).andExpect(status().isBadRequest());
    }
    @Test void notesAndMoneyAreValidatedAndNeverImplicitlyCharged() throws Exception {
        UUID id=create();
        save(id,0,"{\"amount\":12.345}").andExpect(status().isBadRequest());
        save(id,0,"{\"amount\":-1}").andExpect(status().isBadRequest());
        save(id,0,terms()).andExpect(status().isOk());
        assertThat(getLease(id).path("previewDueDates").size()).isBetween(1,5);
        confirm(id,1,true).andExpect(status().isOk());
        assertThat(jdbc.queryForObject("SELECT amount FROM rent_schedule",java.math.BigDecimal.class)).isEqualByComparingTo("1200");
        verifyNoInteractions(paypal);
    }
    @Test void leaseRoutesArePublishedInTheOpenApiContract() throws Exception {
        JsonNode spec=json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(spec.path("paths").has("/api/v1/leases/{id}/confirm")).isTrue();
        assertThat(spec.path("paths").path("/api/v1/leases/{id}/draft").path("post").path("operationId").asText()).isEqualTo("saveLeaseDraft");
    }
    private int countSchedules(){return jdbc.queryForObject("SELECT count(*) FROM rent_schedule",Integer.class);}
    private String bearer(){return "Bearer "+JwtFixture.token("auth0|lease-owner");}
    private String path(UUID id){return "/api/v1/leases/"+id;}
    private UUID create() throws Exception {
        UUID id=UUID.randomUUID();
        mvc.perform(post("/api/v1/leases").header("Authorization",bearer()).contentType("application/json").content("{\"requestId\":\""+id+"\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.toString()));
        return id;
    }
    private JsonNode getLease(UUID id) throws Exception {return json.readTree(mvc.perform(get(path(id)).header("Authorization",bearer()))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());}
    private org.springframework.test.web.servlet.ResultActions save(UUID id,long revision,String terms) throws Exception {
        return mvc.perform(post(path(id)+"/draft").header("Authorization",bearer()).contentType("application/json")
            .content("{\"revision\":"+revision+",\"terms\":"+terms+"}"));
    }
    private org.springframework.test.web.servlet.ResultActions confirm(UUID id,long revision,boolean confirmed) throws Exception {
        return mvc.perform(post(path(id)+"/confirm").header("Authorization",bearer()).contentType("application/json")
            .content("{\"revision\":"+revision+",\"confirmed\":"+confirmed+"}"));
    }
    private String terms(){return """
        {"tenantName":"Taylor","suiteLabel":"Suite 8","startDate":"%s","endDate":"%s","amount":1200,
        "frequency":"MONTHLY","firstDueDate":"%s","lateFeeTerms":"$25 after five days; owner review required","furnishedItems":"Chair and mirror"}
        """.formatted(TODAY,TODAY.plusMonths(2),TODAY.plusDays(2));}
}
