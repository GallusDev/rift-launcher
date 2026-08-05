package rift.launcher.proxy;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Turns whatever a proxy provider hands out into {@link ProxyEntry}s.
 *
 * <p>Users paste provider output verbatim, and providers agree on nothing. Accepting only one format
 * pushes the reformatting work onto the user for no reason, so this takes every shape in common use:
 *
 * <pre>
 *   host:port                        host:port:user:pass
 *   user:pass@host:port              scheme://user:pass@host:port
 *   [ipv6]:port                      [ipv6]:port:user:pass
 * </pre>
 *
 * <p>Two ordering rules matter and are easy to get wrong, so both are pinned by tests:
 * credentials are split on the <b>first</b> colon (a password may contain colons), and the
 * host is taken after the <b>last</b> {@code @} (a password may contain {@code @}).
 */
public final class ProxyParser
{
	private static final int MIN_PORT = 1;
	private static final int MAX_PORT = 65535;

	private ProxyParser()
	{
	}

	/** A line that could not be read, kept with its position so the user can find it in their paste. */
	public static final class Failure
	{
		private final int lineNumber;
		private final String line;

		Failure(int lineNumber, String line)
		{
			this.lineNumber = lineNumber;
			this.line = line;
		}

		public int getLineNumber()
		{
			return lineNumber;
		}

		public String getLine()
		{
			return line;
		}
	}

	/** The outcome of a bulk paste: what parsed, what didn't, and what was a repeat. */
	public static final class BulkResult
	{
		private final List<ProxyEntry> parsed;
		private final List<Failure> failures;
		private final int duplicatesSkipped;

		BulkResult(List<ProxyEntry> parsed, List<Failure> failures, int duplicatesSkipped)
		{
			this.parsed = parsed;
			this.failures = failures;
			this.duplicatesSkipped = duplicatesSkipped;
		}

		public List<ProxyEntry> getParsed()
		{
			return parsed;
		}

		public List<Failure> getFailures()
		{
			return failures;
		}

		public int getDuplicatesSkipped()
		{
			return duplicatesSkipped;
		}
	}

	/** One proxy, or {@code null} if the text isn't one. */
	public static ProxyEntry parse(String input)
	{
		if (input == null)
		{
			return null;
		}
		String s = input.trim();
		if (s.isEmpty())
		{
			return null;
		}

		// Drop any scheme. Which one it is doesn't change how we connect -- we always speak SOCKS5 --
		// and provider lists label SOCKS endpoints "http://" often enough that rejecting it would only
		// annoy people.
		int scheme = s.indexOf("://");
		if (scheme > 0)
		{
			s = s.substring(scheme + 3);
		}

		String username = null;
		String password = null;

		// Split on the LAST '@': a password containing '@' would otherwise swallow the host.
		int at = s.lastIndexOf('@');
		if (at >= 0)
		{
			String creds = s.substring(0, at);
			s = s.substring(at + 1);
			// FIRST colon: everything after it is the password, colons included.
			int colon = creds.indexOf(':');
			if (colon < 0)
			{
				username = creds.isEmpty() ? null : creds;
			}
			else
			{
				username = creds.substring(0, colon);
				password = creds.substring(colon + 1);
			}
		}

		return s.startsWith("[") ? parseBracketed(s, username, password) : parsePlain(s, username, password);
	}

	/** {@code [2001:db8::1]:1080} — the address is bracketed so its colons aren't separators. */
	private static ProxyEntry parseBracketed(String s, String username, String password)
	{
		int close = s.indexOf(']');
		if (close < 1 || close + 2 > s.length() || s.charAt(close + 1) != ':')
		{
			return null;
		}
		String host = s.substring(1, close);
		String rest = s.substring(close + 2);
		return build(host, rest, username, password);
	}

	private static ProxyEntry parsePlain(String s, String username, String password)
	{
		int colon = s.indexOf(':');
		if (colon < 1)
		{
			return null;
		}
		return build(s.substring(0, colon), s.substring(colon + 1), username, password);
	}

	/**
	 * @param rest everything after the host: either just the port, or {@code port:user:pass} when the
	 *             credentials were given in trailing form rather than before an {@code @}.
	 */
	private static ProxyEntry build(String host, String rest, String username, String password)
	{
		if (host.isEmpty() || rest.isEmpty())
		{
			return null;
		}

		String portText = rest;
		int colon = rest.indexOf(':');
		if (colon >= 0)
		{
			// host:port:user:pass. Credentials already parsed from an '@' form take precedence; this is
			// the other spelling, not a second set.
			portText = rest.substring(0, colon);
			String creds = rest.substring(colon + 1);
			int credColon = creds.indexOf(':');
			if (credColon < 0)
			{
				// "host:port:something" is ambiguous -- a username with no password, or a typo. Refuse
				// rather than guess and silently save an unusable proxy.
				return null;
			}
			if (username == null)
			{
				username = creds.substring(0, credColon);
				password = creds.substring(credColon + 1);
			}
		}

		int port;
		try
		{
			port = Integer.parseInt(portText.trim());
		}
		catch (NumberFormatException ex)
		{
			return null;
		}
		if (port < MIN_PORT || port > MAX_PORT)
		{
			return null;
		}

		ProxyEntry entry = new ProxyEntry();
		entry.setHost(host.trim());
		entry.setPort(port);
		entry.setUsername(username == null || username.isEmpty() ? null : username);
		entry.setPassword(password == null || password.isEmpty() ? null : password);
		return entry;
	}

	/**
	 * Parses a whole pasted list — the common case, since providers hand out dozens at a time. Blank
	 * lines and {@code #} comments are skipped silently; anything else that fails is reported with its
	 * line number rather than disappearing.
	 */
	public static BulkResult parseAll(String text)
	{
		List<ProxyEntry> parsed = new ArrayList<>();
		List<Failure> failures = new ArrayList<>();
		Set<String> seen = new LinkedHashSet<>();
		int duplicates = 0;

		if (text == null)
		{
			return new BulkResult(parsed, failures, 0);
		}

		String[] lines = text.split("\\r?\\n", -1);
		for (int i = 0; i < lines.length; i++)
		{
			String line = lines[i];
			String trimmed = line.trim();
			if (trimmed.isEmpty() || trimmed.startsWith("#"))
			{
				continue;
			}

			ProxyEntry entry = parse(trimmed);
			if (entry == null)
			{
				failures.add(new Failure(i + 1, line));
				continue;
			}
			if (!seen.add(entry.endpoint()))
			{
				duplicates++;
				continue;
			}
			parsed.add(entry);
		}

		// Auto-named so importing fifty proxies isn't fifty naming decisions; the user can rename any
		// that matter. Numbered after de-duplication so the names have no gaps.
		for (int i = 0; i < parsed.size(); i++)
		{
			parsed.get(i).setNickname("proxy-" + (i + 1));
		}
		return new BulkResult(parsed, failures, duplicates);
	}
}
