package com.suitefolk.api.rent;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@io.swagger.v3.oas.annotations.responses.ApiResponses({
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="400",description="Invalid or unconfirmed payment record",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=org.springframework.http.ProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="401",description="Sign-in required",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=org.springframework.http.ProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="403",description="Owner access is not enabled",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=org.springframework.http.ProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="404",description="Owner's rent record was not found",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=org.springframework.http.ProblemDetail.class))),
    @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode="409",description="Stale balance or reconciliation required",content=@io.swagger.v3.oas.annotations.media.Content(mediaType="application/problem+json",schema=@io.swagger.v3.oas.annotations.media.Schema(implementation=org.springframework.http.ProblemDetail.class)))
})
@RestController
@RequestMapping(path="/api/v1/rent-schedules/{scheduleId}/ledger", produces="application/json")
public class RentLedgerController {
    private final RentLedgerService ledger;
    public RentLedgerController(RentLedgerService ledger) { this.ledger=ledger; }
    @Operation(operationId="getRentLedger",summary="List owner-scoped rent obligations and recorded payments")
    @GetMapping
    public ResponseEntity<List<RentPeriodView>> list(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID scheduleId) {
        return privateResponse(ledger.list(jwt,scheduleId));
    }
    @Operation(operationId="recordExternalRentPayment",summary="Confirm an owner-recorded outside payment and hold collection for that period")
    @PostMapping(path="/{periodId}/payments",consumes="application/json")
    public ResponseEntity<RentPeriodView> record(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID scheduleId,
            @PathVariable UUID periodId,@Valid @RequestBody RecordExternalPaymentRequest request) {
        return privateResponse(ledger.record(jwt,scheduleId,periodId,request));
    }
    @Operation(operationId="reverseExternalRentPayment",summary="Reverse an outside-payment record while preserving its audit history")
    @PostMapping(path="/{periodId}/payments/{paymentId}/reversal",consumes="application/json")
    public ResponseEntity<RentPeriodView> reverse(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID scheduleId,
            @PathVariable UUID periodId,@PathVariable UUID paymentId,@Valid @RequestBody ReverseExternalPaymentRequest request) {
        return privateResponse(ledger.reverse(jwt,scheduleId,periodId,paymentId,request));
    }
    @Operation(operationId="reviewRentPeriod",summary="Acknowledge the current balance without enabling automatic collection")
    @PostMapping(path="/{periodId}/review",consumes="application/json")
    public ResponseEntity<RentPeriodView> review(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID scheduleId,
            @PathVariable UUID periodId,@Valid @RequestBody ReviewRentPeriodRequest request) {
        return privateResponse(ledger.review(jwt,scheduleId,periodId,request));
    }
    private <T> ResponseEntity<T> privateResponse(T body) {
        return ResponseEntity.ok().header("Cache-Control","private, no-store").body(body);
    }
}
