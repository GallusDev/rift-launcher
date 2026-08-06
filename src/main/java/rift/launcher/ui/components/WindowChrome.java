package rift.launcher.ui.components;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/**
 * Restores the window behaviour an undecorated frame gives up.
 *
 * <p>Removing the title bar also removes everything the OS attached to it: moving the window,
 * resizing from any edge, and double-click-to-maximise. Without these a frameless window is pretty
 * and unusable, so each is put back here.
 *
 * <p>One thing is genuinely lost and cannot be recreated in pure Swing: <b>Windows Aero Snap</b>
 * (dragging to a screen edge to tile). That is handled by the OS for decorated windows only.
 */
public final class WindowChrome
{
	/** How close to an edge counts as a resize grab. */
	private static final int EDGE = 6;

	private WindowChrome()
	{
	}

	/** Makes {@code handle} drag the window, and double-click toggle maximised. */
	public static void makeDraggable(JFrame frame, Component handle)
	{
		DragHandler drag = new DragHandler(frame);
		handle.addMouseListener(drag);
		handle.addMouseMotionListener(drag);
	}

	/** Lets the window be resized from any edge or corner, as a decorated window could. */
	public static void makeResizable(JFrame frame)
	{
		ResizeHandler resize = new ResizeHandler(frame);
		frame.getRootPane().addMouseListener(resize);
		frame.getRootPane().addMouseMotionListener(resize);
	}

	/**
	 * Rounds the window's corners by clipping it to a shape.
	 *
	 * <p>Per-pixel translucency would give smoother, anti-aliased corners, but it requires the root
	 * panel to be non-opaque -- and that breaks Swing's repaint optimisation, leaving components
	 * blank until something forces a repaint. Slightly harder corners are a far smaller cost than a
	 * window that only draws itself when hovered.
	 */
	public static void applyRoundedCorners(JFrame frame, int radius)
	{
		applyShape(frame, radius);
		frame.addComponentListener(new java.awt.event.ComponentAdapter()
		{
			@Override
			public void componentResized(java.awt.event.ComponentEvent e)
			{
				// The shape is in pixels, so it has to be recut on every resize -- including the
				// resize that happens when the window is maximised or restored.
				applyShape(frame, radius);
			}
		});
	}

	private static void applyShape(JFrame frame, int radius)
	{
		try
		{
			// A maximised window fills the screen; rounding it would leave gaps at the edges.
			boolean maximised =
				(frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
			frame.setShape(maximised ? null
				: new java.awt.geom.RoundRectangle2D.Double(0, 0,
					frame.getWidth(), frame.getHeight(), radius, radius));
		}
		catch (Exception ignored)
		{
			// Shaped windows unsupported here; square corners are cosmetic, not a failure.
		}
	}

	private static final class DragHandler extends MouseAdapter
	{
		private final JFrame frame;
		/** Cursor position relative to the WINDOW's top-left, captured when the drag starts. */
		private Point grab;

		DragHandler(JFrame frame)
		{
			this.frame = frame;
		}

		@Override
		public void mousePressed(MouseEvent e)
		{
			// Measured against the window, not the component the listener is attached to. Using the
			// component-relative point makes the window jump by that component's offset on the first
			// drag event -- the title strip sits to the right of the sidebar, so it lurched sideways.
			grab = new Point(e.getXOnScreen() - frame.getX(), e.getYOnScreen() - frame.getY());
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			grab = null;
		}

		@Override
		public void mouseClicked(MouseEvent e)
		{
			if (e.getClickCount() == 2)
			{
				WindowControls.toggleMaximised(frame);
			}
		}

		@Override
		public void mouseDragged(MouseEvent e)
		{
			if (grab == null)
			{
				return;
			}
			// Dragging a maximised window restores it and continues the drag, as every desktop does.
			// The grab point is rescaled so the cursor keeps the same relative position along the
			// restored (narrower) title area instead of the window snapping under the pointer.
			if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH)
			{
				int maximisedWidth = frame.getWidth();
				frame.setExtendedState(Frame.NORMAL);
				double ratio = maximisedWidth == 0 ? 0.5 : grab.x / (double) maximisedWidth;
				grab = new Point((int) (frame.getWidth() * ratio), grab.y);
			}
			frame.setLocation(e.getXOnScreen() - grab.x, e.getYOnScreen() - grab.y);
		}
	}

	/** Edge and corner resizing, with the cursor reflecting which edge is under the pointer. */
	private static final class ResizeHandler extends MouseAdapter
	{
		private final JFrame frame;
		private int edge;
		private Rectangle startBounds;
		private Point startScreen;

		ResizeHandler(JFrame frame)
		{
			this.frame = frame;
		}

		@Override
		public void mouseMoved(MouseEvent e)
		{
			frame.getRootPane().setCursor(cursorFor(edgeAt(e.getPoint())));
		}

		@Override
		public void mouseExited(MouseEvent e)
		{
			frame.getRootPane().setCursor(Cursor.getDefaultCursor());
		}

		@Override
		public void mousePressed(MouseEvent e)
		{
			edge = edgeAt(e.getPoint());
			startBounds = frame.getBounds();
			startScreen = e.getLocationOnScreen();
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			edge = 0;
		}

		@Override
		public void mouseDragged(MouseEvent e)
		{
			if (edge == 0 || startBounds == null)
			{
				return;
			}
			Point now = e.getLocationOnScreen();
			int dx = now.x - startScreen.x;
			int dy = now.y - startScreen.y;

			Rectangle b = new Rectangle(startBounds);
			java.awt.Dimension min = frame.getMinimumSize();

			if ((edge & SwingConstantsEdge.WEST) != 0)
			{
				int width = Math.max(min.width, startBounds.width - dx);
				b.x = startBounds.x + (startBounds.width - width);
				b.width = width;
			}
			if ((edge & SwingConstantsEdge.EAST) != 0)
			{
				b.width = Math.max(min.width, startBounds.width + dx);
			}
			if ((edge & SwingConstantsEdge.NORTH) != 0)
			{
				int height = Math.max(min.height, startBounds.height - dy);
				b.y = startBounds.y + (startBounds.height - height);
				b.height = height;
			}
			if ((edge & SwingConstantsEdge.SOUTH) != 0)
			{
				b.height = Math.max(min.height, startBounds.height + dy);
			}
			frame.setBounds(b);
			frame.revalidate();
		}

		private int edgeAt(Point p)
		{
			// A maximised window has no edges to drag; resizing it would be meaningless.
			if ((frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH)
			{
				return 0;
			}
			int w = frame.getRootPane().getWidth();
			int h = frame.getRootPane().getHeight();
			int result = 0;
			if (p.x <= EDGE)
			{
				result |= SwingConstantsEdge.WEST;
			}
			if (p.x >= w - EDGE)
			{
				result |= SwingConstantsEdge.EAST;
			}
			if (p.y <= EDGE)
			{
				result |= SwingConstantsEdge.NORTH;
			}
			if (p.y >= h - EDGE)
			{
				result |= SwingConstantsEdge.SOUTH;
			}
			return result;
		}

		private static Cursor cursorFor(int edge)
		{
			switch (edge)
			{
				case SwingConstantsEdge.NORTH:
					return Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR);
				case SwingConstantsEdge.SOUTH:
					return Cursor.getPredefinedCursor(Cursor.S_RESIZE_CURSOR);
				case SwingConstantsEdge.WEST:
					return Cursor.getPredefinedCursor(Cursor.W_RESIZE_CURSOR);
				case SwingConstantsEdge.EAST:
					return Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR);
				case SwingConstantsEdge.NORTH | SwingConstantsEdge.WEST:
					return Cursor.getPredefinedCursor(Cursor.NW_RESIZE_CURSOR);
				case SwingConstantsEdge.NORTH | SwingConstantsEdge.EAST:
					return Cursor.getPredefinedCursor(Cursor.NE_RESIZE_CURSOR);
				case SwingConstantsEdge.SOUTH | SwingConstantsEdge.WEST:
					return Cursor.getPredefinedCursor(Cursor.SW_RESIZE_CURSOR);
				case SwingConstantsEdge.SOUTH | SwingConstantsEdge.EAST:
					return Cursor.getPredefinedCursor(Cursor.SE_RESIZE_CURSOR);
				default:
					return Cursor.getDefaultCursor();
			}
		}
	}

	/** Edge bit flags, named rather than raw numbers so the combinations above stay readable. */
	private static final class SwingConstantsEdge
	{
		static final int NORTH = 1;
		static final int SOUTH = 2;
		static final int WEST = 4;
		static final int EAST = 8;

		private SwingConstantsEdge()
		{
		}
	}
}
