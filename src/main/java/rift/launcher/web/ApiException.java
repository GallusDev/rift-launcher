package rift.launcher.web;

/** A non-2xx response from the Rift API. Carries the HTTP status and (if present) the error code. */
public class ApiException extends Exception
{
	private final int status;
	private final String code;

	public ApiException(int status, String code, String message)
	{
		super("Rift API " + status + (code == null ? "" : " (" + code + ")") + (message == null ? "" : ": " + message));
		this.status = status;
		this.code = code;
	}

	public int getStatus()
	{
		return status;
	}

	/** The `error.code` from the body, or null if none was parseable. */
	public String getCode()
	{
		return code;
	}
}
