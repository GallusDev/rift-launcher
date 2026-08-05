package rift.launcher.proxy;

import java.net.ServerSocket;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

/**
 * Exercised against a stub SOCKS5 server rather than a real proxy, so the distinction that matters --
 * bad credentials versus unreachable -- is actually verified instead of assumed.
 */
public class ProxyTesterTest
{
	private static ProxyEntry entry(int port, String user, String pass)
	{
		return new ProxyEntry("test", "127.0.0.1", port, user, pass);
	}

	/** A port with nothing listening, for the "host is down" case. */
	private static int closedPort() throws Exception
	{
		try (ServerSocket s = new ServerSocket(0))
		{
			return s.getLocalPort();
		}
	}

	@Test
	public void reachableProxyReportsOkWithLatency() throws Exception
	{
		try (StubSocks5Server server = StubSocks5Server.open())
		{
			ProxyTester.Result result = ProxyTester.test(entry(server.port(), null, null));

			assertEquals(ProxyEntry.Status.OK, result.getStatus());
			assertNotNull("a working proxy reports how slow it is", result.getLatencyMs());
			assertTrue(result.getLatencyMs() >= 0);
		}
	}

	@Test
	public void rejectedCredentialsAreReportedAsAuthFailureNotUnreachable() throws Exception
	{
		try (StubSocks5Server server = StubSocks5Server.rejectingAuth())
		{
			ProxyTester.Result result = ProxyTester.test(entry(server.port(), "alice", "wrong"));

			// The whole point: "check your password" and "check your network" are different advice.
			assertEquals(ProxyEntry.Status.AUTH_FAILED, result.getStatus());
			assertNull(result.getLatencyMs());
		}
	}

	@Test
	public void unreachableProxyIsReportedAsUnreachable() throws Exception
	{
		ProxyTester.Result result = ProxyTester.test(entry(closedPort(), null, null));

		assertEquals(ProxyEntry.Status.UNREACHABLE, result.getStatus());
		assertNull(result.getLatencyMs());
		assertNotNull("the user gets a reason, not just a red dot", result.getDetail());
	}

	@Test
	public void failureDetailNeverLeaksThePassword() throws Exception
	{
		try (StubSocks5Server server = StubSocks5Server.rejectingAuth())
		{
			ProxyTester.Result result = ProxyTester.test(entry(server.port(), "alice", "s3cret-password"));

			// Test output ends up in screenshots and support threads.
			assertFalse(result.getDetail().contains("s3cret-password"));
		}
	}

	@Test
	public void applyStampsTheEntrySoStalenessIsVisible() throws Exception
	{
		ProxyEntry entry = entry(1080, null, null);
		long before = System.currentTimeMillis();

		ProxyTester.apply(entry, new ProxyTester.Result(ProxyEntry.Status.OK, 42, "9.9.9.9", "ok"));

		assertEquals(ProxyEntry.Status.OK, entry.getLastStatus());
		assertEquals(Integer.valueOf(42), entry.getLastLatencyMs());
		assertEquals("9.9.9.9", entry.getLastExitIp());
		// Timestamped because a proxy that worked an hour ago may well be dead now.
		assertNotNull(entry.getLastTestedAt());
		assertTrue(entry.getLastTestedAt() >= before);
	}

	@Test
	public void testAllAppliesResultsToEveryEntry() throws Exception
	{
		try (StubSocks5Server server = StubSocks5Server.open())
		{
			ProxyEntry ok = entry(server.port(), null, null);
			ProxyEntry dead = entry(closedPort(), null, null);

			ProxyTester.testAll(java.util.Arrays.asList(ok, dead));

			assertEquals(ProxyEntry.Status.OK, ok.getLastStatus());
			assertEquals(ProxyEntry.Status.UNREACHABLE, dead.getLastStatus());
			assertNotNull(ok.getLastTestedAt());
			assertNotNull(dead.getLastTestedAt());
		}
	}
}
