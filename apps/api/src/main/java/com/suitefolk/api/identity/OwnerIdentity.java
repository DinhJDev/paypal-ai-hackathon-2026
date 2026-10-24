package com.suitefolk.api.identity;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record OwnerIdentity(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID ownerId,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String subject,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = "OWNER") String role) { }
