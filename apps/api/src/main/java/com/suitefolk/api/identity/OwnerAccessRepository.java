package com.suitefolk.api.identity;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

public interface OwnerAccessRepository extends Repository<OwnerAccess, UUID> {
	Optional<OwnerAccess> findByIssuerAndSubjectAndEnabledTrue(String issuer, String subject);
}
