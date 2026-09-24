package rift.launcher.ui.theme;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import javax.swing.Icon;

/**
 * Small interface icons drawn as vectors rather than shipped as images.
 *
 * <p>The navigation and avatar icons are not here: they are SVG art, rendered by {@link SvgIcons}.
 * What remains are the incidental marks that have no art of their own, such as the Launch button's.
 *
 * <p>Drawn because these need to recolour with state (idle, hover, selected) and stay crisp on any
 * display scaling. A PNG would need one file per colour per size; a path needs neither, and the
 * launcher keeps working before any art arrives.
 */
public final class RiftIcons
{
	public enum Kind
	{
		ROCKET, PLUS, DOTS, CLOCK, PLAY, CHECK
	}

	private RiftIcons()
	{
	}

	public static Icon of(Kind kind, int size, Color color)
	{
		return new VectorIcon(kind, size, color);
	}

	private static final class VectorIcon implements Icon
	{
		private final Kind kind;
		private final int size;
		private final Color color;

		VectorIcon(Kind kind, int size, Color color)
		{
			this.kind = kind;
			this.size = size;
			this.color = color;
		}

		@Override
		public int getIconWidth()
		{
			return size;
		}

		@Override
		public int getIconHeight()
		{
			return size;
		}

		@Override
		public void paintIcon(Component c, Graphics graphics, int x, int y)
		{
			Graphics2D g = (Graphics2D) graphics.create();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
			g.translate(x, y);
			// Paths are authored on a 24x24 grid and scaled, so one definition serves every size.
			double s = size / 24.0;
			g.scale(s, s);
			g.setColor(color);
			g.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			paint(g);
			g.dispose();
		}

		private void paint(Graphics2D g)
		{
			switch (kind)
			{
				case ROCKET:
				{
					GeneralPath p = new GeneralPath();
					p.moveTo(12, 3);
					p.curveTo(16, 7, 17, 12, 15, 16);
					p.lineTo(9, 16);
					p.curveTo(7, 12, 8, 7, 12, 3);
					p.closePath();
					g.draw(p);
					g.draw(new Ellipse2D.Double(10.5, 8, 3, 3));
					g.draw(new java.awt.geom.Line2D.Double(10, 18, 9, 21));
					g.draw(new java.awt.geom.Line2D.Double(14, 18, 15, 21));
					break;
				}
				case PLUS:
					g.draw(new java.awt.geom.Line2D.Double(12, 5, 12, 19));
					g.draw(new java.awt.geom.Line2D.Double(5, 12, 19, 12));
					break;
				case DOTS:
					for (int i = 0; i < 3; i++)
					{
						g.fill(new Ellipse2D.Double(10.7, 5 + i * 6.0, 2.6, 2.6));
					}
					break;
				case CLOCK:
					g.draw(new Ellipse2D.Double(3.5, 3.5, 17, 17));
					g.draw(new java.awt.geom.Line2D.Double(12, 7.5, 12, 12));
					g.draw(new java.awt.geom.Line2D.Double(12, 12, 15.5, 14));
					break;
				case PLAY:
				{
					GeneralPath p = new GeneralPath(Path2D.WIND_NON_ZERO);
					p.moveTo(8, 5);
					p.lineTo(19, 12);
					p.lineTo(8, 19);
					p.closePath();
					g.fill(p);
					break;
				}
				case CHECK:
					g.draw(new java.awt.geom.Line2D.Double(5, 12.5, 10, 17.5));
					g.draw(new java.awt.geom.Line2D.Double(10, 17.5, 19, 7));
					break;
				default:
					break;
			}
		}
	}
}
