package rift.launcher.proxy;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * A minimal SOCKS5 server for tests.
 *
 * <p>Exists so the difference between "wrong credentials" and "host is down" can actually be verified.
 * Those two are indistinguishable from the outside, {@link ProxyTester} tells them apart by inspecting
 * the failure, and that logic is worth pinning — a user told to check their network when the password
 * is wrong will waste a long time.
 *
 * <p>Speaks just enough of RFC 1928/1929 to get through the handshake and answer a CONNECT. It never
 * relays traffic: the point is the negotiation, not the payload.
 */
final class StubSocks5Server implements Closeable
{
	private final ServerSocket serverSocket;
	private final boolean requireAuth;
	private final boolean acceptCredentials;
	private final Thread thread;
	private volatile boolean running = true;

	private StubSocks5Server(boolean requireAuth, boolean acceptCredentials) throws Exception
	{
		this.requireAuth = requireAuth;
		this.acceptCredentials = acceptCredentials;
		this.serverSocket = new ServerSocket(0);
		this.thread = new Thread(this::acceptLoop, "stub-socks5");
		this.thread.setDaemon(true);
		this.thread.start();
	}

	/** Accepts any client and reports every CONNECT as succeeded. */
	static StubSocks5Server open() throws Exception
	{
		return new StubSocks5Server(false, true);
	}

	/** Demands username/password and rejects whatever it is given. */
	static StubSocks5Server rejectingAuth() throws Exception
	{
		return new StubSocks5Server(true, false);
	}

	int port()
	{
		return serverSocket.getLocalPort();
	}

	private void acceptLoop()
	{
		while (running)
		{
			try (Socket client = serverSocket.accept())
			{
				handle(client);
			}
			catch (Exception ignored)
			{
				// Socket closed, or the client gave up mid-handshake -- expected while tests tear down.
			}
		}
	}

	private void handle(Socket client) throws Exception
	{
		DataInputStream in = new DataInputStream(client.getInputStream());
		OutputStream out = client.getOutputStream();

		// Greeting: VER, NMETHODS, METHODS...
		in.readByte();
		int methodCount = in.readUnsignedByte();
		skip(in, methodCount);

		// Method selection: 0x00 no auth, 0x02 username/password.
		out.write(new byte[]{0x05, (byte) (requireAuth ? 0x02 : 0x00)});
		out.flush();

		if (requireAuth)
		{
			in.readByte();                              // sub-negotiation version
			skip(in, in.readUnsignedByte());            // username
			skip(in, in.readUnsignedByte());            // password
			out.write(new byte[]{0x01, (byte) (acceptCredentials ? 0x00 : 0x01)});
			out.flush();
			if (!acceptCredentials)
			{
				return; // the client will surface this as an authentication failure
			}
		}

		// CONNECT request: VER, CMD, RSV, ATYP, ADDR, PORT.
		in.readByte();
		in.readByte();
		in.readByte();
		int addressType = in.readUnsignedByte();
		if (addressType == 0x01)
		{
			skip(in, 4);
		}
		else if (addressType == 0x03)
		{
			skip(in, in.readUnsignedByte());
		}
		else
		{
			skip(in, 16);
		}
		skip(in, 2); // port

		// Reply "succeeded", bound to 0.0.0.0:0. No relaying: the handshake is what is under test.
		out.write(new byte[]{0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0});
		out.flush();
	}

	private static void skip(InputStream in, int count) throws Exception
	{
		for (int i = 0; i < count; i++)
		{
			if (in.read() < 0)
			{
				return;
			}
		}
	}

	@Override
	public void close()
	{
		running = false;
		try
		{
			serverSocket.close();
		}
		catch (Exception ignored)
		{
			// Nothing useful to do while shutting a test fixture down.
		}
		thread.interrupt();
	}
}
