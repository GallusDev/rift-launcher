package rift.launcher.web;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * Reading the {@code sub} claim, which is how the launcher decides whether a developer key belongs to
 * the signed-in account. Anything unreadable must come back null so the ownership check fails closed.
 */
public class JwtTest
{
	private static String token(String payloadJson)
	{
		String header = base64Url("{\"alg\":\"HS256\",\"typ\":\"JWT\"}");
		return header + "." + base64Url(payloadJson) + ".signature-not-checked";
	}

	private static String base64Url(String s)
	{
		return Base64.getUrlEncoder().withoutPadding()
			.encodeToString(s.getBytes(StandardCharsets.UTF_8));
	}

	@Test
	public void readsTheSubjectClaim()
	{
		String jwt = token("{\"sub\":\"11111111-2222-3333-4444-555555555555\",\"role\":\"authenticated\"}");
		assertEquals("11111111-2222-3333-4444-555555555555", Jwt.subject(jwt));
	}

	@Test
	public void handlesPayloadNeedingBase64UrlAlphabet()
	{
		// '-' and '_' appear in base64url output; the standard decoder would reject them.
		String jwt = token("{\"sub\":\"user-id_with~padding??\",\"email\":\"a+b@example.com\"}");
		assertEquals("user-id_with~padding??", Jwt.subject(jwt));
	}

	@Test
	public void missingOrEmptySubjectIsNull()
	{
		assertNull(Jwt.subject(token("{\"role\":\"authenticated\"}")));
		assertNull(Jwt.subject(token("{\"sub\":\"\"}")));
		assertNull(Jwt.subject(token("{\"sub\":null}")));
	}

	@Test
	public void malformedTokensAreNullNotExceptions()
	{
		assertNull(Jwt.subject(null));
		assertNull(Jwt.subject(""));
		assertNull(Jwt.subject("not-a-jwt"));
		assertNull(Jwt.subject("only.two"));
		assertNull(Jwt.subject("a.!!!not-base64!!!.c"));
		assertNull(Jwt.subject("a." + base64Url("not json at all") + ".c"));
	}
}
