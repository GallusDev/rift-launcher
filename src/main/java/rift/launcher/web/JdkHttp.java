package rift.launcher.web;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Production {@link Http} backed by the JDK 11 {@link HttpClient}. Follows redirects normally; callers
 * that must NOT auto-follow (e.g. reading a 302 {@code Location}) can be handled later if needed.
 */
public final class JdkHttp implements Http
{
	// HTTP/1.1: the JDK client defaults to HTTP/2, which fails ("header parser received no bytes")
	// against a cleartext HTTP/1.1 dev server. 1.1 is safe against the prod HTTPS host too.
	private final HttpClient client = HttpClient.newBuilder()
		.version(HttpClient.Version.HTTP_1_1)
		// /download answers 302 to storage, and the JDK default is NEVER, so downloads would fail
		// outright. NORMAL follows redirects but refuses an HTTPS -> HTTP downgrade.
		.followRedirects(HttpClient.Redirect.NORMAL)
		.connectTimeout(Duration.ofSeconds(20))
		.build();

	@Override
	public Reply send(String method, String url, Map<String, String> headers, byte[] body) throws IOException
	{
		HttpRequest.BodyPublisher publisher = body == null
			? HttpRequest.BodyPublishers.noBody()
			: HttpRequest.BodyPublishers.ofByteArray(body);

		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(60))
			.method(method, publisher);

		if (headers != null)
		{
			headers.forEach(builder::header);
		}

		try
		{
			HttpResponse<byte[]> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
			return new Reply(response.statusCode(), response.body());
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
			throw new IOException("HTTP request interrupted", e);
		}
	}
}
