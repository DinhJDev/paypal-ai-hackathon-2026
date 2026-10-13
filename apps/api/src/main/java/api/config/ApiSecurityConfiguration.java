package api.config;

import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
public class ApiSecurityConfiguration {

	@Bean
	JwtDecoder apiJwtDecoder(
			@Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}") String issuer,
			@Value("${spring.security.oauth2.resourceserver.jwt.audiences}") String audience,
			@Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String keys) {
		var decoder = NimbusJwtDecoder.withJwkSetUri(keys).jwsAlgorithm(SignatureAlgorithm.RS256).build();
		decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
				JwtValidators.createDefaultWithIssuer(issuer),
				new JwtClaimValidator<List<String>>("aud", values -> values != null && values.contains(audience)),
				new JwtClaimValidator<String>("sub", value -> value != null && !value.isBlank())));
		return decoder;
	}

	@Bean
	SecurityFilterChain apiSecurity(HttpSecurity http, Environment environment,
			ApiAuthenticationEntryPoint entryPoint) throws Exception {
		return http.authorizeHttpRequests(requests -> {
				requests.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll();
				if (environment.matchesProfiles("local")) {
					requests.requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
							"/swagger-ui.html", "/swagger-ui/**").permitAll();
				}
				requests.anyRequest().authenticated();
			})
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// API credentials are bearer headers; browser-facing routes retain CSRF protection.
			.csrf(csrf -> csrf.ignoringRequestMatchers("/api/**"))
			.exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint))
			.oauth2ResourceServer(resource -> resource.jwt(jwt -> { }).authenticationEntryPoint(entryPoint))
			.build();
	}
}
