package rift.launcher;

import com.formdev.flatlaf.FlatDarkLaf;
import java.io.File;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import rift.launcher.account.Account;
import rift.launcher.account.AccountImporter;
import rift.launcher.account.AccountStore;
import rift.launcher.crypto.DpapiCrypto;
import rift.launcher.launch.ClientLauncher;
import rift.launcher.launch.JxCredentials;
import rift.launcher.ui.LauncherFrame;
import rift.launcher.web.ApiException;
import rift.launcher.web.AuthFlow;
import rift.launcher.web.AuthStore;
import rift.launcher.web.JdkHttp;
import rift.launcher.web.LaunchHandoff;
import rift.launcher.web.License;
import rift.launcher.web.RiftApiClient;
import rift.launcher.web.RiftConfig;
import rift.launcher.web.Session;
import rift.launcher.web.SessionEnforcer;
import rift.launcher.web.SupabaseAuth;

@Slf4j
public class RiftLauncher
{
	private static final File RIFT_DIR = new File(System.getProperty("user.home"), ".rift");
	private static final File ACCOUNTS_FILE = new File(RIFT_DIR, "accounts.dat");
	private static final File AUTH_FILE = new File(RIFT_DIR, "auth.dat");
	private static final File CLIENT_JAR = new File(RIFT_DIR, "rift-client.jar");

	/** How often the signed-in launcher re-checks the license, so a mid-session ban is reflected. */
	private static final long LICENSE_POLL_SECONDS = 30;

	// Rift-account state, shared between the auth bootstrap and the launch path.
	private static final AtomicReference<Session> SESSION = new AtomicReference<>();
	private static final AtomicReference<License> LICENSE = new AtomicReference<>();
	private static final AtomicInteger RUNNING_CLIENTS = new AtomicInteger();
	private static final ScheduledExecutorService POLL =
		Executors.newSingleThreadScheduledExecutor(r -> new Thread(r, "rift-license-poll"));
	private static volatile boolean banned;

	private static final AuthFlow AUTH_FLOW = AuthFlow.create(
		new SupabaseAuth(RiftConfig.SUPABASE_URL, RiftConfig.SUPABASE_ANON_KEY, new JdkHttp()),
		new AuthStore(AUTH_FILE, new DpapiCrypto()));
	private static final RiftApiClient API = new RiftApiClient(RiftConfig.apiBaseUrl(), new JdkHttp());

	public static void main(String[] args)
	{
		AccountStore store = new AccountStore(ACCOUNTS_FILE, new DpapiCrypto());

		if (System.getenv("JX_SESSION_ID") != null && System.getenv("JX_CHARACTER_ID") != null)
		{
			importCurrentAccount(store);
		}

		ClientLauncher clientLauncher = new ClientLauncher();

		SwingUtilities.invokeLater(() ->
		{
			FlatDarkLaf.setup();
			LauncherFrame frame = new LauncherFrame();
			frame.setOnLaunch(account -> launchAccount(clientLauncher, frame, account));
			frame.setOnSignIn(() -> signIn(frame));
			frame.setOnSignOut(() -> signOut(frame));
			frame.setAccounts(store.load());
			frame.setVisible(true);

			// Silently resume a stored session so returning users are signed in without a browser.
			// First-time sign-in is driven by the button (signIn), not forced on startup.
			resumeRiftAccount(frame);

			// Re-check the license while signed in, so a ban that happens after sign-in is reflected
			// (the client reacts via its own poll; the launcher needs its own).
			POLL.scheduleWithFixedDelay(() -> pollLicense(frame),
				LICENSE_POLL_SECONDS, LICENSE_POLL_SECONDS, TimeUnit.SECONDS);
		});
	}

	/** Periodic re-check: ban → close; token rejected + un-refreshable → sign out (session revoked). */
	private static void pollLicense(LauncherFrame frame)
	{
		Session session = SESSION.get();
		if (session == null)
		{
			return; // not signed in — nothing to check
		}
		try
		{
			License license = API.licenseCheck(session.getAccessToken());
			LICENSE.set(license);
			if (license.isBlocked())
			{
				bannedAndExit();
			}
		}
		catch (ApiException e)
		{
			// Token rejected. A ban revokes the tokens, so refresh usually fails too — try once; if it
			// can't recover, the session is gone: sign out so the UI stops showing "signed in".
			if (e.getStatus() == 401 || e.getStatus() == 403)
			{
				recoverOrSignOut(frame);
			}
			// Other statuses are transient; leave the session as-is and try next tick.
		}
		catch (Exception e)
		{
			// Network/server down — transient; don't disturb the signed-in state.
			log.debug("Rift license poll failed ({})", e.getClass().getSimpleName());
		}
	}

	/** Try to refresh + re-check; if that fails, sign out (revoked session, possibly a ban). */
	private static void recoverOrSignOut(LauncherFrame frame)
	{
		try
		{
			Session fresh = AUTH_FLOW.resume();
			if (fresh != null)
			{
				SESSION.set(fresh);
				License license = API.licenseCheck(fresh.getAccessToken());
				LICENSE.set(license);
				if (license.isBlocked())
				{
					bannedAndExit();
				}
				return;
			}
		}
		catch (Exception ignored)
		{
			// fall through to sign-out
		}
		SESSION.set(null);
		LICENSE.set(null);
		frame.setRiftAccount(null);
		frame.setStatus("Session ended - sign in again");
	}

	/** Resumes a previously stored Rift session (no browser). Leaves the UI signed-out if there's none. */
	private static void resumeRiftAccount(LauncherFrame frame)
	{
		new Thread(() ->
		{
			try
			{
				Session session = AUTH_FLOW.resume();
				if (session == null)
				{
					frame.setRiftAccount(null);
					frame.setStatus("Not signed in - click Sign in to Rift to load managed plugins");
					return;
				}
				applySession(frame, session);
			}
			catch (Exception ex)
			{
				log.warn("Rift resume failed ({}); signed out", ex.getClass().getSimpleName());
				frame.setRiftAccount(null);
				frame.setStatus("Rift sign-in unavailable - running without managed plugins");
			}
		}, "rift-resume").start();
	}

	/** Interactive sign-in (opens the browser), triggered by the Sign-in button. */
	private static void signIn(LauncherFrame frame)
	{
		new Thread(() ->
		{
			frame.setRiftAuthEnabled(false);
			try
			{
				frame.setStatus("Sign in to Rift in your browser...");
				Session session = AUTH_FLOW.signIn();
				applySession(frame, session);
			}
			catch (Exception ex)
			{
				// Only reached if the OAuth exchange itself failed (browser cancelled, bad code, etc.);
				// a license-server outage is handled inside applySession and does NOT land here.
				log.warn("Rift sign-in failed", ex);
				frame.setRiftAccount(null);
				frame.setStatus("Sign-in failed: " + reason(ex));
			}
			finally
			{
				frame.setRiftAuthEnabled(true);
			}
		}, "rift-signin").start();
	}

	/** Signs out: forgets the stored session so a different Discord/Rift account can sign in next. */
	private static void signOut(LauncherFrame frame)
	{
		AUTH_FLOW.signOut();
		SESSION.set(null);
		LICENSE.set(null);
		frame.setRiftAccount(null);
		frame.setStatus("Signed out - click Sign in to Rift to switch accounts");
	}

	/**
	 * Records a live session and updates the account bar, then runs the license gate. Auth success and
	 * license reachability are decoupled: once the OAuth exchange has produced a session the user is
	 * signed in, so a license-check failure (e.g. the API being down) is surfaced as a non-fatal warning
	 * rather than discarding the session or reporting a sign-in failure.
	 */
	private static void applySession(LauncherFrame frame, Session session)
	{
		SESSION.set(session);
		String name = session.getUserName() == null ? "Rift account" : session.getUserName();
		frame.setRiftAccount(name);

		try
		{
			License license = API.licenseCheck(session.getAccessToken());
			LICENSE.set(license);
			if (license.isBlocked())
			{
				bannedAndExit();
				return;
			}
			frame.setStatus("Signed in to Rift as " + name);
		}
		catch (Exception ex)
		{
			// Signed in, but couldn't verify the license (server unreachable / error). Non-fatal:
			// keep the session; managed plugins simply won't resolve until the API is reachable.
			LICENSE.set(null);
			log.warn("License check failed after sign-in (signed in anyway)", ex);
			frame.setStatus("Signed in as " + name + " - Rift server unreachable, license unverified");
		}
	}

	/** Ban: show the account-banned notice and close the launcher entirely. */
	private static void bannedAndExit()
	{
		if (banned)
		{
			return;
		}
		banned = true;
		POLL.shutdownNow();
		SwingUtilities.invokeLater(() ->
		{
			javax.swing.JOptionPane.showMessageDialog(null,
				"Your Rift account has been banned.\nAll access revoked. Effective immediately.",
				"Rift", javax.swing.JOptionPane.ERROR_MESSAGE);
			System.exit(0);
		});
	}

	/** Short, token-free reason for a sign-in failure, for the status line. */
	private static String reason(Exception ex)
	{
		if (ex instanceof java.net.ConnectException)
		{
			return "could not reach the Rift server";
		}
		String message = ex.getMessage();
		return message == null ? ex.getClass().getSimpleName() : message;
	}

	private static void importCurrentAccount(AccountStore store)
	{
		try
		{
			Account imported = AccountImporter.fromEnvironment(System.getenv(), System.currentTimeMillis());
			List<Account> accounts = store.load();
			accounts.removeIf(a -> a.getCharacterId().equals(imported.getCharacterId()));
			accounts.add(imported);
			store.save(accounts);
			log.info("Imported Jagex session for character {}", imported.getCharacterId());
		}
		catch (Exception ex)
		{
			log.error("Failed to import Jagex session from environment", ex);
		}
	}

	/** Builds the JX_* credentials to replay for a stored account. Package-private for tests. */
	static JxCredentials credentialsFor(Account account)
	{
		return new JxCredentials(account.getSessionId(), account.getCharacterId(), account.getDisplayName());
	}

	private static void launchAccount(ClientLauncher clientLauncher, LauncherFrame frame, Account account)
	{
		String characterId = account.getCharacterId();

		License license = LICENSE.get();
		if (license != null && license.isBlocked())
		{
			frame.setAccountStatus(characterId, "Ready");
			frame.setStatus("Your Rift account is under review - the client is disabled");
			return;
		}
		Integer maxSessions = license == null ? null : license.getMaxSessions();
		if (!SessionEnforcer.canLaunch(maxSessions, RUNNING_CLIENTS.get()))
		{
			frame.setAccountStatus(characterId, "Ready");
			frame.setStatus("Session limit reached (" + maxSessions + ") - close a client or upgrade to VIP");
			return;
		}

		new Thread(() ->
		{
			RUNNING_CLIENTS.incrementAndGet();
			try
			{
				JxCredentials creds = credentialsFor(account);
				File javaw = new File(System.getProperty("java.home"), "bin/javaw.exe");
				Process process = launchWithSession(clientLauncher, creds, javaw);
				frame.setAccountStatus(characterId, "Playing");
				process.waitFor();
				frame.setAccountStatus(characterId, "Ready");
			}
			catch (Exception ex)
			{
				log.error("Launch failed for {}", characterId, ex);
				frame.setAccountStatus(characterId, "Ready");
				frame.setStatus("Launch failed - the Jagex session may have expired; re-launch "
					+ account.getDisplayName() + " once via the Jagex Launcher to refresh it");
			}
			finally
			{
				RUNNING_CLIENTS.decrementAndGet();
			}
		}, "rift-launch-" + characterId).start();
	}

	/**
	 * Launches with a fresh Rift session handed off over stdin when signed in, else a plain Jagex-only
	 * launch. Refreshes the access token first so the client starts with a full-lifetime token.
	 */
	private static Process launchWithSession(ClientLauncher clientLauncher, JxCredentials creds, File javaw)
		throws Exception
	{
		if (SESSION.get() != null)
		{
			try
			{
				Session fresh = AUTH_FLOW.resume();
				if (fresh != null)
				{
					SESSION.set(fresh);
					String handoff = new LaunchHandoff(fresh.getAccessToken(), fresh.getRefreshToken(),
						fresh.getExpiresAt(), RiftConfig.apiBaseUrl(), RiftConfig.SUPABASE_ANON_KEY,
						RiftConfig.SUPABASE_URL).toJson();
					return clientLauncher.launch(creds, handoff, javaw, CLIENT_JAR);
				}
			}
			catch (Exception ex)
			{
				log.warn("Could not refresh the Rift session ({}); launching without managed plugins",
					ex.getClass().getSimpleName());
			}
		}
		return clientLauncher.launch(creds, javaw, CLIENT_JAR);
	}
}
