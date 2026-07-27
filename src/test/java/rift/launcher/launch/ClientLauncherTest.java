package rift.launcher.launch;

import java.io.File;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ClientLauncherTest
{
	private final JxCredentials creds = new JxCredentials("sess", "char", "Zezima");

	@Test
	public void envMapContainsJxVars()
	{
		Map<String, String> env = creds.asEnvMap();
		assertEquals("sess", env.get("JX_SESSION_ID"));
		assertEquals("char", env.get("JX_CHARACTER_ID"));
		assertEquals("Zezima", env.get("JX_DISPLAY_NAME"));
		assertEquals("", env.get("JX_ACCESS_TOKEN"));
		assertEquals("", env.get("JX_REFRESH_TOKEN"));
	}

	@Test
	public void managedEnvAddsHandoffSignal()
	{
		Map<String, String> plain = ClientLauncher.buildEnv(creds, false);
		assertEquals("sess", plain.get("JX_SESSION_ID"));
		assertEquals(null, plain.get(ClientLauncher.HANDOFF_SIGNAL));

		Map<String, String> managed = ClientLauncher.buildEnv(creds, true);
		assertEquals("1", managed.get(ClientLauncher.HANDOFF_SIGNAL));
		assertEquals("sess", managed.get("JX_SESSION_ID"));
	}

	@Test
	public void buildsJavawJarCommand()
	{
		File javaw = new File("C:/jre/bin/javaw.exe");
		File clientJar = new File("C:/Users/x/.rift/rift-client.jar");
		List<String> cmd = ClientLauncher.buildCommand(javaw, clientJar);

		assertEquals(3, cmd.size());
		assertTrue(cmd.get(0).endsWith("javaw.exe"));
		assertEquals("-jar", cmd.get(1));
		assertTrue(cmd.get(2).endsWith("rift-client.jar"));
	}
}
