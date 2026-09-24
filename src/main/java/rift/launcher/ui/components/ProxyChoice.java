package rift.launcher.ui.components;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import rift.launcher.proxy.ProxyEntry;

/**
 * One option in an account's proxy dropdown, and the rule for building the list of them.
 *
 * <p>Pure, so the one case that matters most -- an account pointing at a proxy that no longer exists
 * -- can be tested without Swing.
 */
public final class ProxyChoice
{
	/** What an option stands for. */
	public enum Kind
	{
		/** No proxy: the account connects from this machine's own address. */
		DIRECT,
		/** A configured proxy. */
		PROXY,
		/**
		 * The account's assigned proxy, which has since been deleted.
		 *
		 * <p>Shown rather than hidden. Launching such an account is refused, deliberately, so it
		 * does not quietly fall back to the real IP -- and the dropdown is where the user looks to find
		 * out why, and where they fix it by choosing something else.
		 */
		MISSING
	}

	private final Kind kind;
	private final String id;
	private final String label;
	private final String detail;
	private final ProxyEntry.Status status;

	private ProxyChoice(Kind kind, String id, String label, String detail, ProxyEntry.Status status)
	{
		this.kind = kind;
		this.id = id;
		this.label = label;
		this.detail = detail;
		this.status = status;
	}

	public Kind getKind()
	{
		return kind;
	}

	/** The proxy id to store on the account; null for a direct connection. */
	public String getId()
	{
		return id;
	}

	public String getLabel()
	{
		return label;
	}

	/** Where it goes, for a tooltip; null when there is nothing to add. */
	public String getDetail()
	{
		return detail;
	}

	/** The result of the proxy's last test; null for anything that is not a configured proxy. */
	public ProxyEntry.Status getStatus()
	{
		return status;
	}

	/** Whether choosing this would change an account currently assigned {@code proxyId}. */
	public boolean differsFrom(String proxyId)
	{
		return !Objects.equals(id, proxyId);
	}

	/** Shown by accessibility tools and anything that falls back to toString. */
	@Override
	public String toString()
	{
		return label;
	}

	/** The options for an account's dropdown, together with which one is currently selected. */
	public static final class Options
	{
		private final List<ProxyChoice> choices;
		private final int selected;

		Options(List<ProxyChoice> choices, int selected)
		{
			this.choices = Collections.unmodifiableList(choices);
			this.selected = selected;
		}

		public List<ProxyChoice> getChoices()
		{
			return choices;
		}

		public int getSelected()
		{
			return selected;
		}
	}

	/**
	 * Direct first, then every configured proxy in the order the Proxies page lists them. If the
	 * account is assigned a proxy that is not among them, that assignment is kept as a
	 * {@link Kind#MISSING} option straight after Direct, and selected -- never silently shown as
	 * Direct, because Direct is precisely what it will not do.
	 */
	public static Options forAccount(List<ProxyEntry> proxies, String proxyId)
	{
		List<ProxyChoice> choices = new ArrayList<>();
		choices.add(new ProxyChoice(Kind.DIRECT, null, "Direct connection",
			"No proxy - connects from this machine's own address", null));

		int selected = 0;
		for (ProxyEntry p : proxies == null ? Collections.<ProxyEntry>emptyList() : proxies)
		{
			if (p == null || p.getId() == null)
			{
				continue;
			}
			String address = p.getHost() + ":" + p.getPort();
			String name = p.getNickname() == null || p.getNickname().trim().isEmpty() ? address : p.getNickname();
			if (p.getId().equals(proxyId))
			{
				selected = choices.size();
			}
			choices.add(new ProxyChoice(Kind.PROXY, p.getId(), name, address, p.getLastStatus()));
		}

		if (proxyId != null && selected == 0)
		{
			choices.add(1, new ProxyChoice(Kind.MISSING, proxyId, "Deleted proxy",
				"This account's proxy was deleted, so launching it is refused rather than connecting "
					+ "from your real IP. Choose another proxy, or Direct.", null));
			selected = 1;
		}
		return new Options(choices, selected);
	}
}
