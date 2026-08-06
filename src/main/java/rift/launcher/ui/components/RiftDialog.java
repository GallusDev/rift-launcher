package rift.launcher.ui.components;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import rift.launcher.ui.theme.RiftTheme;

/**
 * A modal dialog painted in the launcher's own style.
 *
 * <p>{@code JOptionPane} cannot be themed far enough: its chrome, buttons and background come from
 * the look-and-feel, so a popup always arrives looking like a different application. This is an
 * undecorated window that paints its own surface, title row and buttons — so every dialog in the app
 * matches, and new ones inherit that for free rather than each being styled by hand.
 *
 * <p>Keyboard behaviour is kept: <b>Escape</b> cancels, matching what the platform dialog did.
 */
public class RiftDialog extends JDialog
{
	private final JPanel body = new JPanel(new BorderLayout());
	private final JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
	private boolean confirmed;

	public RiftDialog(Component parent, String title, String subtitle)
	{
		super(parent == null ? null : SwingUtilities.getWindowAncestor(parent),
			title, ModalityType.APPLICATION_MODAL);
		setUndecorated(true);
		setBackground(new java.awt.Color(0, 0, 0, 0));

		JPanel surface = new Surface();
		surface.setLayout(new BorderLayout());
		surface.setBorder(BorderFactory.createEmptyBorder(20, 22, 18, 22));

		surface.add(buildHeader(title, subtitle), BorderLayout.NORTH);

		body.setOpaque(false);
		surface.add(body, BorderLayout.CENTER);

		actions.setOpaque(false);
		actions.setBorder(BorderFactory.createEmptyBorder(18, 0, 0, 0));
		surface.add(actions, BorderLayout.SOUTH);

		setContentPane(surface);

		// Escape cancels, as it would in the platform dialog it replaces.
		getRootPane().registerKeyboardAction(e -> cancel(),
			KeyStroke.getKeyStroke(KeyEvent.VK_ESCAPE, 0),
			JComponent.WHEN_IN_FOCUSED_WINDOW);
	}

	private JPanel buildHeader(String title, String subtitle)
	{
		JPanel header = new JPanel(new BorderLayout());
		header.setOpaque(false);
		header.setBorder(BorderFactory.createEmptyBorder(0, 0, 16, 0));

		JPanel text = new JPanel();
		text.setLayout(new javax.swing.BoxLayout(text, javax.swing.BoxLayout.Y_AXIS));
		text.setOpaque(false);

		JLabel heading = new JLabel(title);
		heading.setFont(RiftTheme.bold(17));
		heading.setForeground(RiftTheme.TEXT);
		heading.setAlignmentX(LEFT_ALIGNMENT);
		text.add(heading);

		if (subtitle != null && !subtitle.isEmpty())
		{
			JLabel sub = new JLabel(subtitle);
			sub.setFont(RiftTheme.regular(12));
			sub.setForeground(RiftTheme.TEXT_FAINT);
			sub.setAlignmentX(LEFT_ALIGNMENT);
			text.add(javax.swing.Box.createVerticalStrut(4));
			text.add(sub);
		}
		header.add(text, BorderLayout.WEST);

		// Undecorated windows cannot be moved by the OS, so the header doubles as a drag handle --
		// a dialog that cannot be moved off whatever it is covering is genuinely annoying.
		DragSupport drag = new DragSupport(this);
		header.addMouseListener(drag);
		header.addMouseMotionListener(drag);
		header.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
		return header;
	}

	/** Puts the dialog's content in place. */
	public void setBody(JComponent component)
	{
		body.removeAll();
		body.add(component, BorderLayout.CENTER);
	}

	/** Adds a footer button; {@code primary} gets the filled treatment. */
	public PrimaryButton addAction(String text, boolean primary, Runnable onClick)
	{
		PrimaryButton button = new PrimaryButton(text, null, primary);
		button.addActionListener(e -> onClick.run());
		actions.add(button);
		return button;
	}

	/** Standard Cancel / confirm pair, in the order Windows users expect. */
	public void addCancelAndConfirm(String confirmText)
	{
		addAction("Cancel", false, this::cancel);
		addAction(confirmText, true, this::confirm);
	}

	public void confirm()
	{
		confirmed = true;
		dispose();
	}

	public void cancel()
	{
		confirmed = false;
		dispose();
	}

	public boolean isConfirmed()
	{
		return confirmed;
	}

	/** Sizes to content, centres on the parent, and blocks until closed. */
	public boolean showDialog()
	{
		pack();
		setLocationRelativeTo(getOwner());
		setVisible(true);
		return confirmed;
	}

	/** The rounded panel the dialog is drawn on; the window itself is transparent behind it. */
	private static final class Surface extends JPanel
	{
		Surface()
		{
			setOpaque(false);
		}

		@Override
		protected void paintComponent(Graphics graphics)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			int arc = RiftTheme.RADIUS + 4;
			g.setColor(RiftTheme.SURFACE_RAISED);
			g.fillRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
			g.setColor(RiftTheme.BORDER_ACCENT);
			g.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
			g.dispose();
			super.paintComponent(graphics);
		}
	}

	/** Lets an undecorated window be dragged by whichever component this is attached to. */
	private static final class DragSupport extends MouseAdapter
	{
		private final Window window;
		private Point origin;

		DragSupport(Window window)
		{
			this.window = window;
		}

		@Override
		public void mousePressed(MouseEvent e)
		{
			// Relative to the window, not to the header -- the header is inset by the dialog's
			// padding, and a component-relative offset makes the window jump by that amount.
			origin = new Point(e.getXOnScreen() - window.getX(), e.getYOnScreen() - window.getY());
		}

		@Override
		public void mouseReleased(MouseEvent e)
		{
			origin = null;
		}

		@Override
		public void mouseDragged(MouseEvent e)
		{
			if (origin != null)
			{
				window.setLocation(e.getXOnScreen() - origin.x, e.getYOnScreen() - origin.y);
			}
		}
	}
}
