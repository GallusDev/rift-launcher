package rift.launcher.proxy;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;

/**
 * Tests a proxy by doing what the game will do, and reports what the user actually needs to know.
 *
 * <p>A pass/fail button is close to useless here. Two failure modes look identical from the outside
 * and have completely different fixes — bad credentials versus an unreachable host — and a proxy can
 * pass a naive check and still break every launch:
 *
 * <ul>
 *   <li><b>The game port.</b> We connect through the proxy to a real world on <b>43594</b>, not to a
 *       web endpoint. Plenty of proxies allow 443 and quietly refuse everything else.</li>
 *   <li><b>The exit IP.</b> The address Jagex will see. This is the field that catches a proxy which
 *       connects perfectly but exits somewhere unexpected — invisible in every other launcher.</li>
 * </ul>
 */
@Slf4j
public final class ProxyTester
{
	/** A live world. Only used to prove the proxy will carry a game connection. */
	private static final String GAME_HOST = "oldschool1.runescape.com";
	private static final int GAME_PORT = 43594;

	/** Echoes the caller's address, so we can report the IP Jagex would see. */
	private static final String EXIT_IP_URL = "https://api.ipify.org";

	private static final int CONNECT_TIMEOUT_MS = 10_000;
	private static final int READ_TIMEOUT_MS = 10_000;

	/** How many proxies to test at once. Providers hand out dozens; serial testing would crawl. */
	private static final int PARALLELISM = 8;

	private ProxyTester()
	{
	}

	/** The outcome of one test. Written back onto the entry by the caller so the table can show it. */
	public static final class Result
	{
		private final ProxyEntry.Status status;
		private final Integer latencyMs;
		private final String exitIp;
		private final String detail;

		Result(ProxyEntry.Status status, Integer latencyMs, String exitIp, String detail)
		{
			this.status = status;
			this.latencyMs = latencyMs;
			this.exitIp = exitIp;
			this.detail = detail;
		}

		public ProxyEntry.Status getStatus()
		{
			return status;
		}

		public Integer getLatencyMs()
		{
			return latencyMs;
		}

		public String getExitIp()
		{
			return exitIp;
		}

		/** Short, user-facing explanation. Never contains the password. */
		public String getDetail()
		{
			return detail;
		}
	}

	/**
	 * Tests one proxy: connect to a live game port through it, time that, then look up the exit IP.
	 *
	 * <p>The exit-IP lookup is best-effort — it needs an outside service, and failing to reach that
	 * does not mean the proxy is broken, so a working proxy is still reported OK with no exit IP.
	 */
	public static Result test(ProxyEntry entry)
	{
		Proxy proxy = new Proxy(Proxy.Type.SOCKS,
			new InetSocketAddress(entry.getHost(), entry.getPort()));

		long start = System.currentTimeMillis();
		try (Socket socket = new Socket(proxy))
		{
			withAuth(entry, () ->
			{
				try
				{
					socket.connect(new InetSocketAddress(GAME_HOST, GAME_PORT), CONNECT_TIMEOUT_MS);
				}
				catch (Exception ex)
				{
					throw new RuntimeException(ex);
				}
				return null;
			});
			int latency = (int) (System.currentTimeMillis() - start);
			return new Result(ProxyEntry.Status.OK, latency, exitIp(entry), "Reached the game port");
		}
		catch (Exception ex)
		{
			String message = String.valueOf(rootMessage(ex)).toLowerCase();
			// SOCKS auth failures surface as a generic SocketException, so the message is the only
			// signal available. Worth separating: "wrong password" and "host is down" need different
			// fixes, and telling a user to check their network when the password is wrong wastes time.
			boolean auth = message.contains("authentication")
				|| message.contains("username")
				|| message.contains("password");
			return new Result(
				auth ? ProxyEntry.Status.AUTH_FAILED : ProxyEntry.Status.UNREACHABLE,
				null, null,
				auth ? "Proxy rejected the credentials" : shortReason(ex));
		}
	}

	/** Tests many proxies concurrently, applying each result to its entry. */
	public static void testAll(List<ProxyEntry> proxies)
	{
		ExecutorService pool = Executors.newFixedThreadPool(
			Math.min(PARALLELISM, Math.max(1, proxies.size())),
			r -> new Thread(r, "rift-proxy-test"));
		try
		{
			for (ProxyEntry entry : proxies)
			{
				pool.submit(() -> apply(entry, test(entry)));
			}
			pool.shutdown();
			//noinspection ResultOfMethodCallIgnored
			pool.awaitTermination(2, TimeUnit.MINUTES);
		}
		catch (InterruptedException ex)
		{
			Thread.currentThread().interrupt();
		}
		finally
		{
			pool.shutdownNow();
		}
	}

	/** Copies a result onto the entry, stamping the time so the UI can show how stale it is. */
	public static void apply(ProxyEntry entry, Result result)
	{
		entry.setLastStatus(result.getStatus());
		entry.setLastLatencyMs(result.getLatencyMs());
		entry.setLastExitIp(result.getExitIp());
		entry.setLastTestedAt(System.currentTimeMillis());
	}

	/** The address the outside world sees for this proxy, or {@code null} if it can't be determined. */
	private static String exitIp(ProxyEntry entry)
	{
		Proxy proxy = new Proxy(Proxy.Type.SOCKS,
			new InetSocketAddress(entry.getHost(), entry.getPort()));
		try
		{
			return withAuth(entry, () ->
			{
				try
				{
					HttpURLConnection conn = (HttpURLConnection) new URL(EXIT_IP_URL).openConnection(proxy);
					conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
					conn.setReadTimeout(READ_TIMEOUT_MS);
					try (BufferedReader r = new BufferedReader(
						new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)))
					{
						String ip = r.readLine();
						return ip == null || ip.trim().isEmpty() ? null : ip.trim();
					}
				}
				catch (Exception ex)
				{
					// Best effort: the proxy already proved it works, and the echo service being
					// unreachable says nothing about the proxy.
					return null;
				}
			});
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	/**
	 * Runs an action with SOCKS credentials installed.
	 *
	 * <p>Java asks a global {@link Authenticator} for proxy credentials, which is process-wide state,
	 * so this is synchronized and always restores the previous authenticator — otherwise testing one
	 * proxy would leave its credentials in place for the next.
	 */
	private static synchronized <T> T withAuth(ProxyEntry entry, java.util.function.Supplier<T> action)
	{
		if (!entry.hasAuth())
		{
			return action.get();
		}
		Authenticator previous = currentAuthenticator();
		try
		{
			Authenticator.setDefault(new Authenticator()
			{
				@Override
				protected PasswordAuthentication getPasswordAuthentication()
				{
					String password = entry.getPassword() == null ? "" : entry.getPassword();
					return new PasswordAuthentication(entry.getUsername(), password.toCharArray());
				}
			});
			return action.get();
		}
		finally
		{
			Authenticator.setDefault(previous);
		}
	}

	private static Authenticator currentAuthenticator()
	{
		try
		{
			// Java 9+ exposes the current default; on older runtimes there is no getter and clearing
			// it afterwards is the closest we can get.
			return (Authenticator) Authenticator.class.getMethod("getDefault").invoke(null);
		}
		catch (Exception ex)
		{
			return null;
		}
	}

	private static String rootMessage(Throwable ex)
	{
		Throwable t = ex;
		while (t.getCause() != null && t.getCause() != t)
		{
			t = t.getCause();
		}
		return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
	}

	private static String shortReason(Throwable ex)
	{
		String message = rootMessage(ex);
		return message.length() > 80 ? message.substring(0, 77) + "..." : message;
	}
}
