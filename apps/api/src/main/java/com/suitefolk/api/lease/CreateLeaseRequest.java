package com.suitefolk.api.lease;
import java.util.UUID;
import jakarta.validation.constraints.NotNull;
public record CreateLeaseRequest(@NotNull UUID requestId) { }
