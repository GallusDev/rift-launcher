package rift.launcher.ui.components;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import rift.launcher.ui.theme.RiftTheme;

/**
 * Minimise / maximise / close, drawn in the app's own style for an undecorated window.
 *
 * <p>The glyphs are painted rather than typed as characters: the usual approach of using text like
 * "—" and "✕" renders at whatever weight the font feels like and rarely lines up optically. Drawing
 * them keeps all three the same stroke and the same visual size.
 *
 * <p>Close hovers red while the other two hover grey — the convention every desktop user already
 * reads, and worth keeping because this is the one control with an irreversible outcome.
 */
public class WindowControls extends JPanel
{
	private enum Kind
	{
		MINIMISE, MAXIMISE, CLOSE
	}

	public WindowControls(JFrame frame)
	{
		setOpaque(false);
		setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 0, 0));
		add(new ControlButton(Kind.MINIMISE, () -> frame.setState(Frame.ICONIFIED)));
		add(new ControlButton(Kind.MAXIMISE, () -> toggleMaximised(frame)));
		add(new ControlButton(Kind.CLOSE, () -> frame.dispatchEvent(
			new java.awt.event.WindowEvent(frame, java.awt.event.WindowEvent.WINDOW_CLOSING))));
	}

	/** Maximise or restore, matching what double-clicking the title area does. */
	public static void toggleMaximised(JFrame frame)
	{
		boolean maximised = (frame.getExtendedState() & Frame.MAXIMIZED_BOTH) == Frame.MAXIMIZED_BOTH;
		frame.setExtendedState(maximised ? Frame.NORMAL : Frame.MAXIMIZED_BOTH);
	}

	private static final class ControlButton extends JComponent
	{
		private static final int WIDTH = 46;
		private static final int HEIGHT = 34;

		private final Kind kind;
		private boolean hovered;

		ControlButton(Kind kind, Runnable onClick)
		{
			this.kind = kind;
			setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			addMouseListener(new MouseAdapter()
			{
				@Override
				public void mouseEntered(MouseEvent e)
				{
					hovered = true;
					repaint();
				}

				@Override
				public void mouseExited(MouseEvent e)
				{
					hovered = false;
					repaint();
				}

				@Override
				public void mouseClicked(MouseEvent e)
				{
					onClick.run();
				}
			});
		}

		@Override
		public Dimension getPreferredSize()
		{
			return new Dimension(WIDTH, HEIGHT);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

			int w = getWidth();
			int h = getHeight();

			if (hovered)
			{
				g.setColor(kind == Kind.CLOSE ? new Color(0xE81123) : RiftTheme.SURFACE_HOVER);
				g.fillRect(0, 0, w, h);
			}

			g.setColor(hovered ? RiftTheme.TEXT : RiftTheme.TEXT_MUTED);
			g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));

			int cx = w / 2;
			int cy = h / 2;
			int s = 5; // half the glyph size, so all three share one optical weight

			switch (kind)
			{
				case MINIMISE:
					g.drawLine(cx - s, cy, cx + s, cy);
					break;
				case MAXIMISE:
					g.drawRect(cx - s, cy - s, s * 2, s * 2);
					break;
				case CLOSE:
					g.drawLine(cx - s, cy - s, cx + s, cy + s);
					g.drawLine(cx + s, cy - s, cx - s, cy + s);
					break;
				default:
					break;
			}
			g.dispose();
		}
	}
}
