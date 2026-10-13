package api.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

	@Bean
	OpenAPI suitefolkOpenApi() {
		return new OpenAPI().components(new Components().addSecuritySchemes("bearerAuth",
				new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")))
				.addSecurityItem(new SecurityRequirement().addList("bearerAuth"))
				.info(new Info().title("Suitefolk API").version("v1")
				.description("Suitefolk HTTP API. Business endpoints are added as features are implemented.")
				.license(new License().name("Apache License 2.0").identifier("Apache-2.0")));
	}

	@Bean
	OpenApiCustomizer problemDetailSchema() {
		return openApi -> {
			if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null
					|| !openApi.getComponents().getSchemas().containsKey("ProblemDetail")) {
				return;
			}
			var fieldError = new ObjectSchema()
					.addProperty("field", new StringSchema())
					.addProperty("message", new StringSchema());
			fieldError.setRequired(List.of("field", "message"));
			// Spring serializes ProblemDetail extensions at the top level, not in a properties wrapper.
			var problem = new ObjectSchema()
					.addProperty("type", new StringSchema().format("uri-reference"))
					.addProperty("title", new StringSchema())
					.addProperty("status", new IntegerSchema())
					.addProperty("detail", new StringSchema())
					.addProperty("instance", new StringSchema().format("uri-reference"))
					.addProperty("errors", new ArraySchema().items(fieldError));
			problem.setDescription("RFC 9457 Problem Details. Validation failures may include field errors.");
			problem.setRequired(List.of("type", "title", "status"));
			problem.setAdditionalProperties(true);
			openApi.getComponents().addSchemas("ProblemDetail", problem);
		};
	}
}
