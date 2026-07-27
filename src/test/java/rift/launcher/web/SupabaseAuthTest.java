package rift.launcher.web;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class SupabaseAuthTest
{
	private static final String URL = "https://proj.supabase.co";
	private static final String ANON = "anon-key-123";

	/** Captures the outgoing request and returns a canned reply. */
	private static final class StubHttp implements Http
	{
		String method;
		String url;
		Map<String, String> headers;
		String body;
		int status = 200;
		String reply = "{}";

		@Override
		public Http.Reply send(String method, String url, Map<String, String> headers, byte[] body)
		{
			this.method = method;
			this.url = url;
			this.headers = headers;
			this.body = body == null ? null : new String(body, StandardCharsets.UTF_8);
			return new Http.Reply(status, reply.getBytes(StandardCharsets.UTF_8));
		}
	}

	@Test
	public void authorizeUrlHasDiscordAndPkce()
	{
		SupabaseAuth auth = new SupabaseAuth(URL, ANON, new StubHttp());
		String u = auth.authorizeUrl("http://127.0.0.1:53682/callback", "CHALLENGE123");

		assertTrue(u, u.startsWith(URL + "/auth/v1/authorize?"));
		assertTrue(u, u.contains("provider=discord"));
		assertTrue(u, u.contains("code_challenge=CHALLENGE123"));
		assertTrue(u, u.contains("code_challenge_method=s256"));
		assertTrue(u, u.contains("redirect_to=http%3A%2F%2F127.0.0.1%3A53682%2Fcallback"));
	}

	@Test
	public void exchangeCodePostsPkceGrantAndParsesSession() throws Exception
	{
		StubHttp http = new StubHttp();
		http.reply = "{\"access_token\":\"AT\",\"refresh_token\":\"RT\",\"expires_at\":1999999999,\"expires_in\":3600}";
		SupabaseAuth auth = new SupabaseAuth(URL, ANON, http);

		Session s = auth.exchangeCode("CODE", "VERIFIER");

		assertEquals("POST", http.method);
		assertEquals(URL + "/auth/v1/token?grant_type=pkce", http.url);
		assertEquals(ANON, http.headers.get("apikey"));
		assertTrue(http.body, http.body.contains("\"auth_code\":\"CODE\""));
		assertTrue(http.body, http.body.contains("\"code_verifier\":\"VERIFIER\""));
		assertEquals("AT", s.getAccessToken());
		assertEquals("RT", s.getRefreshToken());
		assertEquals(1999999999L, s.getExpiresAt());
	}

	@Test
	public void exchangeParsesDiscordUserName() throws Exception
	{
		StubHttp http = new StubHttp();
		http.reply = "{\"access_token\":\"AT\",\"refresh_token\":\"RT\",\"expires_at\":1,"
			+ "\"user\":{\"email\":\"x@y.z\",\"user_metadata\":{\"full_name\":\"ignored\",\"user_name\":\"Jello36\"}}}";
		SupabaseAuth auth = new SupabaseAuth(URL, ANON, http);

		Session s = auth.exchangeCode("CODE", "VERIFIER");

		assertEquals("Jello36", s.getUserName());
	}

	@Test
	public void refreshPostsRefreshGrantAndParsesSession() throws Exception
	{
		StubHttp http = new StubHttp();
		http.reply = "{\"access_token\":\"AT2\",\"refresh_token\":\"RT2\",\"expires_at\":123}";
		SupabaseAuth auth = new SupabaseAuth(URL, ANON, http);

		Session s = auth.refresh("OLD-RT");

		assertEquals(URL + "/auth/v1/token?grant_type=refresh_token", http.url);
		assertTrue(http.body, http.body.contains("\"refresh_token\":\"OLD-RT\""));
		assertEquals("AT2", s.getAccessToken());
		assertEquals("RT2", s.getRefreshToken());
	}

	@Test
	public void nonSuccessStatusThrows()
	{
		StubHttp http = new StubHttp();
		http.status = 400;
		http.reply = "{\"error\":\"invalid_grant\"}";
		SupabaseAuth auth = new SupabaseAuth(URL, ANON, http);

		try
		{
			auth.exchangeCode("x", "y");
			fail("expected OAuthException on 400");
		}
		catch (OAuthException e)
		{
			assertTrue(e.getMessage(), e.getMessage().contains("400"));
		}
	}

	@Test
	public void trailingSlashInBaseUrlIsNormalized()
	{
		SupabaseAuth auth = new SupabaseAuth(URL + "/", ANON, new StubHttp());
		assertTrue(auth.authorizeUrl("http://x/cb", "C").startsWith(URL + "/auth/v1/authorize?"));
	}
}
