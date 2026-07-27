package rift.launcher.web;

import java.io.File;
import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import rift.launcher.crypto.Crypto;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class AuthFlowTest
{
	@Rule
	public TemporaryFolder tmp = new TemporaryFolder();

	private static final class IdentityCrypto implements Crypto
	{
		public byte[] protect(byte[] p)
		{
			return p;
		}

		public byte[] unprotect(byte[] c)
		{
			return c;
		}
	}

	/** SupabaseAuth backed by a stub that returns a fixed session on exchange/refresh. */
	private static SupabaseAuth stubAuth(String accessToken, String refreshToken)
	{
		Http http = (method, url, headers, body) -> new Http.Reply(200,
			("{\"access_token\":\"" + accessToken + "\",\"refresh_token\":\"" + refreshToken
				+ "\",\"expires_at\":123}").getBytes(StandardCharsets.UTF_8));
		return new SupabaseAuth("https://proj.supabase.co", "anon", http);
	}

	@Test
	public void signInCapturesCodeExchangesAndPersistsRefresh() throws Exception
	{
		File authFile = new File(tmp.getRoot(), "auth.dat");
		AuthStore store = new AuthStore(authFile, new IdentityCrypto());

		// The "browser" opener extracts redirect_to and hits it with a code, like a real redirect would.
		AuthFlow flow = new AuthFlow(stubAuth("ACCESS", "REFRESH"), store, 0, authorizeUrl ->
		{
			try
			{
				String redirect = URLDecoder.decode(
					param(authorizeUrl, "redirect_to"), StandardCharsets.UTF_8.name());
				HttpClient.newHttpClient().send(
					HttpRequest.newBuilder(URI.create(redirect + "?code=THE-CODE")).GET().build(),
					HttpResponse.BodyHandlers.discarding());
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});

		Session session = flow.signIn();

		assertEquals("ACCESS", session.getAccessToken());
		assertEquals("REFRESH", session.getRefreshToken());
		assertEquals("refresh token persisted for next launch", "REFRESH", store.load());
	}

	@Test
	public void resumeRefreshesWhenTokenStored() throws Exception
	{
		File authFile = new File(tmp.getRoot(), "auth.dat");
		AuthStore store = new AuthStore(authFile, new IdentityCrypto());
		store.save("OLD-REFRESH");

		AuthFlow flow = new AuthFlow(stubAuth("NEW-ACCESS", "NEW-REFRESH"), store, 0, url ->
		{
			throw new IllegalStateException("resume must not open a browser");
		});

		Session session = flow.resume();

		assertNotNull(session);
		assertEquals("NEW-ACCESS", session.getAccessToken());
		assertEquals("rotated refresh token re-persisted", "NEW-REFRESH", store.load());
	}

	@Test
	public void resumeReturnsNullWhenNoStoredToken() throws Exception
	{
		AuthStore store = new AuthStore(new File(tmp.getRoot(), "none.dat"), new IdentityCrypto());
		AuthFlow flow = new AuthFlow(stubAuth("A", "R"), store, 0, url ->
		{
		});
		assertNull(flow.resume());
	}

	private static String param(String url, String key)
	{
		for (String pair : URI.create(url).getRawQuery().split("&"))
		{
			int eq = pair.indexOf('=');
			if (eq > 0 && pair.substring(0, eq).equals(key))
			{
				return pair.substring(eq + 1);
			}
		}
		throw new IllegalArgumentException("no " + key + " in " + url);
	}
}
