package rift.launcher.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * The developer-license verify call. The security-relevant behaviour is that only an explicit
 * {@code 200 valid:true} unlocks developer mode — every other outcome (401, malformed body, transport
 * failure) must read as "not a developer".
 */
public class DevLicenseVerifyTest
{
	private static final String BASE = "http://localhost:3000";
	private static final String KEY = "rift_dev_0123456789abcdef0123456789abcdef01234567";

	private static final class StubHttp implements Http
	{
		String method;
		String url;
		Map<String, String> headers;
		String body;
		int status = 200;
		String reply = "{}";
		IOException fail;

		@Override
		public Http.Reply send(String method, String url, Map<String, String> headers, byte[] body)
			throws IOException
		{
			if (fail != null)
			{
				throw fail;
			}
			this.method = method;
			this.url = url;
			this.headers = headers;
			this.body = body == null ? null : new String(body, StandardCharsets.UTF_8);
			return new Http.Reply(status, reply.getBytes(StandardCharsets.UTF_8));
		}
	}

	@Test
	public void validKeyPostsKeyInBodyAndParsesDeveloper() throws Exception
	{
		StubHttp http = new StubHttp();
		http.reply = "{\"valid\":true,\"developer_id\":\"dev-123\",\"tier\":\"premium\"}";

		DevLicense license = new RiftApiClient(BASE, http).verifyDevLicense(KEY);

		assertEquals("POST", http.method);
		assertEquals(BASE + "/api/v1/dev/verify", http.url);
		assertEquals("application/json", http.headers.get("Content-Type"));
		// The key is the credential and travels in the body — never as a header or query param.
		assertEquals("{\"key\":\"" + KEY + "\"}", http.body);
		assertTrue(license.isValid());
		assertEquals("dev-123", license.getDeveloperId());
		assertEquals("premium", license.getTier());
	}

	@Test
	public void unauthorizedKeyIsInvalidNotAnException() throws Exception
	{
		StubHttp http = new StubHttp();
		http.status = 401;
		http.reply = "{\"valid\":false}";

		// A revoked/unknown key is an expected answer, so it must not throw — it just isn't valid.
		assertFalse(new RiftApiClient(BASE, http).verifyDevLicense(KEY).isValid());
	}

	@Test
	public void malformedRequestIsInvalid() throws Exception
	{
		StubHttp http = new StubHttp();
		http.status = 400;
		http.reply = "{\"error\":\"bad json\"}";

		assertFalse(new RiftApiClient(BASE, http).verifyDevLicense(KEY).isValid());
	}

	@Test
	public void unparseableSuccessBodyIsInvalid() throws Exception
	{
		StubHttp http = new StubHttp();
		http.status = 200;
		http.reply = "not json at all";

		assertFalse(new RiftApiClient(BASE, http).verifyDevLicense(KEY).isValid());
	}

	@Test
	public void okBodyWithoutValidFlagIsInvalid() throws Exception
	{
		StubHttp http = new StubHttp();
		http.status = 200;
		http.reply = "{\"developer_id\":\"dev-123\"}";

		// Absent "valid" must not be read as true.
		assertFalse(new RiftApiClient(BASE, http).verifyDevLicense(KEY).isValid());
	}

	@Test(expected = IOException.class)
	public void transportFailurePropagatesSoCallerCanFailClosed() throws Exception
	{
		StubHttp http = new StubHttp();
		http.fail = new IOException("connection refused");

		// Surfaced, not swallowed as "valid" — RiftLauncher turns this into standard mode.
		new RiftApiClient(BASE, http).verifyDevLicense(KEY);
	}
}
