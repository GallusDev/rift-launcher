package rift.launcher.proxy;

import java.util.List;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import org.junit.Test;

/**
 * The formats providers actually hand out. This is the piece users hit first and judge us on, so it
 * accepts everything reasonable and reports what it cannot read rather than dropping it.
 */
public class ProxyParserTest
{
	private static void assertParsed(String input, String host, int port, String user, String pass)
	{
		ProxyEntry e = ProxyParser.parse(input);
		assertNotNull("should parse: " + input, e);
		assertEquals(input, host, e.getHost());
		assertEquals(input, port, e.getPort());
		assertEquals(input, user, e.getUsername());
		assertEquals(input, pass, e.getPassword());
	}

	@Test
	public void hostAndPort()
	{
		assertParsed("1.2.3.4:1080", "1.2.3.4", 1080, null, null);
		assertParsed("proxy.example.com:9050", "proxy.example.com", 9050, null, null);
	}

	@Test
	public void hostPortUserPass()
	{
		// Vortex's format.
		assertParsed("1.2.3.4:1080:alice:s3cret", "1.2.3.4", 1080, "alice", "s3cret");
	}

	@Test
	public void curlStyleCredentialsBeforeHost()
	{
		assertParsed("alice:s3cret@1.2.3.4:1080", "1.2.3.4", 1080, "alice", "s3cret");
	}

	@Test
	public void schemePrefixesAreAccepted()
	{
		assertParsed("socks5://1.2.3.4:1080", "1.2.3.4", 1080, null, null);
		assertParsed("socks5h://alice:s3cret@1.2.3.4:1080", "1.2.3.4", 1080, "alice", "s3cret");
		assertParsed("SOCKS://1.2.3.4:1080", "1.2.3.4", 1080, null, null);
		// http:// appears on provider lists constantly even for SOCKS endpoints; accept and let the
		// user sort it out rather than rejecting something that is probably fine.
		assertParsed("http://1.2.3.4:8080", "1.2.3.4", 8080, null, null);
	}

	@Test
	public void passwordContainingColonSurvives()
	{
		// The trap: with host:port:user:pass you cannot just split on ":" and take 4 parts, and with
		// user:pass@host the credentials must be split on the FIRST colon only.
		assertParsed("alice:pa:ss:w:rd@1.2.3.4:1080", "1.2.3.4", 1080, "alice", "pa:ss:w:rd");
		assertParsed("1.2.3.4:1080:alice:pa:ss", "1.2.3.4", 1080, "alice", "pa:ss");
	}

	@Test
	public void passwordContainingAtSignSurvives()
	{
		// Split on the LAST '@', or a password containing '@' takes the host with it.
		assertParsed("alice:p@ss@1.2.3.4:1080", "1.2.3.4", 1080, "alice", "p@ss");
	}

	@Test
	public void ipv6IsBracketed()
	{
		assertParsed("[2001:db8::1]:1080", "2001:db8::1", 1080, null, null);
		assertParsed("[2001:db8::1]:1080:alice:s3cret", "2001:db8::1", 1080, "alice", "s3cret");
		assertParsed("socks5://alice:s3cret@[2001:db8::1]:1080", "2001:db8::1", 1080, "alice", "s3cret");
	}

	@Test
	public void surroundingWhitespaceIsIgnored()
	{
		assertParsed("   1.2.3.4:1080   ", "1.2.3.4", 1080, null, null);
		assertParsed("\t1.2.3.4:1080\r", "1.2.3.4", 1080, null, null);
	}

	@Test
	public void unreadableInputReturnsNull()
	{
		assertNull(ProxyParser.parse(null));
		assertNull(ProxyParser.parse(""));
		assertNull(ProxyParser.parse("   "));
		assertNull(ProxyParser.parse("not-a-proxy"));
		assertNull("no port", ProxyParser.parse("1.2.3.4"));
		assertNull("port not a number", ProxyParser.parse("1.2.3.4:abc"));
		assertNull("port out of range", ProxyParser.parse("1.2.3.4:0"));
		assertNull("port out of range", ProxyParser.parse("1.2.3.4:70000"));
		assertNull("empty host", ProxyParser.parse(":1080"));
		assertNull("three parts is ambiguous", ProxyParser.parse("1.2.3.4:1080:alice"));
	}

	@Test
	public void bulkParseReportsWhatItCouldNotRead()
	{
		String pasted = String.join("\n",
			"1.2.3.4:1080",
			"",                          // blank lines are skipped, not errors
			"# a comment from the provider",
			"garbage here",
			"5.6.7.8:1080:bob:hunter2",
			"   ",
			"9.9.9.9:not-a-port");

		ProxyParser.BulkResult result = ProxyParser.parseAll(pasted);

		assertEquals(2, result.getParsed().size());
		assertEquals("1.2.3.4", result.getParsed().get(0).getHost());
		assertEquals("bob", result.getParsed().get(1).getUsername());

		// Failures carry the line number so the user can find them in what they pasted.
		List<ProxyParser.Failure> failures = result.getFailures();
		assertEquals(2, failures.size());
		assertEquals(4, failures.get(0).getLineNumber());
		assertEquals("garbage here", failures.get(0).getLine());
		assertEquals(7, failures.get(1).getLineNumber());
	}

	@Test
	public void bulkParseAutoNamesEntriesUniquely()
	{
		ProxyParser.BulkResult result = ProxyParser.parseAll("1.2.3.4:1080\n5.6.7.8:1080");

		// A 50-proxy import should not require naming 50 things by hand.
		assertEquals("proxy-1", result.getParsed().get(0).getNickname());
		assertEquals("proxy-2", result.getParsed().get(1).getNickname());
	}

	@Test
	public void duplicatesWithinAPasteAreDropped()
	{
		// Provider lists repeat entries surprisingly often.
		ProxyParser.BulkResult result = ProxyParser.parseAll("1.2.3.4:1080\n1.2.3.4:1080\nsocks5://1.2.3.4:1080");
		assertEquals(1, result.getParsed().size());
		assertEquals(2, result.getDuplicatesSkipped());
	}
}
