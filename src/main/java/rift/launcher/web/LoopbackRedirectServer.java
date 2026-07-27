package rift.launcher.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A one-shot loopback HTTP listener for the OAuth redirect. Binds an ephemeral port on 127.0.0.1 and
 * captures the {@code ?code=} (or {@code ?error=}) the browser is redirected to after Discord consent.
 * <p>
 * Loopback capture is the standard desktop PKCE pattern (RFC 8252) and matches how the launcher already
 * handles Jagex login — no custom URI scheme to register, and the code never leaves the machine.
 */
public final class LoopbackRedirectServer implements AutoCloseable
{
	private static final String PATH = "/callback";

	/**
	 * The fixed loopback port the launcher uses in production. Pinned (not ephemeral) because Supabase's
	 * redirect-URL allow-list must contain the exact port; the operator allow-lists this one.
	 */
	public static final int DEFAULT_PORT = 53682;

	private final HttpServer server;
	private final CompletableFuture<String> code = new CompletableFuture<>();

	private LoopbackRedirectServer(HttpServer server)
	{
		this.server = server;
	}

	/** Binds an ephemeral loopback port (port 0) — convenient for tests. */
	public static LoopbackRedirectServer start() throws IOException
	{
		return start(0);
	}

	/** Binds the given loopback port (0 = ephemeral) and begins listening for the redirect. */
	public static LoopbackRedirectServer start(int port) throws IOException
	{
		HttpServer server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port), 0);
		LoopbackRedirectServer self = new LoopbackRedirectServer(server);
		server.createContext(PATH, self::handle);
		server.start();
		return self;
	}

	public String getRedirectUri()
	{
		return "http://127.0.0.1:" + server.getAddress().getPort() + PATH;
	}

	/**
	 * Blocks until the browser hits the callback, then returns the authorization code.
	 *
	 * @throws OAuthException the provider redirected with an error, or the wait timed out.
	 */
	public String awaitCode(Duration timeout) throws OAuthException
	{
		try
		{
			return code.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException e)
		{
			throw new OAuthException("Timed out waiting for the OAuth redirect", e);
		}
		catch (ExecutionException e)
		{
			Throwable cause = e.getCause();
			if (cause instanceof OAuthException)
			{
				throw (OAuthException) cause;
			}
			throw new OAuthException("OAuth redirect failed", cause);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new OAuthException("Interrupted waiting for the OAuth redirect", e);
		}
	}

	private void handle(HttpExchange exchange) throws IOException
	{
		Map<String, String> params = parseQuery(exchange.getRequestURI().getRawQuery());
		String error = params.get("error");
		String authCode = params.get("code");

		String body;
		if (error != null)
		{
			String description = params.getOrDefault("error_description", "");
			code.completeExceptionally(new OAuthException("OAuth error: " + error
				+ (description.isEmpty() ? "" : " (" + description + ")")));
			body = "Rift sign-in failed. You can close this window and return to the launcher.";
		}
		else if (authCode != null && !authCode.isEmpty())
		{
			code.complete(authCode);
			body = "Rift sign-in complete. You can close this window and return to the launcher.";
		}
		else
		{
			body = "Waiting for Rift sign-in...";
		}

		byte[] bytes = ("<!doctype html><meta charset=utf-8><title>Rift</title>"
			+ "<body style='font-family:sans-serif;background:#1e1e1e;color:#c6c6c6;text-align:center;padding-top:4rem'>"
			+ "<h2>" + body + "</h2></body>").getBytes(StandardCharsets.UTF_8);
		exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
		exchange.sendResponseHeaders(200, bytes.length);
		try (OutputStream os = exchange.getResponseBody())
		{
			os.write(bytes);
		}
	}

	private static Map<String, String> parseQuery(String rawQuery)
	{
		Map<String, String> params = new HashMap<>();
		if (rawQuery == null || rawQuery.isEmpty())
		{
			return params;
		}
		for (String pair : rawQuery.split("&"))
		{
			int eq = pair.indexOf('=');
			if (eq < 0)
			{
				continue;
			}
			String key = urlDecode(pair.substring(0, eq));
			String value = urlDecode(pair.substring(eq + 1));
			params.put(key, value);
		}
		return params;
	}

	private static String urlDecode(String s)
	{
		try
		{
			return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8.name());
		}
		catch (Exception e)
		{
			return s;
		}
	}

	@Override
	public void close()
	{
		server.stop(0);
	}
}
