package api.identity;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IdentityController {

	private final OwnerIdentityService identities;

	public IdentityController(OwnerIdentityService identities) { this.identities = identities; }

	@Operation(operationId = "getOwnerIdentity", summary = "Get the authenticated owner's identity")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Enabled owner", content =
					@Content(mediaType = "application/json", schema = @Schema(implementation = OwnerIdentity.class))),
			@ApiResponse(responseCode = "401", description = "Missing or invalid credentials", content =
					@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "Owner access is not enabled", content =
					@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	})
	@GetMapping(path = "/api/v1/me", produces = "application/json")
	public ResponseEntity<OwnerIdentity> me(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().header("Cache-Control", "private, no-store").body(identities.requireOwner(jwt));
	}
}
