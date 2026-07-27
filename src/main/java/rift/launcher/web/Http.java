package rift.launcher.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Minimal HTTP transport seam. Abstracted so the website/auth clients can be unit-tested with a stub
 * instead of real network calls. One method covers every call we make (GET/POST, JSON or binary body).
 */
public interface Http
{
	/**
	 * @param method  HTTP method ("GET", "POST", ...).
	 * @param url     absolute URL.
	 * @param headers request headers (may be empty).
	 * @param body    request body, or {@code null} for none.
	 */
	Reply send(String method, String url, Map<String, String> headers, byte[] body) throws IOException;

	/** An HTTP response: status code plus raw body bytes. */
	final class Reply
	{
		private final int status;
		private final byte[] body;

		public Reply(int status, byte[] body)
		{
			this.status = status;
			this.body = body;
		}

		public int status()
		{
			return status;
		}

		public byte[] body()
		{
			return body;
		}

		public String text()
		{
			return body == null ? "" : new String(body, StandardCharsets.UTF_8);
		}
	}
}
