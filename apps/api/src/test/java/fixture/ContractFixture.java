package fixture;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// Outside the application's component scan; imported only by contract tests.
@TestConfiguration(proxyBeanMethods = false)
@Import(ContractFixture.Controller.class)
public class ContractFixture {

	@Bean
	UserDetailsService fixtureUsers() {
		return new InMemoryUserDetailsManager(User.withUsername("contract-test")
				.password("{noop}test-only-password").roles("TEST").build());
	}

	public record Request(@NotBlank String name, @NotNull @Positive Integer quantity) { }
	public record Response(String name, int quantity) { }

	@RestController
	public static class Controller {
		@Operation(operationId = "verifyContract", summary = "Test-only contract fixture")
		@ApiResponses({
				@ApiResponse(responseCode = "200", description = "Accepted", content =
						@Content(mediaType = "application/json", schema = @Schema(implementation = Response.class))),
				@ApiResponse(responseCode = "400", description = "Invalid request", content =
						@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
		})
		@PostMapping(path = "/api/v1/contract-fixture", consumes = "application/json", produces = "application/json")
		public Response verify(@Valid @RequestBody Request request) {
			return new Response(request.name(), request.quantity());
		}

		@GetMapping("/api/v1/contract-fixture/failure")
		@ApiResponse(responseCode = "500", description = "Unexpected failure", content =
				@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
		public void failure() {
			throw new IllegalStateException("sensitive-internal-fixture-detail");
		}

		@GetMapping("/internal/contract-fixture")
		public String internal() {
			return "excluded from the public contract";
		}

		@GetMapping("/api/v1/contract-fixture/quantity")
		public Response quantity(@RequestParam int quantity) {
			return new Response("Example", quantity);
		}
	}
}
