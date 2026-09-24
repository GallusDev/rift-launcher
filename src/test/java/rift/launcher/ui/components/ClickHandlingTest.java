package rift.launcher.ui.components;

import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JComponent;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

/**
 * The nav rail and the window controls used to act in mouseClicked, which AWT only delivers when the
 * pointer has not moved between press and release -- so a quick click made while the hand was already
 * moving did nothing. These drive press and release directly, and never send a click event at all:
 * the handlers must not depend on one.
 */
public class ClickHandlingTest
{
	private static void press(JComponent c, int button, int x, int y)
	{
		int mask = button == MouseEvent.BUTTON1 ? InputEvent.BUTTON1_DOWN_MASK : InputEvent.BUTTON3_DOWN_MASK;
		MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_PRESSED, 0L, mask, x, y, 1, false, button);
		for (MouseListener l : c.getMouseListeners())
		{
			l.mousePressed(e);
		}
	}

	private static void release(JComponent c, int button, int x, int y)
	{
		MouseEvent e = new MouseEvent(c, MouseEvent.MOUSE_RELEASED, 0L, 0, x, y, 1, false, button);
		for (MouseListener l : c.getMouseListeners())
		{
			l.mouseReleased(e);
		}
	}

	@Test
	public void aNavItemSwitchesOnPressEvenIfTheReleaseDrifts()
	{
		AtomicInteger switched = new AtomicInteger();
		NavButton nav = new NavButton("Plugins", null, switched::incrementAndGet);
		nav.setSize(200, 48);

		press(nav, MouseEvent.BUTTON1, 20, 20);
		assertEquals("switches the moment the button goes down", 1, switched.get());

		// The input that used to be lost: released 30px away, and no MOUSE_CLICKED ever arrives.
		release(nav, MouseEvent.BUTTON1, 50, 44);
		assertEquals("and exactly once", 1, switched.get());
	}

	@Test
	public void aRightClickDoesNotSwitchPage()
	{
		AtomicInteger switched = new AtomicInteger();
		NavButton nav = new NavButton("Plugins", null, switched::incrementAndGet);
		nav.setSize(200, 48);

		press(nav, MouseEvent.BUTTON3, 20, 20);
		release(nav, MouseEvent.BUTTON3, 20, 20);

		assertEquals(0, switched.get());
	}

	private static WindowControls.ControlButton control(AtomicInteger fired)
	{
		WindowControls.ControlButton b = new WindowControls.ControlButton(WindowControls.Kind.CLOSE,
			fired::incrementAndGet);
		b.setSize(46, 34);
		return b;
	}

	@Test
	public void aWindowControlFiresOnReleaseOverItEvenAfterMovingWithinIt()
	{
		AtomicInteger fired = new AtomicInteger();
		WindowControls.ControlButton close = control(fired);

		press(close, MouseEvent.BUTTON1, 5, 5);
		assertEquals("pressing Close must not close the window by itself", 0, fired.get());

		release(close, MouseEvent.BUTTON1, 30, 22);
		assertEquals(1, fired.get());
	}

	@Test
	public void slidingOffAWindowControlBeforeLettingGoCancels()
	{
		AtomicInteger fired = new AtomicInteger();
		WindowControls.ControlButton close = control(fired);

		press(close, MouseEvent.BUTTON1, 5, 5);
		// Swing hands the release to the pressed component even out here; the bounds check is what
		// turns it into a cancel, as with a native caption button.
		release(close, MouseEvent.BUTTON1, 90, 20);

		assertEquals(0, fired.get());
	}

	@Test
	public void aWindowControlIgnoresOtherButtonsAndStrayReleases()
	{
		AtomicInteger fired = new AtomicInteger();
		WindowControls.ControlButton close = control(fired);

		press(close, MouseEvent.BUTTON3, 5, 5);
		release(close, MouseEvent.BUTTON3, 5, 5);
		assertEquals("right-click", 0, fired.get());

		release(close, MouseEvent.BUTTON1, 5, 5);
		assertEquals("a release with no press behind it", 0, fired.get());

		press(close, MouseEvent.BUTTON1, 5, 5);
		release(close, MouseEvent.BUTTON1, 5, 5);
		release(close, MouseEvent.BUTTON1, 5, 5);
		assertEquals("one press is one click, however many releases follow", 1, fired.get());
	}
}
