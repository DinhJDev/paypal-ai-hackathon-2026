package fixture;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpServer;
import org.springframework.test.context.DynamicPropertyRegistry;

public class JwtFixture {

	public static final String ISSUER = "https://issuer.example.test/";
	public static final String AUDIENCE = "https://api.suitefolk.example.test";
	private static final KeyPair KEYS = keys();
	private static final HttpServer SERVER = server();

	public static void properties(DynamicPropertyRegistry properties) {
			properties.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> ISSUER);
			properties.add("spring.security.oauth2.resourceserver.jwt.audiences", () -> AUDIENCE);
			properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () ->
					"http://127.0.0.1:" + SERVER.getAddress().getPort() + "/jwks");
	}

	public static String token(String subject) {
		return token(subject, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300));
	}

	public static String token(String subject, String issuer, List<String> audiences, Instant expiry) {
		return signed(subject, issuer, audiences, expiry, JWSAlgorithm.RS256, KEYS);
	}

	public static String wrongSignature(String subject) {
		return signed(subject, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300), JWSAlgorithm.RS256, keys());
	}

	public static String unsupportedAlgorithm(String subject) {
		return signed(subject, ISSUER, List.of(AUDIENCE), Instant.now().plusSeconds(300), JWSAlgorithm.RS512, KEYS);
	}

	private static String signed(String subject, String issuer, List<String> audiences, Instant expiry,
			JWSAlgorithm algorithm, KeyPair pair) {
		try {
			Instant issued = expiry.isBefore(Instant.now()) ? expiry.minusSeconds(60) : Instant.now().minusSeconds(30);
			var claims = new JWTClaimsSet.Builder().issuer(issuer).subject(subject).audience(audiences)
					.issueTime(Date.from(issued)).notBeforeTime(Date.from(issued))
					.expirationTime(Date.from(expiry)).claim("roles", List.of("OWNER")).build();
			var jwt = new SignedJWT(new JWSHeader.Builder(algorithm).keyID("generated-test-key").build(), claims);
			jwt.sign(new RSASSASigner((RSAPrivateKey) pair.getPrivate()));
			return jwt.serialize();
		} catch (Exception exception) {
			throw new IllegalStateException("Could not sign test credential", exception);
		}
	}

	private static KeyPair keys() {
		try {
			var generator = KeyPairGenerator.getInstance("RSA");
			generator.initialize(2048);
			return generator.generateKeyPair();
		} catch (Exception exception) { throw new IllegalStateException(exception); }
	}

	private static HttpServer server() {
		try {
			var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
			String jwk = new RSAKey.Builder((RSAPublicKey) KEYS.getPublic()).keyID("generated-test-key").build().toJSONString();
			byte[] body = ("{\"keys\":[" + jwk + "]}").getBytes(StandardCharsets.UTF_8);
			server.createContext("/jwks", exchange -> {
				exchange.getResponseHeaders().add("Content-Type", "application/json");
				exchange.sendResponseHeaders(200, body.length);
				try (var output = exchange.getResponseBody()) { output.write(body); }
			});
			server.start();
			Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
			return server;
		} catch (Exception exception) { throw new IllegalStateException(exception); }
	}
}
