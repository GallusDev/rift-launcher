package rift.launcher.ui;

import java.util.Arrays;
import rift.launcher.account.Account;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

public class AccountTableModelTest
{
	private static final long NOW = 1_000_000_000L;
	private static final long MINUTE = 60_000L;
	private static final long HOUR = 60 * MINUTE;
	private static final long DAY = 24 * HOUR;

	@Test
	public void assignedProxyIsShownByNickname()
	{
		AccountTableModel model = new AccountTableModel();
		rift.launcher.proxy.ProxyEntry proxy =
			new rift.launcher.proxy.ProxyEntry("home", "1.2.3.4", 1080, null, null);
		proxy.setId("p1");
		model.setProxies(java.util.Collections.singletonList(proxy));
		model.setAccounts(Arrays.asList(
			new Account("c1", "Zezima", "s1", 1L, "p1"),
			new Account("c2", "Durial", "s2", 2L, null)));

		assertEquals("home", model.getValueAt(0, AccountTableModel.PROXY_COLUMN));
		assertEquals(AccountTableModel.NO_PROXY, model.getValueAt(1, AccountTableModel.PROXY_COLUMN));
	}

	@Test
	public void deletedProxyReadsAsMissingNotDirect()
	{
		AccountTableModel model = new AccountTableModel();
		model.setAccounts(java.util.Collections.singletonList(
			new Account("c1", "Zezima", "s1", 1L, "deleted-id")));

		// Showing "Direct" would hide why the launch refuses -- the account is assigned to a proxy
		// that no longer exists, and connecting directly is exactly what the user did not ask for.
		assertEquals("(missing)", model.getValueAt(0, AccountTableModel.PROXY_COLUMN));
	}

	@Test
	public void proxyChoicesOfferDirectPlusEverySavedProxy()
	{
		AccountTableModel model = new AccountTableModel();
		rift.launcher.proxy.ProxyEntry a = new rift.launcher.proxy.ProxyEntry("a", "1.1.1.1", 1080, null, null);
		a.setId("id-a");
		model.setProxies(java.util.Collections.singletonList(a));

		assertEquals(2, model.proxyChoices().length);
		assertEquals(AccountTableModel.NO_PROXY, model.proxyChoices()[0]);
		assertEquals("a", model.proxyChoices()[1]);
		assertEquals("id-a", model.proxyIdForChoice("a"));
		assertEquals(null, model.proxyIdForChoice(AccountTableModel.NO_PROXY));
	}

	@Test
	public void exposesNameStatusAndSessionAgeColumns()
	{
		AccountTableModel model = new AccountTableModel();
		model.setAccounts(Arrays.asList(
			new Account("c1", "Zezima", "s1", 1L, null),
			new Account("c2", "Durial", "s2", 2L, null)));

		assertEquals(2, model.getRowCount());
		assertEquals(5, model.getColumnCount());
		assertEquals("Name", model.getColumnName(0));
		assertEquals("Status", model.getColumnName(1));
		assertEquals("Session age", model.getColumnName(2));
		assertEquals("Proxy", model.getColumnName(AccountTableModel.PROXY_COLUMN));
		assertEquals("Launch", model.getColumnName(AccountTableModel.LAUNCH_COLUMN));
		assertEquals("Zezima", model.getValueAt(0, 0));
		assertEquals("Ready", model.getValueAt(0, 1));
		// An account with no proxy connects directly.
		assertEquals(AccountTableModel.NO_PROXY, model.getValueAt(0, AccountTableModel.PROXY_COLUMN));
		assertEquals("Launch", model.getValueAt(0, AccountTableModel.LAUNCH_COLUMN));
	}

	@Test
	public void onlyTheLaunchColumnIsEditable()
	{
		AccountTableModel model = new AccountTableModel();
		model.setAccounts(Arrays.asList(new Account("c1", "Zezima", "s1", 1L, null)));

		// Editability is how Swing routes a click to the button cell editor; the data columns must
		// stay read-only so a stray double-click can't turn an account name into a text field.
		assertFalse(model.isCellEditable(0, 0));
		assertFalse(model.isCellEditable(0, 1));
		assertFalse(model.isCellEditable(0, 2));
		assertTrue(model.isCellEditable(0, AccountTableModel.LAUNCH_COLUMN));
	}

	@Test
	public void sessionAgeColumnShowsAgeSinceImport()
	{
		AccountTableModel model = new AccountTableModel(() -> NOW);
		model.setAccounts(Arrays.asList(
			new Account("c1", "Zezima", "s1", NOW - (26 * HOUR), null),
			new Account("c2", "Durial", "s2", NOW - (5 * MINUTE), null)));

		assertEquals("1d 2h", model.getValueAt(0, 2));
		assertEquals("5m", model.getValueAt(1, 2));
	}

	@Test
	public void formatsAgeAcrossUnits()
	{
		assertEquals("just now", AccountTableModel.formatAge(30_000L));
		assertEquals("5m", AccountTableModel.formatAge(5 * MINUTE));
		assertEquals("59m", AccountTableModel.formatAge(59 * MINUTE));
		assertEquals("2h", AccountTableModel.formatAge(2 * HOUR));
		assertEquals("1h 30m", AccountTableModel.formatAge(HOUR + (30 * MINUTE)));
		assertEquals("23h 59m", AccountTableModel.formatAge(DAY - MINUTE));
		assertEquals("1d", AccountTableModel.formatAge(DAY));
		assertEquals("1d 2h", AccountTableModel.formatAge(DAY + (2 * HOUR)));
		assertEquals("9d 5h", AccountTableModel.formatAge((9 * DAY) + (5 * HOUR)));
	}

	@Test
	public void clampsNegativeAgeFromClockSkew()
	{
		assertEquals("just now", AccountTableModel.formatAge(-5_000L));
	}

	@Test
	public void setStatusUpdatesTheRow()
	{
		AccountTableModel model = new AccountTableModel();
		model.setAccounts(Arrays.asList(new Account("c1", "Zezima", "s1", 1L, null)));
		model.setStatus("c1", "Playing");
		assertEquals("Playing", model.getValueAt(0, 1));
	}

	@Test
	public void accountAtReturnsRow()
	{
		AccountTableModel model = new AccountTableModel();
		model.setAccounts(Arrays.asList(new Account("c1", "Zezima", "s1", 1L, null)));
		assertEquals("c1", model.accountAt(0).getCharacterId());
	}
}
