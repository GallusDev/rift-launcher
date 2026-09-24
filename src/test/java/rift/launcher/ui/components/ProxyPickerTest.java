package rift.launcher.ui.components;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import rift.launcher.account.Account;
import rift.launcher.proxy.ProxyEntry;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class ProxyPickerTest
{
	private static ProxyEntry proxy(String id, String nickname, ProxyEntry.Status status)
	{
		ProxyEntry p = new ProxyEntry(nickname, "203.0.113." + id.length(), 1080, "u", "p");
		p.setId(id);
		p.setLastStatus(status);
		return p;
	}

	private static final List<ProxyEntry> PROXIES = Arrays.asList(
		proxy("p1", "Frankfurt", ProxyEntry.Status.OK),
		proxy("p2", "Dallas", ProxyEntry.Status.UNREACHABLE));

	// --- the choices -----------------------------------------------------------------------------

	@Test
	public void directComesFirstThenEveryProxyInOrder()
	{
		ProxyChoice.Options o = ProxyChoice.forAccount(PROXIES, null);

		assertEquals(3, o.getChoices().size());
		assertEquals(ProxyChoice.Kind.DIRECT, o.getChoices().get(0).getKind());
		assertNull(o.getChoices().get(0).getId());
		assertEquals("Frankfurt", o.getChoices().get(1).getLabel());
		assertEquals("Dallas", o.getChoices().get(2).getLabel());
		assertEquals("an unassigned account shows Direct", 0, o.getSelected());
	}

	@Test
	public void theAssignedProxyIsSelected()
	{
		assertEquals(2, ProxyChoice.forAccount(PROXIES, "p2").getSelected());
	}

	@Test
	public void aDeletedProxyIsShownAndSelectedNeverPassedOffAsDirect()
	{
		// Launching this account is refused, so showing "Direct" would be a lie about what happens.
		ProxyChoice.Options o = ProxyChoice.forAccount(PROXIES, "gone");

		assertEquals(4, o.getChoices().size());
		ProxyChoice missing = o.getChoices().get(1);
		assertEquals(ProxyChoice.Kind.MISSING, missing.getKind());
		assertEquals(1, o.getSelected());
		assertFalse("re-picking it is not a change", missing.differsFrom("gone"));
	}

	@Test
	public void aProxyWithNoNicknameIsLabelledByItsAddress()
	{
		ProxyEntry unnamed = proxy("p3", "  ", ProxyEntry.Status.UNKNOWN);
		ProxyChoice c = ProxyChoice.forAccount(Collections.singletonList(unnamed), null).getChoices().get(1);
		assertEquals(unnamed.getHost() + ":1080", c.getLabel());
	}

	@Test
	public void noProxiesStillLeavesDirect()
	{
		assertEquals(1, ProxyChoice.forAccount(null, null).getChoices().size());
		assertEquals(1, ProxyChoice.forAccount(new ArrayList<>(), null).getChoices().size());
	}

	@Test
	public void theDotFollowsTheLastTest()
	{
		List<ProxyChoice> c = ProxyChoice.forAccount(PROXIES, "gone").getChoices();
		assertNull("direct has no health to show", ProxyPicker.dotColor(c.get(0)));
		assertEquals(rift.launcher.ui.theme.RiftTheme.WARN, ProxyPicker.dotColor(c.get(1)));
		assertEquals(rift.launcher.ui.theme.RiftTheme.OK, ProxyPicker.dotColor(c.get(2)));
		assertEquals(rift.launcher.ui.theme.RiftTheme.ERROR, ProxyPicker.dotColor(c.get(3)));
	}

	// --- the card --------------------------------------------------------------------------------

	private final List<String> assigned = new ArrayList<>();

	private ClientCard card(String proxyId)
	{
		Account account = new Account("111", "Gallus", "s", 0L, proxyId);
		return new ClientCard(account, "1h", () -> { }, ProxyChoice.forAccount(PROXIES, proxyId), assigned::add);
	}

	@Test
	public void buildingACardAssignsNothing()
	{
		card("p1");
		card(null);
		card("gone");
		assertTrue(assigned.isEmpty());
	}

	@Test
	public void choosingAnotherProxyAssignsIt()
	{
		ProxyPicker picker = card(null).proxyPicker();
		picker.setSelectedIndex(2);
		assertEquals(Collections.singletonList("p2"), assigned);
	}

	@Test
	public void choosingDirectClearsTheAssignment()
	{
		ProxyPicker picker = card("p1").proxyPicker();
		picker.setSelectedIndex(0);
		assertEquals(1, assigned.size());
		assertNull(assigned.get(0));
	}

	@Test
	public void rePickingTheCurrentOptionWritesNothing()
	{
		// JComboBox fires an action for every pick, even of the option already shown.
		ProxyPicker picker = card("p1").proxyPicker();
		picker.setSelectedIndex(1);
		assertTrue(assigned.isEmpty());
	}

	@Test
	public void theDropdownLocksWhileTheClientIsStartingOrRunning()
	{
		ClientCard card = card("p1");
		assertTrue(card.proxyPicker().isEnabled());

		card.setStatus("Launching...");
		assertFalse(card.proxyPicker().isEnabled());
		card.setStatus("Playing");
		assertFalse(card.proxyPicker().isEnabled());
		card.setStatus("Playing (dev)");
		assertFalse(card.proxyPicker().isEnabled());

		card.setStatus("Ready");
		assertTrue(card.proxyPicker().isEnabled());
	}
}
