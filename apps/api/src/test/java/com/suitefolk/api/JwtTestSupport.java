package com.suitefolk.api;

import com.suitefolk.testing.JwtFixture;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

abstract class JwtTestSupport {
	@DynamicPropertySource
	static void jwtProperties(DynamicPropertyRegistry properties) {
		JwtFixture.properties(properties);
	}
}
