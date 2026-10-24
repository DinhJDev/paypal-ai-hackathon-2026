package com.suitefolk.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
class OpenApiDisabledTests extends JwtTestSupport {

	@Autowired MockMvc mvc;

	@Test
	void documentationIsUnregisteredOutsideLocalEvenForAuthenticatedUsers() throws Exception {
		for (String path : new String[]{"/v3/api-docs", "/v3/api-docs.yaml", "/v3/api-docs/swagger-config",
				"/swagger-ui.html", "/swagger-ui/index.html"}) {
			mvc.perform(get(path).with(user("test-only"))).andExpect(status().isNotFound());
		}
	}

	@Test
	void keepsDefaultAuthenticationAndDoesNotExposeTestFixtures() throws Exception {
		mvc.perform(get("/api/v1/contract-fixture/failure").accept("application/json"))
				.andExpect(status().isUnauthorized());
		mvc.perform(get("/api/v1/contract-fixture/failure").with(user("test-only")))
				.andExpect(status().isNotFound());
	}
}
