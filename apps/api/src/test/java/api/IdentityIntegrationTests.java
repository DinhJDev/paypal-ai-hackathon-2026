package api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.Map;

import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;

import fixture.JwtFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles({"test", "local"})
@Import({TestcontainersConfiguration.class})
@SpringBootTest(properties = "spring.datasource.password=test-only-password")
@AutoConfigureMockMvc
class IdentityIntegrationTests extends JwtTestSupport {

	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void provisionTestOwner() {
		jdbc.update("DELETE FROM owner_access");
		jdbc.update("INSERT INTO owner_access (issuer, subject) VALUES (?, ?)", JwtFixture.ISSUER, "auth0|signed-test-owner");
	}

	@Test
	void validatesSignedAccessTokenAndReturnsPublishedIdentity() throws Exception {
		UUID id = jdbc.queryForObject("SELECT id FROM owner_access WHERE subject = ?", UUID.class, "auth0|signed-test-owner");
		var result = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + JwtFixture.token("auth0|signed-test-owner")))
				.andExpect(status().isOk()).andExpect(jsonPath("$.ownerId").value(id.toString()))
				.andExpect(jsonPath("$.role").value("OWNER")).andReturn();
		assertPublishedSchema(result, "200", "application/json");
	}

	@Test
	void missingTokenHasSafeProblemDetailsAndBearerChallenge() throws Exception {
		var result = mvc.perform(get("/api/v1/me")).andExpect(status().isUnauthorized())
				.andExpect(content().contentTypeCompatibleWith("application/problem+json"))
				.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
				.andExpect(jsonPath("$.type").value("about:blank"))
				.andExpect(jsonPath("$.status").value(401)).andReturn();
		assertPublishedSchema(result, "401", "application/problem+json");
	}

	@Test
	void rejectsInvalidCredentialsWithoutDecoderDetails() throws Exception {
		List<String> invalid = List.of(
				JwtFixture.wrongSignature("auth0|signed-test-owner"),
				JwtFixture.unsupportedAlgorithm("auth0|signed-test-owner"),
				JwtFixture.token("auth0|signed-test-owner", JwtFixture.ISSUER, List.of(JwtFixture.AUDIENCE), Instant.now().minusSeconds(300)),
				JwtFixture.token("auth0|signed-test-owner", "https://wrong.example.test/", List.of(JwtFixture.AUDIENCE), Instant.now().plusSeconds(300)),
				JwtFixture.token("auth0|signed-test-owner", JwtFixture.ISSUER, List.of("test-web-client-id"), Instant.now().plusSeconds(300)),
				JwtFixture.token(null), "private-token-marker");
		for (String token : invalid) {
			var response = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + token))
					.andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401))
					.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.startsWith("Bearer")))
					.andReturn().getResponse();
			assertThat(response.getContentAsString()).doesNotContain(token, "Exception", "decode", "signature", "jwks");
			assertThat(response.getHeader("WWW-Authenticate")).doesNotContain(token, "Exception", "decode", "signature", "jwks");
		}
	}

	@Test
	void validTokenCannotSelfProvisionOwnerAccess() throws Exception {
		var result = mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + JwtFixture.token("auth0|not-enabled-test-owner")))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403)).andReturn();
		assertPublishedSchema(result, "403", "application/problem+json");
		assertThat(jdbc.queryForObject("SELECT count(*) FROM owner_access", Integer.class)).isEqualTo(1);
		jdbc.update("UPDATE owner_access SET enabled = false");
		mvc.perform(get("/api/v1/me").header("Authorization", "Bearer " + JwtFixture.token("auth0|signed-test-owner")))
				.andExpect(status().isForbidden());
	}

	@Test
	void sessionAuthenticationCannotReplaceAnApiAccessToken() throws Exception {
		var session = new MockHttpSession();
		var context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated("test-owner", null, List.of()));
		session.setAttribute("SPRING_SECURITY_CONTEXT", context);
		mvc.perform(get("/api/v1/me").session(session)).andExpect(status().isUnauthorized());
	}

	private void assertPublishedSchema(MvcResult result, String status, String media) throws Exception {
		var json = JsonMapper.builder().build();
		JsonNode spec = json.readTree(mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString());
		assertThat(spec.path("components").path("securitySchemes").path("bearerAuth").path("scheme").asString()).isEqualTo("bearer");
		assertThat(spec.path("security").toString()).contains("bearerAuth");
		String reference = spec.path("paths").path("/api/v1/me").path("get").path("responses")
				.path(status).path("content").path(media).path("schema").path("$ref").asString();
		assertThat(reference).startsWith("#/components/schemas/");
		JsonNode schema = json.valueToTree(Map.of("$ref", reference, "components", spec.path("components")));
		var registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
		assertThat(registry.getSchema(schema).validate(json.readTree(result.getResponse().getContentAsString()),
				execution -> execution.executionConfig(config -> config.formatAssertionsEnabled(true)))).isEmpty();
	}
}
