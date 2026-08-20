package rift.launcher.web;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.security.spec.KeySpec;
import java.util.Arrays;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import lombok.extern.slf4j.Slf4j;
import rift.launcher.crypto.Crypto;

/**
 * A local credential that unlocks developer mode while the Rift API is unreachable.
 *
 * <p>Developer mode is normally gated on a license key re-verified against the server at every launch,
 * which fails closed. That is the right default, but it makes the launcher useless for plugin work
 * whenever the API is down — including the ordinary case of not having the dev site running — because
 * the client then launches in standard mode and never loads {@code ~/.rift/dev-plugins}.
 *
 * <h2>What this deliberately is not</h2>
 * There is <b>no default credential and no hardcoded fallback</b>. A build that ships without
 * {@code devoffline.dat} has no way in at all: {@link #isConfigured()} is false and the dialog offers
 * setup rather than entry. Compiling a fixed username and password into the launcher would have been
 * simpler and would have handed every copy of it the same working bypass.
 *
 * <p>The credential is stored as a salted PBKDF2 hash, then encrypted at rest like the other secrets
 * here — so the file is neither a password nor a usable token if it is copied off the machine.
 *
 * <h2>Residual risk, stated plainly</h2>
 * Anyone who controls this machine can create this file themselves, so this is not a security boundary
 * against a determined local user. It does not need to be: developer mode only permits sideloading the
 * user's <em>own</em> local jars. Paid plugins are delivered through the entitlement path, which is
 * verified server-side and is unaffected by anything here. This keeps an honest developer working
 * offline; it is not trying to stop an attacker.
 */
@Slf4j
public final class OfflineDevUnlock
{
	/** Cost factor. High enough that guessing a weak password from the file is slow. */
	private static final int ITERATIONS = 120_000;
	private static final int KEY_BITS = 256;
	private static final int SALT_BYTES = 16;

	private final File file;
	private final Crypto crypto;

	public OfflineDevUnlock(File file, Crypto crypto)
	{
		this.file = file;
		this.crypto = crypto;
	}

	/** Whether an offline credential has been set up on this machine. */
	public boolean isConfigured()
	{
		return read() != null;
	}

	/**
	 * Sets (or replaces) the offline credential.
	 *
	 * @param password cleared by the caller; not retained here
	 */
	public synchronized void configure(String username, char[] password) throws IOException
	{
		if (username == null || username.trim().isEmpty() || password == null || password.length == 0)
		{
			throw new IllegalArgumentException("username and password are required");
		}
		byte[] salt = new byte[SALT_BYTES];
		new SecureRandom().nextBytes(salt);
		byte[] hash = pbkdf2(password, salt);

		String record = username.trim() + "\n"
			+ Base64.getEncoder().encodeToString(salt) + "\n"
			+ Base64.getEncoder().encodeToString(hash);

		File parent = file.getParentFile();
		if (parent != null)
		{
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		Files.write(file.toPath(), crypto.protect(record.getBytes(StandardCharsets.UTF_8)));
		Arrays.fill(hash, (byte) 0);
	}

	/**
	 * Whether these credentials match the stored ones.
	 *
	 * <p>Compared in constant time, and a missing or unreadable file is a failure rather than a pass —
	 * the one mistake here that would turn the whole thing into an open door.
	 */
	public boolean verify(String username, char[] password)
	{
		String[] record = read();
		if (record == null || username == null || password == null)
		{
			return false;
		}
		byte[] expected;
		byte[] actual;
		try
		{
			byte[] salt = Base64.getDecoder().decode(record[1]);
			expected = Base64.getDecoder().decode(record[2]);
			actual = pbkdf2(password, salt);
		}
		catch (RuntimeException ex)
		{
			log.warn("Offline developer credential is unreadable ({})", ex.getClass().getSimpleName());
			return false;
		}
		boolean nameOk = constantTimeEquals(
			record[0].getBytes(StandardCharsets.UTF_8), username.trim().getBytes(StandardCharsets.UTF_8));
		boolean hashOk = constantTimeEquals(expected, actual);
		Arrays.fill(actual, (byte) 0);
		// Both are always evaluated: short-circuiting on the username would leak which half was wrong.
		return nameOk & hashOk;
	}

	/** Forgets the offline credential. */
	public synchronized void clear()
	{
		if (file.exists() && !file.delete())
		{
			log.warn("Could not delete offline developer credential {}", file);
		}
	}

	/** {username, saltB64, hashB64}, or null when absent or unreadable. */
	private String[] read()
	{
		if (!file.exists())
		{
			return null;
		}
		try
		{
			String plain = new String(crypto.unprotect(Files.readAllBytes(file.toPath())),
				StandardCharsets.UTF_8);
			String[] parts = plain.split("\n");
			return parts.length == 3 ? parts : null;
		}
		catch (Exception ex)
		{
			// Log only the failure type -- never the message, which could carry credential material.
			log.warn("Could not read offline developer credential {} ({}); treating as absent",
				file, ex.getClass().getSimpleName());
			return null;
		}
	}

	private static byte[] pbkdf2(char[] password, byte[] salt)
	{
		try
		{
			KeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_BITS);
			return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
		}
		catch (Exception ex)
		{
			throw new IllegalStateException("PBKDF2 unavailable", ex);
		}
	}

	private static boolean constantTimeEquals(byte[] a, byte[] b)
	{
		if (a.length != b.length)
		{
			return false;
		}
		int diff = 0;
		for (int i = 0; i < a.length; i++)
		{
			diff |= a[i] ^ b[i];
		}
		return diff == 0;
	}
}
