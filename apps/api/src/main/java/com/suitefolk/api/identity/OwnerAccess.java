package com.suitefolk.api.identity;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "owner_access")
public class OwnerAccess {

	@Id
	private UUID id;
	@Column(nullable = false, length = 512)
	private String issuer;
	@Column(nullable = false)
	private String subject;
	@Column(nullable = false)
	private boolean enabled;

	protected OwnerAccess() { }

	public UUID getId() { return id; }
	public String getSubject() { return subject; }
}
