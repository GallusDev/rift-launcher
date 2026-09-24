package rift.launcher.web;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Test;

public class RiftApiClientTest
{
	private static final String BASE = "http://localhost:3000";

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
	public void licenseCheckPostsBearerEmptyBodyAndParses() throws Exception
	{
		StubHttp http = new StubHttp();
		http.reply = "{\"vip\":true,\"max_sessions\":null,\"blocked\":false,\"tier\":\"vip\","
			+ "\"discount_bps\":1000,\"entitlements\":[{\"plugin_slug\":\"rift-combat\",\"expires_at\":null}]}";
		RiftApiClient api = new RiftApiClient(BASE, http);

		License license = api.licenseCheck("JWT-123");

		assertEquals("POST", http.method);
		assertEquals(BASE + "/api/v1/license/check", http.url);
		assertEquals("Bearer JWT-123", http.headers.get("Authorization"));
		assertEquals("", http.body); // empty body
		assertTrue(license.isVip());
		assertFalse(license.isBlocked());
		assertNull("null max_sessions means unlimited", license.getMaxSessions());
		assertTrue(license.isUnlimitedSessions());
		assertEquals("vip", license.getTier());
		assertEquals(1, license.getEntitlements().size());
		assertEquals("rift-combat", license.getEntitlements().get(0).getPluginSlug());
	}

	@Test
	public void licenseCheckParsesBlockedAndFreeLimit() throws Exception
	{
		StubHttp http = new StubHttp();
		http.reply = "{\"vip\":false,\"max_sessions\":1,\"blocked\":true,\"tier\":null,\"discount_bps\":0,\"entitlements\":[]}";
		RiftApiClient api = new RiftApiClient(BASE, http);

		License license = api.licenseCheck("JWT");

		assertTrue(license.isBlocked());
		assertEquals(Integer.valueOf(1), license.getMaxSessions());
		assertFalse(license.isUnlimitedSessions());
	}

	@Test
	public void nonSuccessThrowsApiExceptionWithStatus() throws Exception
	{
		StubHttp http = new StubHttp();
		http.status = 401;
		http.reply = "{\"error\":{\"code\":\"unauthorized\",\"message\":\"no token\"}}";
		RiftApiClient api = new RiftApiClient(BASE, http);

		try
		{
			api.licenseCheck("bad");
			fail("expected ApiException on 401");
		}
		catch (ApiException e)
		{
			assertEquals(401, e.getStatus());
		}
	}

	@Test
	public void licenseCheckParsesDeveloperFlag() throws Exception
	{
		StubHttp http = new StubHttp();
		http.reply = "{\"vip\":false,\"max_sessions\":1,\"blocked\":false,\"tier\":\"free\","
			+ "\"discount_bps\":0,\"developer\":true,\"entitlements\":[]}";

		assertTrue(new RiftApiClient(BASE, http).licenseCheck("JWT").isDeveloper());
	}

	@Test
	public void licenseCheckDeveloperDefaultsFalseWhenAbsent() throws Exception
	{
		StubHttp http = new StubHttp();
		// An older server omits the field; the Developer section must stay hidden.
		http.reply = "{\"vip\":false,\"max_sessions\":1,\"blocked\":false,\"tier\":\"free\","
			+ "\"discount_bps\":0,\"entitlements\":[]}";

		assertFalse(new RiftApiClient(BASE, http).licenseCheck("JWT").isDeveloper());
	}

	@Test
	public void myPluginsGetsWithBearerAndReadsTheEntitlementTerms() throws Exception
	{
		StubHttp http = new StubHttp();
		// The live route's shape: terms live under "entitlement", and trials always carry a null expiry.
		http.reply = "{\"plugins\":["
			+ "{\"slug\":\"rift-combat\",\"name\":\"Rift Combat\",\"enabled\":true,"
			+ "\"entitlement\":{\"expires_at\":\"2026-10-24T12:00:00+00:00\",\"source\":\"purchase\"},"
			+ "\"version\":\"1.4.0\",\"version_id\":\"v1\",\"protected\":true,"
			+ "\"artifact\":{\"key\":\"/api/v1/artifact?plugin_version=v1\"}},"
			+ "{\"slug\":\"rift-toa\",\"name\":\"Rift ToA\",\"enabled\":true,"
			+ "\"entitlement\":{\"expires_at\":null,\"source\":\"trial\"}}"
			+ "]}";
		RiftApiClient api = new RiftApiClient(BASE, http);

		java.util.List<OwnedPlugin> plugins = api.myPlugins("JWT-123");

		assertEquals("GET", http.method);
		assertEquals(BASE + "/api/v1/me/plugins", http.url);
		assertEquals("Bearer JWT-123", http.headers.get("Authorization"));
		assertEquals(2, plugins.size());
		assertEquals("Rift Combat", plugins.get(0).getName());
		assertEquals("2026-10-24T12:00:00+00:00", plugins.get(0).getExpiresAt());
		assertFalse(plugins.get(0).isTrial());
		assertTrue(plugins.get(1).isTrial());
		assertNull(plugins.get(1).getExpiresAt());
	}

	@Test
	public void myPluginsDropsMalformedEntriesRatherThanFailingTheList() throws Exception
	{
		StubHttp http = new StubHttp();
		http.reply = "{\"plugins\":[null,{\"name\":\"no slug\"},{\"slug\":\"ok\"}]}";

		java.util.List<OwnedPlugin> plugins = new RiftApiClient(BASE, http).myPlugins("JWT");

		assertEquals(1, plugins.size());
		assertEquals("a nameless entry falls back to its slug", "ok", plugins.get(0).getName());
		assertNull("no entitlement object is no terms, not a crash", plugins.get(0).getExpiresAt());
	}

	@Test
	public void myPluginsWithNoListIsEmpty() throws Exception
	{
		StubHttp http = new StubHttp();
		http.reply = "{}";
		assertTrue(new RiftApiClient(BASE, http).myPlugins("JWT").isEmpty());
	}

	@Test
	public void myPluginsSurfacesAnHttpErrorWithItsStatus() throws Exception
	{
		StubHttp http = new StubHttp();
		http.status = 401;
		http.reply = "{\"error\":{\"code\":\"unauthorized\",\"message\":\"Bearer token required\"}}";
		try
		{
			new RiftApiClient(BASE, http).myPlugins("stale");
			fail("expected ApiException");
		}
		catch (ApiException ex)
		{
			assertEquals(401, ex.getStatus());
			assertEquals("unauthorized", ex.getCode());
		}
	}
}
