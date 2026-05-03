import javax.swing.border.AbstractBorder;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;

/**
 * RoundBorder.java
 * ────────────────
 * A custom Swing Border that draws a rounded rectangle outline.
 * Used to give text fields and panels a modern "pill" look.
 *
 * This is a helper class — college projects often don't need this,
 * but it makes the UI look significantly more polished.
 */
public class RoundBorder extends AbstractBorder {

    private final int   radius;
    private final Color color;
    private final int   thickness;

    public RoundBorder(int radius, Color color, int thickness) {
        this.radius    = radius;
        this.color     = color;
        this.thickness = thickness;
    }

    @Override
    public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
        Graphics2D g2 = (Graphics2D) g.create();
        // Anti-aliasing makes the rounded corners smooth
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(thickness));
        g2.draw(new RoundRectangle2D.Double(x + 0.5, y + 0.5,
                width - 1, height - 1, radius, radius));
        g2.dispose();
    }

    @Override
    public Insets getBorderInsets(Component c) {
        return new Insets(thickness + 2, thickness + 2, thickness + 2, thickness + 2);
    }

    @Override
    public Insets getBorderInsets(Component c, Insets insets) {
        insets.set(thickness + 2, thickness + 2, thickness + 2, thickness + 2);
        return insets;
    }
}
