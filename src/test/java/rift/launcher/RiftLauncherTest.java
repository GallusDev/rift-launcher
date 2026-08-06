package rift.launcher;

import rift.launcher.account.Account;
import rift.launcher.launch.JxCredentials;
import static org.junit.Assert.assertEquals;
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
}
