package com.mepatrick73.ringmenu.engine.runtime;

import com.mepatrick73.ringmenu.engine.model.RingEntry;
import net.runelite.api.Client;
import net.runelite.client.ui.FontManager;
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

	// ── Static color palette ──────────────────────────────────────────────

	private static final Color BG          = new Color(20, 20, 20);
	private static final Color SLICE_HOT   = new Color(65, 38, 4);
	private static final Color DIVIDER     = new Color(58, 58, 58);
	private static final Color BORDER      = new Color(70, 70, 70);
	private static final Color TEXT_NORMAL = new Color(198, 198, 198);
	private static final Color TEXT_HOT    = Color.WHITE;
	private static final Color SHADOW      = new Color(0, 0, 0, 180);

	// Center fill: four states (canBack × hoverCenter)
	private static final Color CENTER_CLOSE_HOT  = new Color(170, 18, 18);
	private static final Color CENTER_CLOSE_COLD = new Color(110, 12, 12);
	private static final Color CENTER_BACK_HOT   = new Color(195, 96, 0);
	private static final Color CENTER_BACK_COLD  = new Color(55, 55, 55);

	// Inner circle border: four states (canBack × hoverCenter)
	private static final Color INNER_CLOSE_HOT  = new Color(230, 60, 60);
	private static final Color INNER_CLOSE_COLD = new Color(155, 35, 35);
	private static final Color INNER_BACK_HOT   = new Color(255, 168, 40);
	private static final Color INNER_BACK_COLD  = new Color(95, 95, 95);

	// X / arrow button colors (two states each: hoverCenter)
	private static final Color X_HOT      = Color.WHITE;
	private static final Color X_COLD     = new Color(220, 110, 110);
	private static final Color ARROW_HOT  = Color.WHITE;
	private static final Color ARROW_COLD = new Color(175, 175, 175);

	// Dashed ring shown when cursor is outside
	private static final Color DASHED_COLOR = new Color(190, 190, 190);

	// ── Static strokes ────────────────────────────────────────────────────

	private static final Stroke STROKE_THIN   = new BasicStroke(1.0f);
	private static final Stroke STROKE_BORDER = new BasicStroke(1.5f);
	private static final Stroke STROKE_DASHED = new BasicStroke(
		1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[]{5f, 5f}, 0f);
	private static final Stroke STROKE_X      = new BasicStroke(
		2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

	// ── Overlay bounding box ──────────────────────────────────────────────

	private static final Dimension OVERLAY_SIZE = new Dimension(FULL_R * 2, FULL_R * 2);

	// ── Alpha composites — two sets: near (cursor inside) and far (cursor outside) ──

	private static final float ALPHA_FAR = 0.40f;

	private static final AlphaComposite AC_BG_NEAR     = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.90f);
	private static final AlphaComposite AC_SLICE_NEAR  = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f);
	private static final AlphaComposite AC_CENTER_NEAR = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f);
	private static final AlphaComposite AC_DIV_NEAR    = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f);
	private static final AlphaComposite AC_FULL_NEAR   = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.00f);

	private static final AlphaComposite AC_BG_FAR      = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.90f * ALPHA_FAR);
	private static final AlphaComposite AC_SLICE_FAR   = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.88f * ALPHA_FAR);
	private static final AlphaComposite AC_CENTER_FAR  = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.95f * ALPHA_FAR);
	private static final AlphaComposite AC_DIV_FAR     = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.80f * ALPHA_FAR);
	private static final AlphaComposite AC_FULL_FAR    = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ALPHA_FAR);

	private static final AlphaComposite AC_DASHED      = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.55f);

	// ── Instance fields ───────────────────────────────────────────────────

	private final Client client;
	private final RingController ringController;

	// Written by setCenter() (EDT); read by render() (client thread). Volatile for visibility.
	private volatile Point center = null;

	private final Font labelFont;
	private final Font arrowFont;

	// Per-slice geometry cache — recomputed only when the number of slices changes.
	// Eliminates sin/cos calls for static geometry on every frame.
	private int      cachedN     = -1;
	private double   sliceSize;        // radians per slice
	private int      sliceDeg;         // degrees per slice (for fillArc)
	private double[] sliceAngles;      // center angle of each slice
	private int[]    divDx1, divDy1;   // divider line inner endpoint offsets from center
	private int[]    divDx2, divDy2;   // divider line outer endpoint offsets from center
	private int[]    lblDx,  lblDy;    // label center offsets from center

	@Inject
	public RingMenuOverlay(Client client, RingController ringController)
	{
		this.client = client;
		this.ringController = ringController;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
		labelFont = FontManager.getRunescapeFont().deriveFont(17f);
		arrowFont = FontManager.getRunescapeBoldFont().deriveFont(26f);
	}

	private void rebuildSliceCache(int n)
	{
		if (n == cachedN) return;
		cachedN    = n;
		sliceSize  = 2 * Math.PI / n;
		sliceDeg   = (int) Math.toDegrees(sliceSize);
		int midR   = (INNER_RADIUS + RING_RADIUS) / 2;
		sliceAngles = new double[n];
		divDx1 = new int[n]; divDy1 = new int[n];
		divDx2 = new int[n]; divDy2 = new int[n];
		lblDx  = new int[n]; lblDy  = new int[n];
		for (int i = 0; i < n; i++)
		{
			sliceAngles[i] = sliceSize * i - Math.PI / 2;
			double div     = sliceAngles[i] - sliceSize / 2;
			divDx1[i] = (int)(INNER_RADIUS * Math.cos(div));
			divDy1[i] = (int)(INNER_RADIUS * Math.sin(div));
			divDx2[i] = (int)(RING_RADIUS  * Math.cos(div));
			divDy2[i] = (int)(RING_RADIUS  * Math.sin(div));
			lblDx[i]  = (int)(midR         * Math.cos(sliceAngles[i]));
			lblDy[i]  = (int)(midR         * Math.sin(sliceAngles[i]));
		}
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
		Point c = center;
		if (c == null) return true;
		int dx = x - c.x;
		int dy = y - c.y;
		return dx * dx + dy * dy > RING_RADIUS * RING_RADIUS;
	}

	public int getHighlightedIndex()
	{
		Point c = center;
		if (c == null) return -1;
		List<RingEntry> entries = ringController.currentEntries();
		if (entries.isEmpty()) return -1;

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		int dx = mouse.getX() - c.x;
		int dy = mouse.getY() - c.y;
		if (dx * dx + dy * dy <= INNER_RADIUS * INNER_RADIUS) return -1;

		rebuildSliceCache(entries.size());
		return bestSliceIndex(dx, dy);
	}

	private int bestSliceIndex(int dx, int dy)
	{
		double mouseAngle = Math.atan2(dy, dx);
		int    best       = 0;
		double bestDiff   = Double.MAX_VALUE;
		for (int i = 0; i < cachedN; i++)
		{
			double diff = Math.abs(angleDiff(mouseAngle, sliceAngles[i]));
			if (diff < bestDiff)
			{
				bestDiff = diff;
				best = i;
			}
		}
		return best;
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
		Point c = center;
		if (!ringController.isOpen() || c == null) return null;

		List<RingEntry> entries = ringController.currentEntries();
		int n  = entries.size();
		int lx = FULL_R, ly = FULL_R;
		if (n > 0) rebuildSliceCache(n);

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Composite origComposite = g.getComposite();
		Stroke    origStroke    = g.getStroke();

		// Read mouse position once for the entire frame.
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		int     mdx         = mouse.getX() - c.x;
		int     mdy         = mouse.getY() - c.y;
		int     mDistSq     = mdx * mdx + mdy * mdy;
		boolean outside     = mDistSq > RING_RADIUS * RING_RADIUS;
		boolean hoverCenter = mDistSq <= INNER_RADIUS * INNER_RADIUS;
		boolean canBack     = ringController.canGoBack();

		// Select the pre-allocated composite set for this frame (near vs far).
		AlphaComposite acBg     = outside ? AC_BG_FAR     : AC_BG_NEAR;
		AlphaComposite acSlice  = outside ? AC_SLICE_FAR  : AC_SLICE_NEAR;
		AlphaComposite acCenter = outside ? AC_CENTER_FAR : AC_CENTER_NEAR;
		AlphaComposite acDiv    = outside ? AC_DIV_FAR    : AC_DIV_NEAR;
		AlphaComposite acFull   = outside ? AC_FULL_FAR   : AC_FULL_NEAR;

		// ── 1. Background disc ────────────────────────────────────────────
		g.setComposite(acBg);
		g.setColor(BG);
		g.fillOval(lx - RING_RADIUS, ly - RING_RADIUS, RING_RADIUS * 2, RING_RADIUS * 2);

		if (n > 0)
		{
			int highlighted = hoverCenter ? -1 : bestSliceIndex(mdx, mdy);

			// ── 2. Highlighted pie slice ──────────────────────────────────
			if (highlighted >= 0)
			{
				int startDeg = (int)(-Math.toDegrees(sliceAngles[highlighted]) + sliceDeg / 2);
				g.setComposite(acSlice);
				g.setColor(SLICE_HOT);
				g.fillArc(lx - RING_RADIUS, ly - RING_RADIUS,
					RING_RADIUS * 2, RING_RADIUS * 2, startDeg, -sliceDeg);
			}

			// ── 3. Center button fill ─────────────────────────────────────
			g.setComposite(acCenter);
			g.setColor(!canBack
				? (hoverCenter ? CENTER_CLOSE_HOT : CENTER_CLOSE_COLD)
				: (hoverCenter ? CENTER_BACK_HOT  : CENTER_BACK_COLD));
			g.fillOval(lx - CENTER_R, ly - CENTER_R, CENTER_R * 2, CENTER_R * 2);

			// ── 4. Slice divider lines (precomputed offsets) ──────────────
			g.setComposite(acDiv);
			g.setColor(DIVIDER);
			g.setStroke(STROKE_THIN);
			for (int i = 0; i < n; i++)
			{
				g.drawLine(lx + divDx1[i], ly + divDy1[i], lx + divDx2[i], ly + divDy2[i]);
			}

			// ── 5. Outer ring border ──────────────────────────────────────
			g.setComposite(acFull);
			g.setColor(BORDER);
			g.setStroke(STROKE_BORDER);
			g.drawOval(lx - RING_RADIUS, ly - RING_RADIUS, RING_RADIUS * 2, RING_RADIUS * 2);

			// ── 6. Inner circle border ────────────────────────────────────
			g.setColor(!canBack
				? (hoverCenter ? INNER_CLOSE_HOT : INNER_CLOSE_COLD)
				: (hoverCenter ? INNER_BACK_HOT  : INNER_BACK_COLD));
			g.drawOval(lx - INNER_RADIUS, ly - INNER_RADIUS, INNER_RADIUS * 2, INNER_RADIUS * 2);

			// Dashed outer ring when cursor is outside
			if (outside)
			{
				g.setComposite(AC_DASHED);
				g.setColor(DASHED_COLOR);
				g.setStroke(STROKE_DASHED);
				g.drawOval(lx - RING_RADIUS, ly - RING_RADIUS, RING_RADIUS * 2, RING_RADIUS * 2);
			}

			// ── 7. Slice text labels (precomputed center offsets) ─────────
			g.setComposite(acFull);
			g.setFont(labelFont);
			FontMetrics fm = g.getFontMetrics();

			for (int i = 0; i < n; i++)
			{
				int tx    = lx + lblDx[i];
				int ty    = ly + lblDy[i];
				String label = entries.get(i).getLabel();
				int tw    = fm.stringWidth(label);
				int textX = tx - tw / 2;
				int textY = ty + fm.getAscent() / 2 - 1;

				g.setColor(SHADOW);
				g.drawString(label, textX + 1, textY + 1);
				g.setColor(i == highlighted ? TEXT_HOT : TEXT_NORMAL);
				g.drawString(label, textX, textY);
			}
		}

		// ── 8. Center button content ──────────────────────────────────────
		g.setComposite(acFull);

		if (!canBack)
		{
			g.setColor(hoverCenter ? X_HOT : X_COLD);
			g.setStroke(STROKE_X);
			int off = CENTER_R / 3;
			g.drawLine(lx - off, ly - off, lx + off, ly + off);
			g.drawLine(lx + off, ly - off, lx - off, ly + off);
		}
		else
		{
			g.setColor(hoverCenter ? ARROW_HOT : ARROW_COLD);
			g.setFont(arrowFont);
			FontMetrics afm = g.getFontMetrics();
			String arrow = "‹";
			g.drawString(arrow, lx - afm.stringWidth(arrow) / 2, ly + afm.getAscent() / 2 - 2);
		}

		g.setComposite(origComposite);
		g.setStroke(origStroke);

		return OVERLAY_SIZE;
	}
}
