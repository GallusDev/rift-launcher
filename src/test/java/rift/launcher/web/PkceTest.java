package rift.launcher.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class PkceTest
{
	// RFC 7636 Appendix B worked example.
	private static final String RFC_VERIFIER = "dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk";
	private static final String RFC_CHALLENGE = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";

	@Test
	public void challengeMatchesRfc7636Vector()
	{
		assertEquals(RFC_CHALLENGE, Pkce.challengeFor(RFC_VERIFIER));
	}

	@Test
	public void generatedVerifierIsUnreservedAndCorrectLength()
	{
		Pkce pkce = Pkce.generate();
		String verifier = pkce.getVerifier();

		assertTrue("verifier length must be 43..128, was " + verifier.length(),
			verifier.length() >= 43 && verifier.length() <= 128);
		assertTrue("verifier must be RFC 7636 unreserved chars only: " + verifier,
			verifier.matches("[A-Za-z0-9\\-._~]+"));
	}

	@Test
	public void generatedChallengeIsDerivedFromItsVerifier()
	{
		Pkce pkce = Pkce.generate();
		assertEquals(Pkce.challengeFor(pkce.getVerifier()), pkce.getChallenge());
	}
}
