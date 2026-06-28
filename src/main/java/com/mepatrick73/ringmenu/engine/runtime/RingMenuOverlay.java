package com.mepatrick73.ringmenu.engine.runtime;

import com.mepatrick73.ringmenu.engine.model.RingEntry;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.util.List;

@Singleton
public class RingMenuOverlay extends Overlay
{
    static final int RING_RADIUS  = 135;
    static final int INNER_RADIUS = 46;
    private static final int CENTER_R = 42;
    static final int FULL_R = RING_RADIUS + 18;

    private static final Color BG          = new Color(20, 20, 20);
    private static final Color SLICE_HOT   = new Color(65, 38, 4);
    private static final Color DIVIDER     = new Color(58, 58, 58);
    private static final Color BORDER      = new Color(70, 70, 70);
    private static final Color TEXT_NORMAL = new Color(198, 198, 198);
    private static final Color TEXT_HOT    = Color.WHITE;
    private static final Color SHADOW      = new Color(0, 0, 0, 180);

    private final Client client;
    private final RingController ringController;
    private Point center = null;

    @Inject
    public RingMenuOverlay(Client client, RingController ringController)
    {
        this.client = client;
        this.ringController = ringController;
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPosition(OverlayPosition.DYNAMIC);
    }

    public void setCenter(Point absoluteCanvasPoint)
    {
        this.center = absoluteCanvasPoint;
        setPreferredLocation(new Point(
            absoluteCanvasPoint.x - FULL_R,
            absoluteCanvasPoint.y - FULL_R
        ));
    }

    public boolean isOutsideRing(int x, int y)
    {
        if (center == null) return true;
        int dx = x - center.x;
        int dy = y - center.y;
        return dx * dx + dy * dy > RING_RADIUS * RING_RADIUS;
    }

    public int getHighlightedIndex()
    {
        if (center == null) return -1;
        List<RingEntry> entries = ringController.currentEntries();
        if (entries.isEmpty()) return -1;

        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        int dx = mouse.getX() - center.x;
        int dy = mouse.getY() - center.y;
        if (dx * dx + dy * dy <= INNER_RADIUS * INNER_RADIUS) return -1;

        double mouseAngle = Math.atan2(dy, dx);
        int n = entries.size();
        double sliceSize = 2 * Math.PI / n;

        int best = 0;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < n; i++)
        {
            double entryAngle = sliceSize * i - Math.PI / 2;
            double diff = angleDiff(mouseAngle, entryAngle);
            if (Math.abs(diff) < bestDiff) { bestDiff = Math.abs(diff); best = i; }
        }
        return best;
    }

    private boolean isHoveringCenter()
    {
        if (center == null) return false;
        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        int dx = mouse.getX() - center.x;
        int dy = mouse.getY() - center.y;
        return dx * dx + dy * dy <= INNER_RADIUS * INNER_RADIUS;
    }

    private double angleDiff(double a, double b)
    {
        double d = a - b;
        while (d >  Math.PI) d -= 2 * Math.PI;
        while (d < -Math.PI) d += 2 * Math.PI;
        return d;
    }

    @Override
    public Dimension render(Graphics2D g)
    {
        if (!ringController.isOpen() || center == null) return null;

        List<RingEntry> entries = ringController.currentEntries();
        int n = entries.size();
        int lx = FULL_R, ly = FULL_R;

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        Composite origComposite = g.getComposite();
        Stroke    origStroke    = g.getStroke();

        net.runelite.api.Point mouse = client.getMouseCanvasPosition();
        boolean outside     = isOutsideRing(mouse.getX(), mouse.getY());
        boolean hoverCenter = isHoveringCenter();
        boolean canBack     = ringController.canGoBack();
        float   alpha       = outside ? 0.40f : 1.0f;

        // ── 1. Background disc ────────────────────────────────────────────
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.90f * alpha));
        g.setColor(BG);
        g.fillOval(lx - RING_RADIUS, ly - RING_RADIUS, RING_RADIUS * 2, RING_RADIUS * 2);

        if (n > 0)
        {
            int highlighted  = getHighlightedIndex();
            double sliceSize = 2 * Math.PI / n;
            double sliceDeg  = Math.toDegrees(sliceSize);

            // ── 2. Highlighted pie slice ──────────────────────────────────
            if (highlighted >= 0)
            {
                double cAngle = sliceSize * highlighted - Math.PI / 2;
                int startDeg  = (int)(-Math.toDegrees(cAngle) + sliceDeg / 2);
                int arcDeg    = -(int)sliceDeg;

                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f * alpha));
                g.setColor(SLICE_HOT);
                g.fillArc(lx - RING_RADIUS, ly - RING_RADIUS,
                    RING_RADIUS * 2, RING_RADIUS * 2, startDeg, arcDeg);
            }

            // ── 3. Center button fill (covers center of highlighted arc) ──
            Color centerFill = !canBack
                ? (hoverCenter ? new Color(170, 18, 18) : new Color(110, 12, 12))
                : (hoverCenter ? new Color(195, 96, 0)  : new Color(55, 55, 55));

            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f * alpha));
            g.setColor(centerFill);
            g.fillOval(lx - CENTER_R, ly - CENTER_R, CENTER_R * 2, CENTER_R * 2);

            // ── 4. Slice divider lines ────────────────────────────────────
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f * alpha));
            g.setColor(DIVIDER);
            g.setStroke(new BasicStroke(1.0f));
            for (int i = 0; i < n; i++)
            {
                double div = sliceSize * i - Math.PI / 2 - sliceSize / 2;
                int x1 = lx + (int)(INNER_RADIUS * Math.cos(div));
                int y1 = ly + (int)(INNER_RADIUS * Math.sin(div));
                int x2 = lx + (int)(RING_RADIUS  * Math.cos(div));
                int y2 = ly + (int)(RING_RADIUS   * Math.sin(div));
                g.drawLine(x1, y1, x2, y2);
            }

            // ── 5. Outer ring border ──────────────────────────────────────
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g.setColor(BORDER);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(lx - RING_RADIUS, ly - RING_RADIUS, RING_RADIUS * 2, RING_RADIUS * 2);

            // ── 6. Inner circle border ────────────────────────────────────
            Color innerBorder = !canBack
                ? (hoverCenter ? new Color(230, 60, 60)  : new Color(155, 35, 35))
                : (hoverCenter ? new Color(255, 168, 40) : new Color(95, 95, 95));

            g.setColor(innerBorder);
            g.setStroke(new BasicStroke(1.5f));
            g.drawOval(lx - INNER_RADIUS, ly - INNER_RADIUS, INNER_RADIUS * 2, INNER_RADIUS * 2);

            // Dashed outer ring when cursor is outside
            if (outside)
            {
                g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f));
                g.setColor(new Color(190, 190, 190));
                g.setStroke(new BasicStroke(1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    0f, new float[]{5f, 5f}, 0f));
                g.drawOval(lx - RING_RADIUS, ly - RING_RADIUS, RING_RADIUS * 2, RING_RADIUS * 2);
            }

            // ── 7. Slice text labels ──────────────────────────────────────
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            Font font = g.getFont().deriveFont(Font.PLAIN, 12.5f);
            g.setFont(font);
            FontMetrics fm = g.getFontMetrics(font);
            int midR = (INNER_RADIUS + RING_RADIUS) / 2;

            for (int i = 0; i < n; i++)
            {
                boolean lit = (i == highlighted);
                double angle = sliceSize * i - Math.PI / 2;
                int tx = lx + (int)(midR * Math.cos(angle));
                int ty = ly + (int)(midR * Math.sin(angle));

                String label = entries.get(i).getLabel();
                int tw = fm.stringWidth(label);
                int textX = tx - tw / 2;
                int textY = ty + fm.getAscent() / 2 - 1;

                g.setColor(SHADOW);
                g.drawString(label, textX + 1, textY + 1);
                g.setColor(lit ? TEXT_HOT : TEXT_NORMAL);
                g.drawString(label, textX, textY);
            }
        }

        // ── 8. Center button content ──────────────────────────────────────
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));

        if (!canBack)
        {
            Color xColor = hoverCenter ? Color.WHITE : new Color(220, 110, 110);
            g.setColor(xColor);
            g.setStroke(new BasicStroke(2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
            int off = CENTER_R / 3;
            g.drawLine(lx - off, ly - off, lx + off, ly + off);
            g.drawLine(lx + off, ly - off, lx - off, ly + off);
        }
        else
        {
            Color arrowColor = hoverCenter ? Color.WHITE : new Color(175, 175, 175);
            g.setColor(arrowColor);
            Font arrowFont = g.getFont().deriveFont(Font.BOLD, 26f);
            g.setFont(arrowFont);
            FontMetrics afm = g.getFontMetrics(arrowFont);
            String arrow = "‹";
            g.drawString(arrow, lx - afm.stringWidth(arrow) / 2, ly + afm.getAscent() / 2 - 2);
        }

        g.setComposite(origComposite);
        g.setStroke(origStroke);

        return new Dimension(FULL_R * 2, FULL_R * 2);
    }
}
