package rift.launcher.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * The launcher's client for the Rift website API. The launcher only needs the license gate; plugin
 * resolve + artifact fetch happen on the game client side. HTTP goes through the injected {@link Http}
 * seam so this is unit-testable without a network.
 */
public final class RiftApiClient
{
	private static final Gson GSON = new Gson();

	private final String baseUrl;
	private final Http http;

	public RiftApiClient(String baseUrl, Http http)
	{
		this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
		this.http = http;
	}

	/** {@code POST /api/v1/license/check} (Bearer, empty body) → the account's gate. */
	public License licenseCheck(String accessToken) throws IOException, ApiException
	{
		Map<String, String> headers = new HashMap<>();
		headers.put("Authorization", "Bearer " + accessToken);
		headers.put("Content-Type", "application/json");

		Http.Reply reply = http.send("POST", baseUrl + "/api/v1/license/check", headers, new byte[0]);
		if (reply.status() / 100 != 2)
		{
			throw toApiException(reply);
		}
		return GSON.fromJson(reply.text(), License.class);
	}

	/**
	 * {@code POST /api/v1/dev/verify} — checks a developer license key. The key <b>is</b> the auth here
	 * (no Bearer token), so it goes in the body and nothing about it is ever logged.
	 *
	 * <p>A rejected key is a normal answer, not a failure: the website returns {@code 401 {valid:false}}
	 * for an unknown/revoked key or a suspended developer, and this maps that to an invalid result. Only
	 * a transport failure throws {@link IOException} — callers must treat that as "not a developer"
	 * (fail closed) rather than granting developer mode on a network error.
	 */
	public DevLicense verifyDevLicense(String key) throws IOException
	{
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json");

		JsonObject body = new JsonObject();
		body.addProperty("key", key);

		Http.Reply reply = http.send("POST", baseUrl + "/api/v1/dev/verify", headers,
			GSON.toJson(body).getBytes(java.nio.charset.StandardCharsets.UTF_8));
		if (reply.status() / 100 != 2)
		{
			// 401 = not a valid developer key; 400 = malformed. Neither leaks detail, and neither is
			// an error we surface differently -- both mean "no developer mode".
			return DevLicense.invalid();
		}
		try
		{
			DevLicense license = GSON.fromJson(reply.text(), DevLicense.class);
			return license == null ? DevLicense.invalid() : license;
		}
		catch (RuntimeException ex)
		{
			return DevLicense.invalid();
		}
	}

	static ApiException toApiException(Http.Reply reply)
	{
		String code = null;
		String message = null;
		try
		{
			JsonObject json = GSON.fromJson(reply.text(), JsonObject.class);
			if (json != null && json.has("error") && json.get("error").isJsonObject())
			{
				JsonObject error = json.getAsJsonObject("error");
				code = error.has("code") ? error.get("code").getAsString() : null;
				message = error.has("message") ? error.get("message").getAsString() : null;
			}
		}
		catch (RuntimeException ignored)
		{
			// Body wasn't the expected error envelope; status alone is enough.
		}
		return new ApiException(reply.status(), code, message);
	}
}
