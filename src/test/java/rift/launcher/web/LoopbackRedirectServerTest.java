package rift.launcher.web;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class LoopbackRedirectServerTest
{
	private static int hit(String url) throws Exception
	{
		HttpResponse<String> r = HttpClient.newHttpClient().send(
			HttpRequest.newBuilder(URI.create(url)).GET().build(),
			HttpResponse.BodyHandlers.ofString());
		return r.statusCode();
	}

	@Test
	public void redirectUriIsLoopbackCallback() throws Exception
	{
		try (LoopbackRedirectServer server = LoopbackRedirectServer.start())
		{
			String uri = server.getRedirectUri();
			assertTrue(uri, uri.startsWith("http://127.0.0.1:"));
			assertTrue(uri, uri.endsWith("/callback"));
		}
	}

	@Test
	public void capturesAuthorizationCode() throws Exception
	{
		try (LoopbackRedirectServer server = LoopbackRedirectServer.start())
		{
			int status = hit(server.getRedirectUri() + "?code=test-code-123&state=xyz");
			assertEquals(200, status); // browser gets a friendly page

			assertEquals("test-code-123", server.awaitCode(Duration.ofSeconds(5)));
		}
	}

	@Test
	public void reportsOAuthErrorRedirect() throws Exception
	{
		try (LoopbackRedirectServer server = LoopbackRedirectServer.start())
		{
			hit(server.getRedirectUri() + "?error=access_denied&error_description=nope");
			try
			{
				server.awaitCode(Duration.ofSeconds(5));
				fail("expected an OAuth error to be reported");
			}
			catch (OAuthException e)
			{
				assertTrue(e.getMessage(), e.getMessage().contains("access_denied"));
			}
		}
	}
}
