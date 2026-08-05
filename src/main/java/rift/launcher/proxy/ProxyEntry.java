package rift.launcher.proxy;

import com.google.gson.annotations.SerializedName;

/**
 * One saved SOCKS5 proxy, plus the result of the last test.
 *
 * <p>Test results are stored because they are the useful part of the UI — a proxy that connects but
 * exits from an unexpected IP looks identical to a good one until you can see the exit IP. They are a
 * snapshot, not a property: proxies die constantly, which is why {@code lastTestedAt} is shown too.
 */
public final class ProxyEntry
{
	public enum Status
	{
		/** Never tested. */
		UNKNOWN,
		/** Handshake, auth and a connection to a live game port all succeeded. */
		OK,
		/** Reachable, but it rejected the credentials — a different fix from "unreachable". */
		AUTH_FAILED,
		/** Could not connect, or it refused the game port. */
		UNREACHABLE
	}

	@SerializedName("id")
	private String id;

	@SerializedName("nickname")
	private String nickname;

	@SerializedName("host")
	private String host;

	@SerializedName("port")
	private int port;

	@SerializedName("username")
	private String username;

	@SerializedName("password")
	private String password;

	@SerializedName("last_status")
	private Status lastStatus = Status.UNKNOWN;

	@SerializedName("last_latency_ms")
	private Integer lastLatencyMs;

	@SerializedName("last_exit_ip")
	private String lastExitIp;

	@SerializedName("last_tested_at")
	private Long lastTestedAt;

	public ProxyEntry()
	{
	}

	public ProxyEntry(String nickname, String host, int port, String username, String password)
	{
		this.nickname = nickname;
		this.host = host;
		this.port = port;
		this.username = username;
		this.password = password;
	}

	/** Identity for de-duplication: the endpoint, ignoring nickname and credentials. */
	public String endpoint()
	{
		return host + ":" + port;
	}

	public boolean hasAuth()
	{
		return username != null && !username.isEmpty();
	}

	public String getId()
	{
		return id;
	}

	public void setId(String id)
	{
		this.id = id;
	}

	public String getNickname()
	{
		return nickname;
	}

	public void setNickname(String nickname)
	{
		this.nickname = nickname;
	}

	public String getHost()
	{
		return host;
	}

	public void setHost(String host)
	{
		this.host = host;
	}

	public int getPort()
	{
		return port;
	}

	public void setPort(int port)
	{
		this.port = port;
	}

	public String getUsername()
	{
		return username;
	}

	public void setUsername(String username)
	{
		this.username = username;
	}

	public String getPassword()
	{
		return password;
	}

	public void setPassword(String password)
	{
		this.password = password;
	}

	public Status getLastStatus()
	{
		return lastStatus == null ? Status.UNKNOWN : lastStatus;
	}

	public void setLastStatus(Status lastStatus)
	{
		this.lastStatus = lastStatus;
	}

	public Integer getLastLatencyMs()
	{
		return lastLatencyMs;
	}

	public void setLastLatencyMs(Integer lastLatencyMs)
	{
		this.lastLatencyMs = lastLatencyMs;
	}

	public String getLastExitIp()
	{
		return lastExitIp;
	}

	public void setLastExitIp(String lastExitIp)
	{
		this.lastExitIp = lastExitIp;
	}

	public Long getLastTestedAt()
	{
		return lastTestedAt;
	}

	public void setLastTestedAt(Long lastTestedAt)
	{
		this.lastTestedAt = lastTestedAt;
	}
}
