package rift.launcher.web;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates Supabase Discord sign-in: generate PKCE, open the system browser to the authorize URL,
 * capture the redirect code on the loopback listener, exchange it for a {@link Session}, and persist
 * the refresh token. {@link #resume()} refreshes a stored session without any browser interaction.
 * <p>
 * The browser step is injected ({@code Consumer<String>}), so the whole flow is testable against the
 * real loopback server without launching a browser.
 */
@Slf4j
public final class AuthFlow
{
	private static final Duration SIGN_IN_TIMEOUT = Duration.ofMinutes(3);

	private final SupabaseAuth auth;
	private final AuthStore store;
	private final int port;
	private final Consumer<String> browserOpener;

	public AuthFlow(SupabaseAuth auth, AuthStore store, int port, Consumer<String> browserOpener)
	{
		this.auth = auth;
		this.store = store;
		this.port = port;
		this.browserOpener = browserOpener;
	}

	/** Production factory: pins the loopback port and opens the real system browser. */
	public static AuthFlow create(SupabaseAuth auth, AuthStore store)
	{
		return new AuthFlow(auth, store, LoopbackRedirectServer.DEFAULT_PORT, AuthFlow::openBrowser);
	}

	/** Full interactive sign-in. Returns the session and persists its refresh token. */
	public Session signIn() throws OAuthException, IOException
	{
		Pkce pkce = Pkce.generate();
		try (LoopbackRedirectServer server = LoopbackRedirectServer.start(port))
		{
			String authorizeUrl = auth.authorizeUrl(server.getRedirectUri(), pkce.getChallenge());
			browserOpener.accept(authorizeUrl);

			String code = server.awaitCode(SIGN_IN_TIMEOUT);
			Session session = auth.exchangeCode(code, pkce.getVerifier());
			persist(session);
			return session;
		}
	}

	/**
	 * Refreshes a previously stored session without a browser, or returns {@code null} if the user has
	 * never signed in. On success the rotated refresh token is re-persisted.
	 */
	public Session resume() throws OAuthException
	{
		String refreshToken = store.load();
		if (refreshToken == null)
		{
			return null;
		}
		Session session = auth.refresh(refreshToken);
		persist(session);
		return session;
	}

	/** Forgets the stored session so the next {@link #resume()} returns null and sign-in starts fresh. */
	public void signOut()
	{
		store.clear();
	}

	private void persist(Session session) throws OAuthException
	{
		if (session.getRefreshToken() == null)
		{
			return;
		}
		try
		{
			store.save(session.getRefreshToken());
		}
		catch (IOException e)
		{
			throw new OAuthException("Could not persist the refresh token", e);
		}
	}

	private static void openBrowser(String url)
	{
		try
		{
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE))
			{
				Desktop.getDesktop().browse(URI.create(url));
				return;
			}
		}
		catch (Exception e)
		{
			log.warn("Could not open the system browser automatically ({})", e.getClass().getSimpleName());
		}
		// Fallback: the caller/UI should surface the URL for manual opening.
		log.info("Open this URL to sign in to Rift: {}", url);
	}
}
