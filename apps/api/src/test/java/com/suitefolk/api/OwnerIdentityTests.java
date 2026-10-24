package com.suitefolk.api;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
class OwnerIdentityTests extends JwtTestSupport {

	private static final String ISSUER = "https://issuer.example.test/";
	@Autowired MockMvc mvc;
	@Autowired JdbcTemplate jdbc;

	@BeforeEach
	void clearOwners() {
		if (tableExists()) jdbc.update("DELETE FROM owner_access");
	}

	@Test
	void migrationCreatesUniqueOwnerIdentity() {
		assertThat(tableExists()).isTrue();
		insert("auth0|test-owner", true);
		assertThatThrownBy(() -> insert("auth0|test-owner", true))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void enabledOwnerReceivesIdentityWithoutCaching() throws Exception {
		assertThat(tableExists()).isTrue();
		UUID id = insert("auth0|test-owner", true);
		mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
				.issuer(ISSUER).subject("auth0|test-owner"))))
				.andExpect(status().isOk()).andExpect(header().string("Cache-Control", "private, no-store"))
				.andExpect(jsonPath("$.ownerId").value(id.toString()))
				.andExpect(jsonPath("$.subject").value("auth0|test-owner"))
				.andExpect(jsonPath("$.role").value("OWNER"));
	}

	@Test
	void tokenRoleCannotGrantOwnerAccess() throws Exception {
		mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
				.issuer(ISSUER).subject("auth0|unprovisioned-test-owner").claim("roles", List.of("OWNER")))))
				.andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
	}

	@Test
	void disabledOwnerIsDenied() throws Exception {
		assertThat(tableExists()).isTrue();
		insert("auth0|disabled-test-owner", false);
		mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
				.issuer(ISSUER).subject("auth0|disabled-test-owner"))))
				.andExpect(status().isForbidden());
	}

	@Test
	void subjectCannotMatchAnOwnerFromAnotherIssuer() throws Exception {
		assertThat(tableExists()).isTrue();
		insert("auth0|test-owner", true);
		mvc.perform(get("/api/v1/me").with(jwt().jwt(token -> token
				.issuer("https://other-issuer.example.test/").subject("auth0|test-owner"))))
				.andExpect(status().isForbidden());
	}

	private boolean tableExists() {
		return Boolean.TRUE.equals(jdbc.queryForObject(
				"SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'owner_access')", Boolean.class));
	}

	private UUID insert(String subject, boolean enabled) {
		return jdbc.queryForObject("INSERT INTO owner_access (issuer, subject, enabled) VALUES (?, ?, ?) RETURNING id",
				UUID.class, ISSUER, subject, enabled);
	}
}
