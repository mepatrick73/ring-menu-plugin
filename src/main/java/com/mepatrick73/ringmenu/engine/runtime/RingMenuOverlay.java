package com.mepatrick73.ringmenu.engine.runtime;

import com.mepatrick73.ringmenu.RingFontType;
import com.mepatrick73.ringmenu.RingMenuConfig;
import com.mepatrick73.ringmenu.data.TextOrientation;
import com.mepatrick73.ringmenu.engine.model.RingEntry;
import com.mepatrick73.ringmenu.engine.model.RingNode;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.tooltip.Tooltip;
import net.runelite.client.ui.overlay.tooltip.TooltipManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.List;

@Singleton
public class RingMenuOverlay extends Overlay
{
	// Reference geometry at the default ring size. The live radii are instance fields derived from
	// config.ringRadius() in refreshGeometry(); the inner/center circles keep these proportions.
	private static final int REF_RING_RADIUS  = 135;
	private static final int REF_INNER_RADIUS = 46;
	private static final int REF_CENTER_R     = 42;
	// Padding around the ring for the overlay bounding box (glow/border room).
	private static final int RIM = 18;

	// Radial labels run along the radius, inset from both circle borders.
	private static final int RADIAL_PAD = 8;

	// Shrink-to-fit never goes below this size; past it, labels truncate instead.
	private static final int MIN_FONT_SIZE = 9;

	// ── Static color palette ──────────────────────────────────────────────

	private static final Color BG          = new Color(20, 20, 20);
	private static final Color SLICE_HOT   = new Color(65, 38, 4);
	private static final Color DIVIDER     = new Color(58, 58, 58);
	private static final Color BORDER      = new Color(70, 70, 70);
	private static final Color TEXT_NORMAL = new Color(198, 198, 198);
	private static final Color TEXT_HOT    = Color.WHITE;
	private static final Color SHADOW      = new Color(0, 0, 0, 180);

	// Entry whose target no longer exists (e.g. a deleted Inventory Setup).
	private static final Color TEXT_MISSING     = new Color(214, 62, 62);
	private static final Color TEXT_MISSING_HOT = new Color(255, 96, 96);

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

	// ── List-mode layout constants ────────────────────────────────────

	private static final int LIST_ROW_H     = 28;
	private static final int LIST_ICON_SIZE = 18;
	private static final int LIST_ICON_PAD  = 4;
	private static final int LIST_H_PAD     = 10;
	private static final int LIST_V_PAD     = 6;
	private static final int LIST_MAX_W     = 300;
	private static final int LIST_ARC       = 8;
	// Extra height reserved at the bottom of the panel for the back indicator row.
	private static final int LIST_BACK_H    = 22;

	// ── Static strokes ────────────────────────────────────────────────────

	private static final Stroke STROKE_THIN   = new BasicStroke(1.0f);
	private static final Stroke STROKE_BORDER = new BasicStroke(1.5f);
	private static final Stroke STROKE_DASHED = new BasicStroke(
		1.2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0f, new float[]{5f, 5f}, 0f);
	private static final Stroke STROKE_X      = new BasicStroke(
		2.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);

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
	private final RingMenuConfig config;
	private final TooltipManager tooltipManager;

	// Written by setCenter() (EDT); read by render() (client thread). Volatile for visibility.
	private volatile Point center = null;

	// Live geometry, derived from config.ringRadius() by refreshGeometry(). Written on the client
	// thread (render) and constructor; the EDT mouse handlers read only ringRadius and innerRadius —
	// plain int reads whose worst case is one frame of slightly-off hit testing after a size change.
	private int ringRadius;
	private int innerRadius;
	private int centerR;
	private int fullR;
	private int radialMaxWidth;
	private Dimension overlaySize;

	// Rebuilt by refreshFonts() when the configured font type or size changes.
	private Font labelFont;
	private Font arrowFont;
	private RingFontType cachedFontType;
	private int          cachedFontSize = -1;
	private boolean      cachedShrink;

	// Per-slice label layout — font (possibly shrunk to fit), display text (possibly truncated) and
	// whether the drawn text differs from the full label (drives the hover tooltip). Recomputed only
	// when the displayed ring level or the font config changes, never per frame.
	private RingNode  cachedLabelNode;
	private Font[]    sliceFonts;
	private String[]  sliceTexts;
	private boolean[] sliceShortened;

	// ── List-mode layout cache ────────────────────────────────────────

	// Invalidated (set to null) when the displayed node or font config changes.
	private RingNode  cachedListNode;
	private int       listPanelW;
	private int       listPanelH;
	// True when at least one entry in the current list level has a non-null icon.
	private boolean   listAnyIcon;

	// Canvas-absolute bounding rect of the list panel. Written on the client render thread;
	// read on the AWT EDT by isOutsideRing() and getHighlightedIndex(). A volatile reference
	// to a freshly-allocated Rectangle is safe: the EDT always sees a complete object.
	private volatile Rectangle listPanelBounds;

	// ── Per-slice geometry cache — recomputed only when the number of slices changes.
	// Eliminates sin/cos calls for static geometry on every frame.
	private int      cachedN     = -1;
	private double   sliceSize;        // radians per slice
	private double[] sliceAngles;      // center angle of each slice
	private int[]    divDx1, divDy1;   // divider line inner endpoint offsets from center
	private int[]    divDx2, divDy2;   // divider line outer endpoint offsets from center
	private int[]    lblDx,  lblDy;    // label center offsets from center
	private int      maxLabelWidth;    // max pixel width for a label before truncation

	@Inject
	public RingMenuOverlay(Client client, RingController ringController, RingMenuConfig config,
		TooltipManager tooltipManager)
	{
		this.client = client;
		this.ringController = ringController;
		this.config = config;
		this.tooltipManager = tooltipManager;
		setLayer(OverlayLayer.ABOVE_WIDGETS);
		setPosition(OverlayPosition.DYNAMIC);
		refreshFonts();
		refreshGeometry();
	}

	// Rebuilds the radii from config when the ring size changes. The inner and center circles scale
	// proportionally. Invalidates the slice and label caches, which depend on the radii.
	private void refreshGeometry()
	{
		int r = config.ringRadius();
		if (r == ringRadius)
		{
			return;
		}
		ringRadius     = r;
		innerRadius    = Math.round(r * ((float) REF_INNER_RADIUS / REF_RING_RADIUS));
		centerR        = Math.round(r * ((float) REF_CENTER_R / REF_RING_RADIUS));
		fullR          = r + RIM;
		radialMaxWidth = ringRadius - innerRadius - 2 * RADIAL_PAD;
		overlaySize    = new Dimension(fullR * 2, fullR * 2);
		cachedN         = -1;
		cachedLabelNode = null;

		// Keep an open ring anchored: setCenter() derived the preferred location from the old fullR,
		// so a size change while open would otherwise shift the drawn ring away from the hit-tested
		// center by the radius delta.
		Point c = center;
		if (c != null)
		{
			setPreferredLocation(new Point(c.x - fullR, c.y - fullR));
		}
	}

	// Rebuilds the label and arrow fonts from config when the type or size changes. The arrow glyph
	// scales with the label size, preserving the original 26:17 ratio.
	private void refreshFonts()
	{
		RingFontType type   = config.fontType();
		int          size   = config.fontSize();
		boolean      shrink = config.shrinkTextToFit();
		if (type == cachedFontType && size == cachedFontSize && shrink == cachedShrink)
		{
			return;
		}
		cachedFontType  = type;
		cachedFontSize  = size;
		cachedShrink    = shrink;
		cachedLabelNode = null;
		cachedListNode  = null;
		labelFont = type.getFont(size);
		arrowFont = type.getFont(Math.round(size * (26f / 17f)));
	}

	private void rebuildSliceCache(int n)
	{
		if (n == cachedN) return;
		cachedN    = n;
		sliceSize  = 2 * Math.PI / n;
		int midR   = (innerRadius + ringRadius) / 2;
		// Chord width of one slice at the label's radius. For n == 1 the slice spans the whole
		// circle (sin(pi) == 0), so fall back to the horizontal room inside the ring at that height.
		maxLabelWidth = n == 1
			? (int)(2 * Math.sqrt((double) ringRadius * ringRadius - midR * midR) * 0.80)
			: (int)(2 * midR * Math.sin(Math.PI / n) * 0.80);
		sliceAngles = new double[n];
		divDx1 = new int[n]; divDy1 = new int[n];
		divDx2 = new int[n]; divDy2 = new int[n];
		lblDx  = new int[n]; lblDy  = new int[n];
		for (int i = 0; i < n; i++)
		{
			sliceAngles[i] = sliceSize * i - Math.PI / 2;
			double div     = sliceAngles[i] - sliceSize / 2;
			divDx1[i] = (int)(innerRadius * Math.cos(div));
			divDy1[i] = (int)(innerRadius * Math.sin(div));
			divDx2[i] = (int)(ringRadius  * Math.cos(div));
			divDy2[i] = (int)(ringRadius  * Math.sin(div));
			lblDx[i]  = (int)(midR         * Math.cos(sliceAngles[i]));
			lblDy[i]  = (int)(midR         * Math.sin(sliceAngles[i]));
		}
	}

	public void setCenter(Point absoluteCanvasPoint)
	{
		// Read the size straight from config rather than the cached fields, which belong to the
		// render thread — this runs on the EDT and must not race refreshGeometry().
		int r = config.ringRadius() + RIM;
		this.center = absoluteCanvasPoint;
		setPreferredLocation(new Point(
			absoluteCanvasPoint.x - r,
			absoluteCanvasPoint.y - r
		));
	}

	public boolean isOutsideRing(int x, int y)
	{
		RingNode node = ringController.currentNode();
		if (node != null && node.isExpandedList())
		{
			Rectangle b = listPanelBounds;
			return b == null || !b.contains(x, y);
		}
		Point c = center;
		if (c == null) return true;
		int dx = x - c.x;
		int dy = y - c.y;
		return dx * dx + dy * dy > ringRadius * ringRadius;
	}

	// Called from the AWT EDT (mouse handler). Uses only pure math and benign int-field reads — it
	// must not touch the slice-geometry cache, which belongs to the client thread (render).
	public int getHighlightedIndex()
	{
		Point c = center;
		if (c == null) return -1;
		List<RingEntry> entries = ringController.currentEntries();
		if (entries.isEmpty()) return -1;

		RingNode node = ringController.currentNode();
		if (node != null && node.isExpandedList())
		{
			Rectangle b = listPanelBounds;
			if (b == null) return -1;
			net.runelite.api.Point m = client.getMouseCanvasPosition();
			if (!b.contains(m.getX(), m.getY())) return -1;
			int localY = m.getY() - b.y - LIST_V_PAD;
			if (localY < 0) return -1;
			int row = localY / LIST_ROW_H;
			return row < entries.size() ? row : -1;
		}

		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		int dx = mouse.getX() - c.x;
		int dy = mouse.getY() - c.y;
		if (dx * dx + dy * dy <= innerRadius * innerRadius) return -1;

		return sliceIndexFromAngle(Math.atan2(dy, dx), entries.size());
	}

	// Slice i is centered at (2π/n)·i − π/2, so the nearest slice to an angle is a rounded division.
	private static int sliceIndexFromAngle(double angle, int n)
	{
		double sliceSize = 2 * Math.PI / n;
		int idx = (int) Math.round((angle + Math.PI / 2) / sliceSize);
		return ((idx % n) + n) % n;
	}

	private static String truncate(String label, FontMetrics fm, int maxW)
	{
		if (fm.stringWidth(label) <= maxW) return label;
		int ellW = fm.stringWidth("…");
		while (label.length() > 0 && fm.stringWidth(label) + ellW > maxW)
		{
			label = label.substring(0, label.length() - 1);
		}
		return label + "…";
	}

	// Shadow pass plus the highlight/missing color pick — shared by the radial and horizontal label
	// branches, which differ only in where and in what transform the text lands.
	private void drawSliceLabel(Graphics2D g, String label, int textX, int textY, boolean missing, boolean hot)
	{
		g.setColor(SHADOW);
		g.drawString(label, textX + 1, textY + 1);
		g.setColor(missing
			? (hot ? TEXT_MISSING_HOT : TEXT_MISSING)
			: (hot ? TEXT_HOT : TEXT_NORMAL));
		g.drawString(label, textX, textY);
	}

	// Computes each slice's font and display text for the given ring level: step the font size down
	// (never below MIN_FONT_SIZE) until the full label fits, then truncate whatever still doesn't.
	// Runs once per displayed ring level, not per frame — rebuildSliceCache(n) must have run first.
	private void rebuildLabelLayout(Graphics2D g, RingNode node, List<RingEntry> entries, boolean radial)
	{
		int n = entries.size();
		cachedLabelNode = node;
		sliceFonts      = new Font[n];
		sliceTexts      = new String[n];
		sliceShortened  = new boolean[n];
		int maxW = radial ? radialMaxWidth : maxLabelWidth;

		for (int i = 0; i < n; i++)
		{
			String full = entries.get(i).getLabel();
			Font font = labelFont;
			FontMetrics fm = g.getFontMetrics(font);
			if (cachedShrink)
			{
				int size = cachedFontSize;
				while (fm.stringWidth(full) > maxW && size > MIN_FONT_SIZE)
				{
					size--;
					font = cachedFontType.getFont(size);
					fm = g.getFontMetrics(font);
				}
			}
			String text = truncate(full, fm, maxW);
			sliceFonts[i]     = font;
			sliceTexts[i]     = text;
			sliceShortened[i] = font != labelFont || !text.equals(full);
		}
	}

	// Recomputes the list panel dimensions when the displayed node or font config changes.
	// Must be called after the Graphics2D font is set to labelFont.
	private void rebuildListCache(Graphics2D g, RingNode node, List<RingEntry> entries)
	{
		if (node == cachedListNode) return;
		cachedListNode = node;
		listAnyIcon = entries.stream().anyMatch(e -> e.getIcon() != null);
		int iconColW = listAnyIcon ? LIST_ICON_SIZE + LIST_ICON_PAD : 0;
		FontMetrics fm = g.getFontMetrics(labelFont);
		int maxW = 0;
		for (RingEntry e : entries)
		{
			int w = fm.stringWidth(e.getLabel());
			if (w > maxW) maxW = w;
		}
		listPanelW = Math.min(LIST_MAX_W, iconColW + maxW + LIST_H_PAD * 2);
		listPanelH = LIST_V_PAD * 2 + entries.size() * LIST_ROW_H
			+ (ringController.canGoBack() ? LIST_BACK_H : 0);
	}

	// Renders the current ring level as a vertical list panel centered on c.
	// origin is the canvas point the overlay renderer translated g to for this frame.
	private void renderList(Graphics2D g, Point origin, Point c, RingNode node, List<RingEntry> entries)
	{
		int n = entries.size();
		g.setFont(labelFont);
		rebuildListCache(g, node, entries);

		// Panel canvas position, kept on-screen so a tall list near an edge is still fully clickable.
		int panelLeft = clamp(c.x - listPanelW / 2, 0, client.getCanvasWidth()  - listPanelW);
		int panelTop  = clamp(c.y - listPanelH / 2, 0, client.getCanvasHeight() - listPanelH);

		// Draw relative to where the renderer actually put the overlay this frame — not where we asked
		// for it — so the painted rows always line up with listPanelBounds, which the clicks test.
		int px = panelLeft - origin.x;
		int py = panelTop  - origin.y;

		// Request tighter overlay positioning for the next frame.
		updatePreferredLocation(panelLeft - RIM, panelTop - RIM);

		// Update the volatile canvas-absolute bounds used by isOutsideRing and getHighlightedIndex
		// on the AWT EDT. Written here (client thread), read there; volatile reference is sufficient.
		listPanelBounds = new Rectangle(panelLeft, panelTop, listPanelW, listPanelH);

		// Determine near/far alpha state
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		Rectangle pb = listPanelBounds;
		boolean outside = !pb.contains(mouse.getX(), mouse.getY());

		AlphaComposite acBg    = outside ? AC_BG_FAR    : AC_BG_NEAR;
		AlphaComposite acSlice = outside ? AC_SLICE_FAR : AC_SLICE_NEAR;
		AlphaComposite acFull  = outside ? AC_FULL_FAR  : AC_FULL_NEAR;

		// ── 1. Background rounded rect ────────────────────────────────
		g.setComposite(acBg);
		g.setColor(BG);
		g.fillRoundRect(px, py, listPanelW, listPanelH, LIST_ARC, LIST_ARC);

		// ── 2. Border ─────────────────────────────────────────────────
		g.setComposite(acFull);
		g.setColor(BORDER);
		g.setStroke(STROKE_BORDER);
		g.drawRoundRect(px, py, listPanelW, listPanelH, LIST_ARC, LIST_ARC);

		// ── 3. Hovered row ────────────────────────────────────────────
		int hoveredRow = -1;
		if (!outside)
		{
			int localMy = mouse.getY() - panelTop - LIST_V_PAD;
			if (localMy >= 0)
			{
				int row = localMy / LIST_ROW_H;
				if (row >= 0 && row < n) hoveredRow = row;
			}
		}

		// ── 4. Entry rows ─────────────────────────────────────────────
		int iconColW = listAnyIcon ? LIST_ICON_SIZE + LIST_ICON_PAD : 0;
		FontMetrics fm = g.getFontMetrics(labelFont);

		for (int i = 0; i < n; i++)
		{
			RingEntry entry = entries.get(i);
			int rowY = py + LIST_V_PAD + i * LIST_ROW_H;
			boolean hot = (i == hoveredRow);

			if (hot)
			{
				g.setComposite(acSlice);
				g.setColor(SLICE_HOT);
				g.fillRoundRect(px + 1, rowY, listPanelW - 2, LIST_ROW_H, 4, 4);
			}

			g.setComposite(acFull);
			g.setFont(labelFont);

			int textX = px + LIST_H_PAD;

			BufferedImage icon = entry.getIcon();
			if (icon != null)
			{
				int iconY = rowY + (LIST_ROW_H - LIST_ICON_SIZE) / 2;
				g.drawImage(icon, textX, iconY, LIST_ICON_SIZE, LIST_ICON_SIZE, null);
			}
			textX += iconColW;

			String label = entry.getLabel();
			int maxTextW = listPanelW - (textX - px) - LIST_H_PAD;
			label = truncate(label, fm, maxTextW);
			int textY = rowY + (LIST_ROW_H + fm.getAscent() - fm.getDescent()) / 2;
			drawSliceLabel(g, label, textX, textY, entry.isMissing(), hot);
		}

		// ── 5. Back indicator (visual only; click anywhere below rows triggers back) ──
		if (ringController.canGoBack())
		{
			int backY = py + listPanelH - LIST_BACK_H;
			g.setComposite(acFull);
			g.setColor(DIVIDER);
			g.setStroke(STROKE_THIN);
			g.drawLine(px + LIST_H_PAD, backY, px + listPanelW - LIST_H_PAD, backY);

			boolean hoverBack = !outside
				&& mouse.getY() >= panelTop + LIST_V_PAD + n * LIST_ROW_H
				&& mouse.getY() < panelTop + listPanelH;
			g.setColor(hoverBack ? ARROW_HOT : ARROW_COLD);
			g.setFont(labelFont);
			g.drawString("‹ Back", px + LIST_H_PAD, backY + fm.getAscent());
		}
	}

	private static int clamp(int v, int lo, int hi)
	{
		return Math.max(lo, Math.min(v, Math.max(lo, hi)));
	}

	// Only replaces the preferred location when it actually moved, to avoid a per-frame allocation.
	private void updatePreferredLocation(int x, int y)
	{
		Point cur = getPreferredLocation();
		if (cur == null || cur.x != x || cur.y != y)
		{
			setPreferredLocation(new Point(x, y));
		}
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		Point c = center;
		RingNode node = ringController.currentNode();
		if (node == null || c == null) return null;

		refreshFonts();

		// The renderer translated g to the overlay's bounds location just before calling render(). It can
		// differ from the preferred location (the renderer clamps overlays on-screen, and the preferred
		// location lags a frame when switching between list and radial levels), so all drawing is placed
		// relative to it, keeping the painted ring/list aligned with the canvas-space hit tests.
		Point origin = getBounds().getLocation();

		// ── List mode: bypass radial geometry and render a vertical panel ─
		if (node.isExpandedList())
		{
			List<RingEntry> entries = node.getChildren();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
			g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
			Composite origComposite = g.getComposite();
			Stroke    origStroke    = g.getStroke();
			renderList(g, origin, c, node, entries);
			g.setComposite(origComposite);
			g.setStroke(origStroke);
			// Return a Dimension large enough to contain the list panel for next frame's clip.
			return new Dimension(listPanelW + 2 * RIM, listPanelH + 2 * RIM);
		}

		refreshGeometry();
		// Re-anchor on the ring's center: a list level may have moved the overlay elsewhere.
		updatePreferredLocation(c.x - fullR, c.y - fullR);

		// Take the entries from the node itself, NOT from a second controller call: the stack can be
		// swapped from the EDT (sub-ring click, editor live update) between two synchronized calls,
		// and a node/entries mismatch would index the label cache with the wrong slice count. A
		// node's child list is never mutated once built, so this snapshot is internally consistent.
		List<RingEntry> entries = node.getChildren();
		int n  = entries.size();
		int lx = c.x - origin.x, ly = c.y - origin.y;
		boolean radial = node.getTextOrientation() == TextOrientation.RADIAL;
		if (n > 0)
		{
			rebuildSliceCache(n);
			if (node != cachedLabelNode) rebuildLabelLayout(g, node, entries, radial);
		}

		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

		Composite origComposite = g.getComposite();
		Stroke    origStroke    = g.getStroke();

		// Read mouse position once for the entire frame.
		net.runelite.api.Point mouse = client.getMouseCanvasPosition();
		int     mdx         = mouse.getX() - c.x;
		int     mdy         = mouse.getY() - c.y;
		int     mDistSq     = mdx * mdx + mdy * mdy;
		boolean outside     = mDistSq > ringRadius * ringRadius;
		boolean hoverCenter = mDistSq <= innerRadius * innerRadius;
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
		g.fillOval(lx - ringRadius, ly - ringRadius, ringRadius * 2, ringRadius * 2);

		int highlighted = (n > 0 && !hoverCenter) ? sliceIndexFromAngle(Math.atan2(mdy, mdx), n) : -1;

		// ── 2. Highlighted pie slice ──────────────────────────────────────
		if (highlighted >= 0)
		{
			// Doubles, not fillArc's int degrees: truncating 360/n drifts the painted slice away from
			// the exact angular range sliceIndexFromAngle() hit-tests.
			double extent   = Math.toDegrees(sliceSize);
			double startDeg = -Math.toDegrees(sliceAngles[highlighted]) + extent / 2;
			g.setComposite(acSlice);
			g.setColor(SLICE_HOT);
			g.fill(new Arc2D.Double(lx - ringRadius, ly - ringRadius,
				ringRadius * 2, ringRadius * 2, startDeg, -extent, Arc2D.PIE));
		}

		// ── 3. Center button fill ─────────────────────────────────────────
		g.setComposite(acCenter);
		g.setColor(!canBack
			? (hoverCenter ? CENTER_CLOSE_HOT : CENTER_CLOSE_COLD)
			: (hoverCenter ? CENTER_BACK_HOT  : CENTER_BACK_COLD));
		g.fillOval(lx - centerR, ly - centerR, centerR * 2, centerR * 2);

		// ── 4. Slice divider lines (precomputed offsets) ──────────────────
		if (n > 0)
		{
			g.setComposite(acDiv);
			g.setColor(DIVIDER);
			g.setStroke(STROKE_THIN);
			for (int i = 0; i < n; i++)
			{
				g.drawLine(lx + divDx1[i], ly + divDy1[i], lx + divDx2[i], ly + divDy2[i]);
			}
		}

		// ── 5. Outer ring border ──────────────────────────────────────────
		g.setComposite(acFull);
		g.setColor(BORDER);
		g.setStroke(STROKE_BORDER);
		g.drawOval(lx - ringRadius, ly - ringRadius, ringRadius * 2, ringRadius * 2);

		// ── 6. Inner circle border ────────────────────────────────────────
		g.setColor(!canBack
			? (hoverCenter ? INNER_CLOSE_HOT : INNER_CLOSE_COLD)
			: (hoverCenter ? INNER_BACK_HOT  : INNER_BACK_COLD));
		g.drawOval(lx - innerRadius, ly - innerRadius, innerRadius * 2, innerRadius * 2);

		// Dashed outer ring when cursor is outside
		if (outside)
		{
			g.setComposite(AC_DASHED);
			g.setColor(DASHED_COLOR);
			g.setStroke(STROKE_DASHED);
			g.drawOval(lx - ringRadius, ly - ringRadius, ringRadius * 2, ringRadius * 2);
		}

		if (n > 0)
		{
			// ── 7. Slice text labels (layout precomputed per ring level) ──
			g.setComposite(acFull);

			if (radial)
			{
				// Labels run along the radius. Right-half slices read inside → outside; left-half
				// slices are flipped 180° (and right-aligned against the inner circle) so their
				// text is never upside down.
				AffineTransform origTx = g.getTransform();
				for (int i = 0; i < n; i++)
				{
					g.setFont(sliceFonts[i]);
					FontMetrics fm = g.getFontMetrics();
					String label = sliceTexts[i];
					boolean left = Math.cos(sliceAngles[i]) < 0;

					g.setTransform(origTx);
					g.translate(lx, ly);
					g.rotate(left ? sliceAngles[i] + Math.PI : sliceAngles[i]);
					int textX = left
						? -(innerRadius + RADIAL_PAD) - fm.stringWidth(label)
						: innerRadius + RADIAL_PAD;
					int textY = fm.getAscent() / 2 - 1;

					drawSliceLabel(g, label, textX, textY,
						entries.get(i).isMissing(), i == highlighted);
				}
				g.setTransform(origTx);
			}
			else
			{
				for (int i = 0; i < n; i++)
				{
					g.setFont(sliceFonts[i]);
					FontMetrics fm = g.getFontMetrics();
					String label = sliceTexts[i];
					int textX = lx + lblDx[i] - fm.stringWidth(label) / 2;
					int textY = ly + lblDy[i] + fm.getAscent() / 2 - 1;

					drawSliceLabel(g, label, textX, textY,
						entries.get(i).isMissing(), i == highlighted);
				}
			}

			// ── 7b. Hover tooltip for shortened labels ────────────────────
			if (highlighted >= 0 && !outside && config.hoverTooltip() && sliceShortened[highlighted])
			{
				tooltipManager.add(new Tooltip(entries.get(highlighted).getLabel()));
			}
		}

		// ── 8. Center button content ──────────────────────────────────────
		g.setComposite(acFull);

		if (!canBack)
		{
			g.setColor(hoverCenter ? X_HOT : X_COLD);
			g.setStroke(STROKE_X);
			int off = centerR / 3;
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

		return overlaySize;
	}
}
