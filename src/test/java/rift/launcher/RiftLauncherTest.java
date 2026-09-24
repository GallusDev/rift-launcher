package rift.launcher;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import rift.launcher.account.Account;
import rift.launcher.launch.JxCredentials;
import rift.launcher.web.LaunchHandoff;
import rift.launcher.web.Session;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class RiftLauncherTest
{
	@Test
	public void credentialsForMapsStoredAccountToJxVars()
	{
		Account account = new Account("char-1", "Zezima", "sess-9", 1L, null);
		JxCredentials creds = RiftLauncher.credentialsFor(account);
		assertEquals("sess-9", creds.getSessionId());
		assertEquals("char-1", creds.getCharacterId());
		assertEquals("Zezima", creds.getDisplayName());
	}

	@Test
	public void shortenLeavesTextUnderTheCapAlone()
	{
		assertEquals("Fixed a crash.", RiftLauncher.shorten("Fixed a crash.", 300));
		assertEquals(null, RiftLauncher.shorten(null, 300));
	}

	@Test
	public void shortenCutsOnAWordBoundary()
	{
		// The cap falls inside "installer"; the cut backs up to the space before it rather than
		// leaving a half word.
		assertEquals("Rift will close while the...",
			RiftLauncher.shorten("Rift will close while the installer runs.", 27));
	}

	@Test
	public void shortenFallsBackToAHardCutWhenThereIsNoSpace()
	{
		assertEquals("https://example.com/a...",
			RiftLauncher.shorten("https://example.com/aaaaaaaaaaaaaaaaaaaa", 21));
	}

	private static final LaunchHandoff.ProxyConfig PROXY = new LaunchHandoff.ProxyConfig("203.0.113.7", 1080, "user", "pass");

	private static JsonObject json(LaunchHandoff handoff)
	{
		return new Gson().fromJson(handoff.toJson(), JsonObject.class);
	}

	@Test
	public void aSignedInLaunchCarriesTheProxy()
	{
		// The original bug: the proxy was passed in and never put in the handoff, so a signed-in
		// launch connected directly from the real IP.
		JsonObject sent = json(RiftLauncher.handoffFor(new Session("AT", "RT", 1999999999L, "me"), false, PROXY));

		assertEquals("AT", sent.get("access_token").getAsString());
		JsonObject proxy = sent.getAsJsonObject("proxy");
		assertEquals("203.0.113.7", proxy.get("host").getAsString());
		assertEquals(1080, proxy.get("port").getAsInt());
		assertEquals("user", proxy.get("username").getAsString());
		assertEquals("pass", proxy.get("password").getAsString());
	}

	@Test
	public void aSignedOutLaunchWithAProxyStillSendsAHandoff()
	{
		// Signed out there used to be no handoff at all, so there was nowhere for a proxy to go.
		LaunchHandoff handoff = RiftLauncher.handoffFor(null, false, PROXY);

		assertTrue("a proxy alone must still produce a handoff", handoff != null);
		JsonObject sent = json(handoff);
		assertFalse("and it must not pose as a session", sent.has("access_token"));
		assertFalse(sent.get("developer_mode").getAsBoolean());
		assertEquals("203.0.113.7", sent.getAsJsonObject("proxy").get("host").getAsString());
	}

	@Test
	public void aDeveloperLaunchWithoutASessionCarriesTheProxyToo()
	{
		JsonObject sent = json(RiftLauncher.handoffFor(null, true, PROXY));

		assertTrue(sent.get("developer_mode").getAsBoolean());
		assertEquals("203.0.113.7", sent.getAsJsonObject("proxy").get("host").getAsString());
	}

	@Test
	public void noSessionNoDeveloperModeAndNoProxyIsAPlainLaunch()
	{
		assertNull(RiftLauncher.handoffFor(null, false, null));
	}

	@Test
	public void aDirectLaunchSendsNoProxy()
	{
		assertFalse(json(RiftLauncher.handoffFor(new Session("AT", "RT", 1L, "me"), true, null)).has("proxy"));
	}
}
