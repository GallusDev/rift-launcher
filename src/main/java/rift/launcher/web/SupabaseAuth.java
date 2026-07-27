package rift.launcher.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Supabase Discord OAuth (PKCE) against the GoTrue auth endpoints — the interim path the website
 * exposes today (the dedicated {@code /launcher/auth} wrapper is not built). Builds the browser
 * authorize URL, exchanges the returned code for a {@link Session}, and refreshes it.
 * <p>
 * HTTP goes through the injected {@link Http} seam so this is unit-testable without a network. Never
 * logs tokens.
 */
public final class SupabaseAuth
{
	private static final Gson GSON = new Gson();

	private final String supabaseUrl;
	private final String anonKey;
	private final Http http;

	public SupabaseAuth(String supabaseUrl, String anonKey, Http http)
	{
		// Normalize so we can always append "/auth/v1/...".
		this.supabaseUrl = supabaseUrl.endsWith("/") ? supabaseUrl.substring(0, supabaseUrl.length() - 1) : supabaseUrl;
		this.anonKey = anonKey;
		this.http = http;
	}

	/** The system-browser URL that starts Discord OAuth with this PKCE challenge and loopback redirect. */
	public String authorizeUrl(String redirectUri, String codeChallenge)
	{
		return supabaseUrl + "/auth/v1/authorize?provider=discord"
			+ "&redirect_to=" + enc(redirectUri)
			+ "&code_challenge=" + enc(codeChallenge)
			+ "&code_challenge_method=s256";
	}

	/** Exchanges the authorization code (+ the PKCE verifier) for a session. */
	public Session exchangeCode(String authCode, String codeVerifier) throws OAuthException
	{
		JsonObject body = new JsonObject();
		body.addProperty("auth_code", authCode);
		body.addProperty("code_verifier", codeVerifier);
		return token("grant_type=pkce", body);
	}

	/** Trades a refresh token for a fresh session before the access token expires. */
	public Session refresh(String refreshToken) throws OAuthException
	{
		JsonObject body = new JsonObject();
		body.addProperty("refresh_token", refreshToken);
		return token("grant_type=refresh_token", body);
	}

	private Session token(String query, JsonObject body) throws OAuthException
	{
		Map<String, String> headers = new HashMap<>();
		headers.put("apikey", anonKey);
		headers.put("Content-Type", "application/json");

		try
		{
			Http.Reply reply = http.send("POST", supabaseUrl + "/auth/v1/token?" + query, headers,
				body.toString().getBytes(StandardCharsets.UTF_8));

			if (reply.status() / 100 != 2)
			{
				// Include the status; body may name the error code, but never contains our tokens.
				throw new OAuthException("Supabase token request failed (" + reply.status() + "): " + reply.text());
			}

			JsonObject json = GSON.fromJson(reply.text(), JsonObject.class);
			return new Session(
				optString(json, "access_token"),
				optString(json, "refresh_token"),
				json.has("expires_at") && !json.get("expires_at").isJsonNull() ? json.get("expires_at").getAsLong() : 0L,
				userName(json));
		}
		catch (IOException e)
		{
			throw new OAuthException("Supabase token request failed", e);
		}
	}

	private static String optString(JsonObject json, String key)
	{
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : null;
	}

	/** Best-effort Discord display name from the session's user object, or null. */
	private static String userName(JsonObject json)
	{
		if (json == null || !json.has("user") || !json.get("user").isJsonObject())
		{
			return null;
		}
		JsonObject user = json.getAsJsonObject("user");
		if (user.has("user_metadata") && user.get("user_metadata").isJsonObject())
		{
			JsonObject meta = user.getAsJsonObject("user_metadata");
			for (String key : new String[]{"user_name", "full_name", "name", "preferred_username", "global_name"})
			{
				String value = optString(meta, key);
				if (value != null && !value.isEmpty())
				{
					return value;
				}
			}
		}
		return optString(user, "email");
	}

	private static String enc(String s)
	{
		try
		{
			return URLEncoder.encode(s, StandardCharsets.UTF_8.name());
		}
		catch (UnsupportedEncodingException e)
		{
			// UTF-8 is always available.
			throw new IllegalStateException(e);
		}
	}
}
