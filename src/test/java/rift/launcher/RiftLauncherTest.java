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
}
