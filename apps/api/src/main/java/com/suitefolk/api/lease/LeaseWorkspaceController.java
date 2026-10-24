package com.suitefolk.api.lease;

import java.util.List;
import java.util.UUID;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(path="/api/v1/leases",produces="application/json")
@ApiResponses({
    @ApiResponse(responseCode="400",description="Invalid lease details or confirmation",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),
    @ApiResponse(responseCode="401",description="Sign-in required",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),
    @ApiResponse(responseCode="403",description="Owner access required",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),
    @ApiResponse(responseCode="404",description="Lease not found",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class))),
    @ApiResponse(responseCode="409",description="Saved revision changed or lease already confirmed",content=@Content(mediaType="application/problem+json",schema=@Schema(implementation=ProblemDetail.class)))
})
public class LeaseWorkspaceController {
    private final LeaseWorkspaceService leases;
    public LeaseWorkspaceController(LeaseWorkspaceService leases){this.leases=leases;}
    @Operation(operationId="createLeaseWorkspace",summary="Create an incomplete owner lease draft without scheduling payments")
    @PostMapping(consumes="application/json")
    public ResponseEntity<LeaseWorkspaceView> create(@AuthenticationPrincipal Jwt jwt,@Valid @RequestBody CreateLeaseRequest request) {
        return privateResponse(leases.create(jwt,request));
    }
    @Operation(operationId="listLeaseWorkspaces",summary="List the authenticated owner's saved leases")
    @GetMapping
    public ResponseEntity<List<LeaseWorkspaceView>> list(@AuthenticationPrincipal Jwt jwt){return privateResponse(leases.list(jwt));}
    @Operation(operationId="getLeaseWorkspace",summary="Reopen a saved lease draft or confirmed snapshot")
    @GetMapping("/{id}")
    public ResponseEntity<LeaseWorkspaceView> get(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id){return privateResponse(leases.get(jwt,id));}
    @Operation(operationId="saveLeaseDraft",summary="Save incomplete manual terms against the current revision")
    @PostMapping(path="/{id}/draft",consumes="application/json")
    public ResponseEntity<LeaseWorkspaceView> save(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody SaveLeaseDraftRequest request){return privateResponse(leases.save(jwt,id,request));}
    @Operation(operationId="confirmLeaseWorkspace",summary="Confirm reviewed terms and create one end-bounded rent schedule without autopay")
    @PostMapping(path="/{id}/confirm",consumes="application/json")
    public ResponseEntity<LeaseWorkspaceView> confirm(@AuthenticationPrincipal Jwt jwt,@PathVariable UUID id,@Valid @RequestBody ConfirmLeaseRequest request){return privateResponse(leases.confirm(jwt,id,request));}
    private <T> ResponseEntity<T> privateResponse(T value){return ResponseEntity.ok().header(HttpHeaders.CACHE_CONTROL,"private, no-store").body(value);}
}
