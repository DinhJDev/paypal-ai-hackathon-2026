package com.suitefolk.api.config;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.server.resource.BearerTokenError;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
	private final JsonMapper json;

	public ApiAuthenticationEntryPoint(JsonMapper json) { this.json = json; }

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		AuthenticationException safe = exception;
		if (exception instanceof OAuth2AuthenticationException oauth) {
			HttpStatus status = oauth.getError() instanceof BearerTokenError error ? error.getHttpStatus() : HttpStatus.UNAUTHORIZED;
			safe = new OAuth2AuthenticationException(new BearerTokenError(oauth.getError().getErrorCode(), status,
					"Authentication credentials are invalid.", null));
		}
		bearer.commence(request, response, safe);
		int status = response.getStatus();
		response.setHeader("Cache-Control", "private, no-store");
		response.setContentType("application/problem+json");
		json.writeValue(response.getOutputStream(), new AuthenticationProblem("about:blank",
				HttpStatus.valueOf(status).getReasonPhrase(), status,
				"Sign-in is required or the access token is invalid.", request.getRequestURI()));
	}

	private record AuthenticationProblem(String type, String title, int status, String detail, String instance) { }
}
