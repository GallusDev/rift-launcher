package rift.launcher.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * PKCE (RFC 7636) verifier/challenge pair for the Supabase Discord OAuth flow.
 * <p>
 * The verifier is a high-entropy random string kept in memory; the challenge is
 * {@code BASE64URL(SHA-256(verifier))} and is what goes in the authorize URL. Only the S256 method is
 * supported. Verified against the RFC 7636 Appendix B worked example.
 */
public final class Pkce
{
	private static final SecureRandom RANDOM = new SecureRandom();
	private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();

	private final String verifier;
	private final String challenge;

	private Pkce(String verifier, String challenge)
	{
		this.verifier = verifier;
		this.challenge = challenge;
	}

	/** A fresh verifier (32 random bytes → 43-char base64url) and its S256 challenge. */
	public static Pkce generate()
	{
		byte[] raw = new byte[32];
		RANDOM.nextBytes(raw);
		String verifier = URL_ENCODER.encodeToString(raw);
		return new Pkce(verifier, challengeFor(verifier));
	}

	/** {@code BASE64URL(SHA-256(verifier))} — the S256 code challenge for a given verifier. */
	public static String challengeFor(String verifier)
	{
		try
		{
			MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
			byte[] digest = sha256.digest(verifier.getBytes(StandardCharsets.US_ASCII));
			return URL_ENCODER.encodeToString(digest);
		}
		catch (NoSuchAlgorithmException e)
		{
			// SHA-256 is a required algorithm on every JVM; its absence is unrecoverable.
			throw new IllegalStateException("SHA-256 not available", e);
		}
	}

	public String getVerifier()
	{
		return verifier;
	}

	public String getChallenge()
	{
		return challenge;
	}
}
