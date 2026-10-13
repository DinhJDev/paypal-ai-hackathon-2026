package api;

import com.networknt.schema.InputFormat;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import fixture.ContractFixture;
import fixture.JwtFixture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.yaml.snakeyaml.Yaml;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles({"test", "local"})
@Import({TestcontainersConfiguration.class, ContractFixture.class})
@SpringBootTest(properties = "spring.datasource.password=test-only-password")
@AutoConfigureMockMvc
class OpenApiContractTests extends JwtTestSupport {

	@Autowired MockMvc mvc;
	private final JsonMapper json = JsonMapper.builder().build();
	private final SchemaRegistry schemas = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);

	@Test
	void publishesValidOpenApi31WithMetadataAndEquivalentYaml() throws Exception {
		JsonNode spec = specification();
		assertThat(spec.path("openapi").asString()).isEqualTo("3.1.0");
		assertThat(spec.path("info").path("title").asString()).isEqualTo("Suitefolk API");
		assertThat(spec.path("info").path("version").asString()).isEqualTo("v1");
		assertThat(spec.path("info").path("license").path("identifier").asString()).isEqualTo("Apache-2.0");
		try (var input = getClass().getResourceAsStream("/openapi/oas-3.1-schema.json")) {
			assertThat(input).isNotNull();
			assertThat(schemas.getSchema(input, InputFormat.JSON).validate(spec)).isEmpty();
		}
		String yaml = mvc.perform(get("/v3/api-docs.yaml")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		JsonNode yamlTree = json.valueToTree(new Yaml().load(yaml));
		assertThat(yamlTree).isEqualTo(spec);
		assertLocalReferencesResolve(spec, spec);
		assertThat(spec.path("paths").has("/internal/contract-fixture")).isFalse();
		assertThat(spec.path("paths").has("/actuator/health")).isFalse();
	}

	@Test
	void documentsDtoConstraintsAndExplicitResponses() throws Exception {
		JsonNode spec = specification();
		JsonNode request = spec.path("components").path("schemas").path("Request");
		assertThat(request.path("required").toString()).contains("name", "quantity");
		assertThat(request.path("properties").path("quantity").path("type").asString()).isEqualTo("integer");
		assertThat(request.path("properties").path("quantity").path("exclusiveMinimum").isNumber()).isTrue();
		assertThat(request.path("properties").path("quantity").path("exclusiveMinimum").asInt()).isZero();
		JsonNode operation = spec.path("paths").path("/api/v1/contract-fixture").path("post");
		assertThat(operation.path("operationId").asString()).isEqualTo("verifyContract");
		assertThat(operation.path("responses").has("200")).isTrue();
		assertThat(operation.path("responses").has("400")).isTrue();
		JsonNode problem = spec.path("components").path("schemas").path("ProblemDetail");
		assertThat(problem.path("properties").has("properties")).isFalse();
		assertThat(problem.path("properties").has("errors")).isTrue();
	}

	@Test
	void servesSwaggerUiAndItsConfigurationWithoutAuthentication() throws Exception {
		mvc.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection())
				.andExpect(redirectedUrl("/swagger-ui/index.html"));
		mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk())
				.andExpect(content().string(org.hamcrest.Matchers.containsString("swagger-ui")));
		mvc.perform(get("/swagger-ui/swagger-ui.css")).andExpect(status().isOk());
		mvc.perform(get("/swagger-ui/swagger-ui-bundle.js")).andExpect(status().isOk());
		mvc.perform(get("/swagger-ui/swagger-initializer.js")).andExpect(status().isOk());
		mvc.perform(get("/v3/api-docs/swagger-config")).andExpect(status().isOk())
				.andExpect(jsonPath("$.url").value("/v3/api-docs"));
	}

	@Test
	void keepsAuthenticationAndAllowsBearerOnlyApiRequests() throws Exception {
		mvc.perform(post("/api/v1/contract-fixture").with(csrf()).accept("application/json")
				.contentType("application/json").content(validRequest())).andExpect(status().isUnauthorized());
		mvc.perform(post("/api/v1/contract-fixture").header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test"))
				.contentType("application/json").content(validRequest())).andExpect(status().isOk());
		mvc.perform(post("/internal/contract-fixture").header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test")))
				.andExpect(status().isForbidden());
	}

	@Test
	void successfulResponseMatchesPublishedSchema() throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/contract-fixture")
				.header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test")).with(csrf())
				.contentType("application/json").content(validRequest())).andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("Example"))
				.andExpect(jsonPath("$.quantity").value(2)).andReturn();
		assertResponseMatchesSchema(result, "200", "application/json");
	}

	@Test
	void validationErrorsAreSafeProblemDetailsMatchingPublishedSchema() throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/contract-fixture")
				.header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test")).with(csrf())
				.contentType("application/json").content("{\"name\":\"\",\"quantity\":0}"))
				.andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.title").value("Bad Request"))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.instance").value("/api/v1/contract-fixture"))
				.andExpect(jsonPath("$.errors[0].field").value("name"))
				.andExpect(jsonPath("$.errors[1].field").value("quantity"))
				.andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist()).andReturn();
		assertResponseMatchesSchema(result, "400", "application/problem+json");
	}

	@Test
	void malformedJsonDoesNotExposeParserDetailsOrInput() throws Exception {
		MvcResult result = mvc.perform(post("/api/v1/contract-fixture")
				.header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test")).with(csrf())
				.contentType("application/json").content("{\"private-token-marker\":"))
				.andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.detail").value("Request body is missing or contains invalid JSON."))
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("private-token-marker", "Exception");
		assertResponseMatchesSchema(result, "400", "application/problem+json");
	}

	@Test
	void invalidParametersDoNotExposeTheirValues() throws Exception {
		MvcResult result = mvc.perform(get("/api/v1/contract-fixture/quantity")
				.param("quantity", "private-token-marker")
				.header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.detail").value("Request parameter is invalid."))
				.andExpect(jsonPath("$.instance").value("/api/v1/contract-fixture/quantity"))
				.andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("private-token-marker", "Exception");
	}

	@Test
	void unexpectedFailuresAreSanitizedProblemDetails() throws Exception {
		MvcResult result = mvc.perform(get("/api/v1/contract-fixture/failure")
				.header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test")))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
				.andExpect(jsonPath("$.status").value(500)).andReturn();
		assertThat(result.getResponse().getContentAsString()).doesNotContain("sensitive-internal", "IllegalStateException", "trace");
		assertResponseMatchesSchema(result, "/api/v1/contract-fixture/failure", "get", "500", "application/problem+json");
	}

	@Test
	void mvcFailuresUseProblemDetails() throws Exception {
		mvc.perform(put("/api/v1/contract-fixture").header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test")).with(csrf()))
				.andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.status").value(405));
		mvc.perform(post("/api/v1/contract-fixture").header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test")).with(csrf())
				.contentType("text/plain").content("Example"))
				.andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415));
		mvc.perform(get("/api/v1/missing").header("Authorization", "Bearer " + JwtFixture.token("auth0|contract-test")))
				.andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
	}

	private String validRequest() {
		return "{\"name\":\"Example\",\"quantity\":2}";
	}

	private JsonNode specification() throws Exception {
		return json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
	}

	private void assertResponseMatchesSchema(MvcResult result, String status, String mediaType) throws Exception {
		assertResponseMatchesSchema(result, "/api/v1/contract-fixture", "post", status, mediaType);
	}

	private void assertResponseMatchesSchema(MvcResult result, String path, String method,
			String status, String mediaType) throws Exception {
		JsonNode spec = specification();
		JsonNode response = spec.path("paths").path(path).path(method)
				.path("responses").path(status).path("content").path(mediaType).path("schema");
		assertThat(response.isMissingNode()).isFalse();
		JsonNode schema = json.valueToTree(Map.of("$ref", response.path("$ref").asString(), "components", spec.path("components")));
		assertThat(schemas.getSchema(schema).validate(json.readTree(result.getResponse().getContentAsString()),
				context -> context.executionConfig(config -> config.formatAssertionsEnabled(true)))).isEmpty();
	}

	private void assertLocalReferencesResolve(JsonNode node, JsonNode document) {
		if (node.isObject() && node.has("$ref")) {
			String reference = node.path("$ref").asString();
			assertThat(reference).startsWith("#/");
			assertThat(document.at(reference.substring(1)).isMissingNode()).as(reference).isFalse();
		}
		for (JsonNode child : node) {
			assertLocalReferencesResolve(child, document);
		}
	}
}
