package rift.launcher.web;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Minimal reader for the {@code sub} (subject) claim of a Supabase access token — the signed-in user's
 * id. Used to check that a developer license key actually belongs to the account signed in to the
 * launcher, rather than to some other developer.
 *
 * <p><b>The signature is deliberately not verified.</b> This is not an authorisation decision: the token
 * came from our own OAuth exchange, and the authority on a key is the server, which already verified it.
 * This only answers "who does the launcher think it is signed in as", so the launcher can refuse a key
 * belonging to a different account. Never treat a value from here as proof of identity.
 */
public final class Jwt
{
	private static final Gson GSON = new Gson();

	private Jwt()
	{
	}

	/** The {@code sub} claim, or {@code null} if the token is absent, malformed, or has no subject. */
	public static String subject(String jwt)
	{
		if (jwt == null || jwt.isEmpty())
		{
			return null;
		}
		try
		{
			String[] parts = jwt.split("\\.");
			if (parts.length < 2)
			{
				return null;
			}
			byte[] payload = Base64.getUrlDecoder().decode(parts[1]);
			JsonObject claims = GSON.fromJson(new String(payload, StandardCharsets.UTF_8), JsonObject.class);
			if (claims == null || !claims.has("sub") || claims.get("sub").isJsonNull())
			{
				return null;
			}
			String sub = claims.get("sub").getAsString();
			return sub.isEmpty() ? null : sub;
		}
		catch (RuntimeException ex)
		{
			// Malformed token — callers treat null as "unknown", which fails the ownership check closed.
			return null;
		}
	}
}
