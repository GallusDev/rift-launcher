package rift.launcher.web;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import rift.launcher.crypto.Crypto;

/**
 * Persists the Supabase refresh token encrypted at rest (DPAPI in production, via {@link Crypto}), so
 * the user isn't re-prompted for Discord sign-in every launch. The access token is never persisted —
 * it stays in memory and is refreshed from this token. Nothing here is ever logged in plaintext.
 */
@Slf4j
public final class AuthStore
{
	private final File file;
	private final Crypto crypto;

	public AuthStore(File file, Crypto crypto)
	{
		this.file = file;
		this.crypto = crypto;
	}

	public synchronized void save(String refreshToken) throws IOException
	{
		byte[] encrypted = crypto.protect(refreshToken.getBytes(StandardCharsets.UTF_8));
		File parent = file.getParentFile();
		if (parent != null)
		{
			//noinspection ResultOfMethodCallIgnored
			parent.mkdirs();
		}
		Files.write(file.toPath(), encrypted);
	}

	/** The stored refresh token, or {@code null} if none is saved or it can't be read. */
	public synchronized String load()
	{
		if (!file.exists())
		{
			return null;
		}
		try
		{
			byte[] decrypted = crypto.unprotect(Files.readAllBytes(file.toPath()));
			return new String(decrypted, StandardCharsets.UTF_8);
		}
		catch (Exception ex)
		{
			// Log only the failure type — never the exception message, which can carry the token.
			log.warn("Could not read auth file {} ({}); treating as signed out",
				file, ex.getClass().getSimpleName());
			return null;
		}
	}

	public synchronized void clear()
	{
		//noinspection ResultOfMethodCallIgnored
		file.delete();
	}
}
