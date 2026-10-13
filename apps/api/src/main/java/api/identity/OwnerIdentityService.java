package api.identity;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

@Service
public class OwnerIdentityService {

	private final OwnerAccessRepository repository;

	public OwnerIdentityService(OwnerAccessRepository repository) {
		this.repository = repository;
	}

	@Transactional(readOnly = true)
	public OwnerIdentity requireOwner(Jwt jwt) {
		if (jwt == null || jwt.getIssuer() == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
			throw new ErrorResponseException(HttpStatus.UNAUTHORIZED,
					ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Sign-in is required."), null);
		}
		OwnerAccess owner = repository.findByIssuerAndSubjectAndEnabledTrue(
				jwt.getIssuer().toString(), jwt.getSubject()).orElseThrow(() ->
				new ErrorResponseException(HttpStatus.FORBIDDEN,
						ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
								"Owner access has not been enabled for this account."), null));
		return new OwnerIdentity(owner.getId(), owner.getSubject(), "OWNER");
	}
}
