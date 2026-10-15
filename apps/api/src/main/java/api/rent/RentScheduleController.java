package api.rent;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

@RestController
@RequestMapping(path = "/api/v1/rent-schedules")
public class RentScheduleController {

	private final RentScheduleService schedules;
	private final PayPalClient paypal;
	private final PayPalProperties properties;

	public RentScheduleController(RentScheduleService schedules, PayPalClient paypal, PayPalProperties properties) {
		this.schedules = schedules;
		this.paypal = paypal;
		this.properties = properties;
	}

	@Operation(operationId = "createRentSchedule", summary = "Create an owner rent schedule and PayPal sandbox approval link")
	@ApiResponses({
			@ApiResponse(responseCode = "201", description = "Rent schedule created", content =
					@Content(mediaType = "application/json", schema = @Schema(implementation = RentScheduleView.class))),
			@ApiResponse(responseCode = "400", description = "Invalid schedule", content =
					@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "401", description = "Sign-in required", content =
					@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "403", description = "Owner access is not enabled", content =
					@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class))),
			@ApiResponse(responseCode = "503", description = "PayPal sandbox is unavailable", content =
					@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	})
	@PostMapping(consumes = "application/json", produces = "application/json")
	public ResponseEntity<RentScheduleView> create(@AuthenticationPrincipal Jwt jwt,
			@Valid @RequestBody CreateRentScheduleRequest request) {
		RentScheduleView schedule = schedules.create(jwt, request);
		return ResponseEntity.created(URI.create("/api/v1/rent-schedules/" + schedule.id()))
				.header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(schedule);
	}

	@Operation(operationId = "listRentSchedules", summary = "List the authenticated owner's rent schedules")
	@GetMapping(produces = "application/json")
	public ResponseEntity<List<RentScheduleView>> list(@AuthenticationPrincipal Jwt jwt) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(schedules.list(jwt));
	}

	@Operation(operationId = "getRentSchedule", summary = "Get one rent schedule")
	@ApiResponses({
			@ApiResponse(responseCode = "200", description = "Rent schedule", content =
					@Content(mediaType = "application/json", schema = @Schema(implementation = RentScheduleView.class))),
			@ApiResponse(responseCode = "404", description = "Schedule not found", content =
					@Content(mediaType = "application/problem+json", schema = @Schema(implementation = ProblemDetail.class)))
	})
	@GetMapping(path = "/{id}", produces = "application/json")
	public ResponseEntity<RentScheduleView> get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(schedules.get(jwt, id));
	}

	@Operation(operationId = "checkRentScheduleApproval", summary = "Check PayPal approval and activate autopay")
	@PostMapping(path = "/{id}/approval-check", produces = "application/json")
	public ResponseEntity<RentScheduleView> checkApproval(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(schedules.checkApproval(jwt, id));
	}

	@Operation(operationId = "pauseRentSchedule", summary = "Pause a rent schedule")
	@PostMapping(path = "/{id}/pause", produces = "application/json")
	public ResponseEntity<RentScheduleView> pause(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
		return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL, "private, no-store").body(schedules.pause(jwt, id));
	}

	@Operation(operationId = "completePayPalRentApproval", summary = "Complete a PayPal vault approval")
	@ApiResponses({
			@ApiResponse(responseCode = "302", description = "Redirect to the Suitefolk approval result"),
			@ApiResponse(responseCode = "503", description = "PayPal sandbox is unavailable")
	})
	@SecurityRequirements
	@GetMapping(path = "/paypal/return")
	public ResponseEntity<Void> paypalReturn(@RequestParam(name = "token", required = false) String token) {
		String result = "failed";
		if (token != null && token.length() <= 64 && schedules.completeApproval(token)) result = "approved";
		URI target = UriComponentsBuilder.fromUriString(properties.webBaseUrl())
				.path("/rent/paypal/return").queryParam("status", result).build().toUri();
		return ResponseEntity.status(HttpStatus.FOUND).location(target)
			.header(HttpHeaders.CACHE_CONTROL, "private, no-store").build();
	}

	@Operation(operationId = "receivePayPalWebhook", summary = "Receive a signature-verified PayPal sandbox event")
	@ApiResponses({
			@ApiResponse(responseCode = "202", description = "Event accepted or already processed"),
			@ApiResponse(responseCode = "401", description = "Webhook signature is invalid"),
			@ApiResponse(responseCode = "400", description = "Invalid event")
	})
	@SecurityRequirements
	@PostMapping(path = "/paypal/webhook", consumes = "application/json")
	public ResponseEntity<Void> paypalWebhook(@RequestHeader HttpHeaders headers, @RequestBody JsonNode event) {
		Map<String, String> transmission = Map.of(
				"paypal-transmission-id", header(headers, "paypal-transmission-id"),
				"paypal-transmission-time", header(headers, "paypal-transmission-time"),
				"paypal-cert-url", header(headers, "paypal-cert-url"),
				"paypal-auth-algo", header(headers, "paypal-auth-algo"),
				"paypal-transmission-sig", header(headers, "paypal-transmission-sig"));
		if (transmission.values().stream().anyMatch(String::isBlank) || !paypal.verifyWebhook(transmission, event)) {
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}
		if (!schedules.recordVerifiedWebhook(event)) return ResponseEntity.badRequest().build();
		return ResponseEntity.accepted().header(HttpHeaders.CACHE_CONTROL, "no-store").build();
	}

	private String header(HttpHeaders headers, String name) {
		String value = headers.getFirst(name);
		return value == null ? "" : value;
	}
}
