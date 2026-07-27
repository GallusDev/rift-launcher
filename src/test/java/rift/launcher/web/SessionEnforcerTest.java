package rift.launcher.web;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class SessionEnforcerTest
{
	@Test
	public void unlimitedWhenMaxIsNull()
	{
		assertTrue(SessionEnforcer.canLaunch(null, 0));
		assertTrue(SessionEnforcer.canLaunch(null, 99));
	}

	@Test
	public void freeTierAllowsUpToLimit()
	{
		assertTrue("0 running, limit 1", SessionEnforcer.canLaunch(1, 0));
		assertFalse("1 running, limit 1", SessionEnforcer.canLaunch(1, 1));
		assertFalse("over limit", SessionEnforcer.canLaunch(1, 2));
	}

	@Test
	public void higherLimitAllowsMore()
	{
		assertTrue(SessionEnforcer.canLaunch(3, 2));
		assertFalse(SessionEnforcer.canLaunch(3, 3));
	}
}
