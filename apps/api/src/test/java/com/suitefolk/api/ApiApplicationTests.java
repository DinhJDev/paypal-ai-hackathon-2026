package com.suitefolk.api;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles({"test", "local"})
@Import({TestcontainersConfiguration.class})
@SpringBootTest(properties = "spring.datasource.password=test-only-password")
@AutoConfigureMockMvc
class ApiApplicationTests extends JwtTestSupport {

	@Autowired MockMvc mvc;

	@Test
	void applicationPublishesOnlyItsShippedContractWithoutTestFixtures() throws Exception {
		var spec = JsonMapper.builder().build().readTree(mvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
		assertThat(spec.path("paths").size()).isEqualTo(16);
        assertThat(spec.path("paths").has("/api/v1/leases")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/leases/{id}")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/leases/{id}/draft")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/leases/{id}/confirm")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/rent-schedules/{id}/autopay-setup")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/rent-schedules/{scheduleId}/ledger")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/rent-schedules/{scheduleId}/ledger/{periodId}/payments")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/rent-schedules/{scheduleId}/ledger/{periodId}/payments/{paymentId}/reversal")).isTrue();
        assertThat(spec.path("paths").has("/api/v1/rent-schedules/{scheduleId}/ledger/{periodId}/review")).isTrue();
		assertThat(spec.path("paths").has("/api/v1/me")).isTrue();
		assertThat(spec.path("paths").has("/api/v1/rent-schedules")).isTrue();
		assertThat(spec.path("paths").has("/api/v1/rent-schedules/{id}")).isTrue();
		assertThat(spec.path("paths").has("/api/v1/rent-schedules/{id}/approval-check")).isTrue();
		assertThat(spec.path("paths").has("/api/v1/rent-schedules/{id}/pause")).isTrue();
		assertThat(spec.path("paths").has("/api/v1/rent-schedules/paypal/return")).isTrue();
		assertThat(spec.path("paths").has("/api/v1/rent-schedules/paypal/webhook")).isTrue();
		try (var input = getClass().getResourceAsStream("/openapi/oas-3.1-schema.json")) {
			var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
			assertThat(registry.getSchema(input, InputFormat.JSON).validate(spec)).isEmpty();
		}
	}

}
