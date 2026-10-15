package api.config;

import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.annotation.Order;
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
	@Order(1)
	SecurityFilterChain apiSecurity(HttpSecurity http,
			ApiAuthenticationEntryPoint entryPoint) throws Exception {
		return http.securityMatcher("/api/**")
			.authorizeHttpRequests(requests -> requests
				.requestMatchers("/api/v1/rent-schedules/paypal/return", "/api/v1/rent-schedules/paypal/webhook").permitAll()
				.anyRequest().authenticated())
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
			// API credentials are bearer headers; the fallback chain retains CSRF protection.
			.csrf(csrf -> csrf.disable())
			.exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint))
			.oauth2ResourceServer(resource -> resource.jwt(jwt -> { }).authenticationEntryPoint(entryPoint))
			.build();
	}

	@Bean
	@Order(2)
	SecurityFilterChain nonApiSecurity(HttpSecurity http, Environment environment,
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
			.exceptionHandling(errors -> errors.authenticationEntryPoint(entryPoint))
			.build();
	}
}
