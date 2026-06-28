package com.mepatrick73.ringmenu.editor;

import com.google.gson.Gson;
import com.mepatrick73.ringmenu.RingManager;
import com.mepatrick73.ringmenu.data.RingDefinition;
import com.mepatrick73.ringmenu.data.RingTreeEntry;
import com.mepatrick73.ringmenu.providers.RingProvider;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

import javax.inject.Inject;
import javax.swing.*;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;
import javax.swing.border.MatteBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** JPanel that tells its parent JScrollPane "fill my width to the viewport width." */
class TrackWidthPanel extends JPanel implements Scrollable
{
	TrackWidthPanel(LayoutManager layout) { super(layout); }
	@Override public Dimension getPreferredScrollableViewportSize() { return getPreferredSize(); }
	@Override public int getScrollableUnitIncrement(Rectangle r, int o, int d) { return 8; }
	@Override public int getScrollableBlockIncrement(Rectangle r, int o, int d) { return 40; }
	@Override public boolean getScrollableTracksViewportWidth()  { return true; }
	@Override public boolean getScrollableTracksViewportHeight() { return false; }
}

public class RingEditorPanel extends PluginPanel
{
	private static final Color BG       = ColorScheme.DARK_GRAY_COLOR;
	private static final Color BG_DARK  = ColorScheme.DARKER_GRAY_COLOR;
	private static final Color BG_MED   = ColorScheme.MEDIUM_GRAY_COLOR;
	private static final Color ACCENT   = ColorScheme.BRAND_ORANGE;
	private static final Color TEXT     = Color.WHITE;
	private static final Color TEXT_DIM = new Color(120, 120, 120);

	private static final int ROW_H            = 27;
	private static final int MIN_ENTRIES_ROWS = 4;
	private static final int INDENT_W         = 14;
	private static final int DRAG_HANDLE_W    = 14;
	private static final int DRAG_THRESHOLD   = 6;

	private final RingManager ringManager;
	private final Gson         gson;

	// Cards
	private final CardLayout cards     = new CardLayout();
	private final JPanel     cardPanel = new JPanel(cards);
	private final JPanel     ringRows  = new JPanel();

	// Detail state
	private RingDefinition selectedRing;
	private String         ringSnapshot;
	private boolean        dirty;
	private JButton        saveBtn;
	private JPanel         detailRoot;
	private JSplitPane     splitPane;

	// Tree state
	// Identity-based: @Data generates hashCode() from children, so adding a child
	// changes the hash and breaks a regular HashSet lookup. IdentityHashMap uses == instead.
	private final Set<RingTreeEntry>  expandedRings = Collections.newSetFromMap(new IdentityHashMap<>());
	private       List<RingTreeEntry> activeList;

	// Entries container (custom paint for drop indicator)
	private JPanel entriesContainer;

	// Providers
	private JPanel     providersContainer;
	private final List<JCheckBox> providerToggles = new ArrayList<>();
	private JTextField pickerSearch;

	// Name field ref (for save on focus-lost)
	private JTextField nameField;

	// ── DnD state ──────────────────────────────────────────────────

	private static final class RowInfo
	{
		final RingTreeEntry       entry;
		final List<RingTreeEntry> parentList;
		final int                 indexInParent;
		final JPanel              component;

		RowInfo(RingTreeEntry e, List<RingTreeEntry> p, int i, JPanel c)
		{ entry = e; parentList = p; indexInParent = i; component = c; }
	}

	private final List<RowInfo> renderedRows = new ArrayList<>();

	// Active drag
	private RingTreeEntry       dragEntry;
	private List<RingTreeEntry> dragSourceList;
	private int                 dragSourceIndex;
	private Point               dragStartScreen;
	private boolean             dragging;

	// Drop target
	private int                 dropLineY          = -1;
	private boolean             dropIntoSubring    = false;
	private RingTreeEntry       dropTargetSubring  = null;
	private List<RingTreeEntry> dropTargetList;
	private int                 dropTargetIndex;

	// ── Lifecycle ──────────────────────────────────────────────────

	@Override
	public Dimension getPreferredSize()
	{
		for (Container p = getParent(); p != null; p = p.getParent())
		{
			if (p instanceof JViewport)
			{
				int h = p.getHeight();
				if (h > 0) return new Dimension(PANEL_WIDTH, h);
			}
		}
		return new Dimension(PANEL_WIDTH, 600);
	}

	@Inject
	public RingEditorPanel(RingManager ringManager, Gson gson)
	{
		this.ringManager = ringManager;
		this.gson        = gson;
		setLayout(new BorderLayout());
		setBackground(BG);
		cardPanel.setBackground(BG);
		add(cardPanel, BorderLayout.CENTER);
		cardPanel.add(buildListView(), "list");
		cardPanel.add(buildDetailPlaceholder(), "detail");
		cards.show(cardPanel, "list");
	}

	// ── Ring list view ─────────────────────────────────────────────

	private JPanel buildListView()
	{
		JPanel root = new JPanel(new BorderLayout());
		root.setBackground(BG);

		JLabel title = new JLabel("My Rings");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(TEXT);
		title.setBorder(new EmptyBorder(10, 10, 6, 10));
		root.add(title, BorderLayout.NORTH);

		ringRows.setLayout(new BoxLayout(ringRows, BoxLayout.Y_AXIS));
		ringRows.setBackground(BG);
		ringRows.setBorder(new EmptyBorder(0, 6, 6, 6));

		JScrollPane scroll = new JScrollPane(ringRows,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		scroll.setBorder(null);
		scroll.getViewport().setBackground(BG);
		root.add(scroll, BorderLayout.CENTER);

		JButton newBtn = new JButton("+ New Ring");
		newBtn.setFont(FontManager.getRunescapeFont());
		newBtn.setBackground(ACCENT);
		newBtn.setForeground(Color.BLACK);
		newBtn.setFocusPainted(false);
		newBtn.setBorder(new EmptyBorder(8, 0, 8, 0));
		newBtn.addActionListener(e -> promptNewRing());

		JPanel btnWrap = new JPanel(new BorderLayout());
		btnWrap.setBackground(BG);
		btnWrap.setBorder(new EmptyBorder(6, 6, 6, 6));
		btnWrap.add(newBtn);
		root.add(btnWrap, BorderLayout.SOUTH);

		rebuildRingRows();
		return root;
	}

	public void rebuildRingRows()
	{
		ringRows.removeAll();
		for (RingDefinition ring : ringManager.getRings())
		{
			ringRows.add(buildRingRow(ring));
			ringRows.add(Box.createVerticalStrut(4));
		}
		ringRows.revalidate();
		ringRows.repaint();
	}

	private JPanel buildRingRow(RingDefinition ring)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(BG_DARK);
		row.setBorder(new CompoundBorder(
			new MatteBorder(0, 3, 0, 0, ACCENT), new EmptyBorder(8, 8, 8, 6)));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));
		row.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

		JLabel name = new JLabel(ring.getName());
		name.setFont(FontManager.getRunescapeFont());
		name.setForeground(TEXT);

		String hkTxt = ring.hasHotkey() ? ring.getHotkey().toString() : "no key";
		JLabel hk = new JLabel(hkTxt);
		hk.setFont(FontManager.getRunescapeSmallFont());
		hk.setForeground(TEXT_DIM);

		JPanel labels = new JPanel(new GridLayout(2, 1));
		labels.setBackground(BG_DARK);
		labels.add(name);
		labels.add(hk);

		JButton del = smallBtn("×");
		del.setForeground(TEXT_DIM);
		del.addActionListener(e -> {
			int ok = JOptionPane.showConfirmDialog(this,
				"Delete ring \"" + ring.getName() + "\"?",
				"Delete Ring", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if (ok != JOptionPane.YES_OPTION) return;
			ringManager.removeRing(ring);
			rebuildRingRows();
		});

		row.add(labels, BorderLayout.CENTER);
		row.add(del, BorderLayout.EAST);
		row.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override public void mouseClicked(java.awt.event.MouseEvent e) { selectRing(ring); }
			@Override public void mouseEntered(java.awt.event.MouseEvent e) { row.setBackground(BG_MED); labels.setBackground(BG_MED); del.setBackground(BG_MED); }
			@Override public void mouseExited(java.awt.event.MouseEvent e)  { row.setBackground(BG_DARK); labels.setBackground(BG_DARK); del.setBackground(BG_DARK); }
		});
		return row;
	}

	private void promptNewRing()
	{
		String name = JOptionPane.showInputDialog(this, "Ring name:", "New Ring", JOptionPane.PLAIN_MESSAGE);
		if (name == null || name.trim().isEmpty()) return;
		RingDefinition ring = ringManager.addRing(name.trim());
		rebuildRingRows();
		selectRing(ring);
	}

	// ── Detail view ────────────────────────────────────────────────

	private JPanel buildDetailPlaceholder()
	{
		detailRoot = new JPanel(new BorderLayout());
		detailRoot.setBackground(BG);
		return detailRoot;
	}

	private void selectRing(RingDefinition ring)
	{
		selectedRing = ring;
		ringSnapshot  = gson.toJson(ring);
		dirty         = false;
		expandedRings.clear();
		activeList = ring.getEntries();
		populateDetail();
		cards.show(cardPanel, "detail");
	}

	private void populateDetail()
	{
		detailRoot.removeAll();

		JPanel fixedNorth = new JPanel();
		fixedNorth.setLayout(new BoxLayout(fixedNorth, BoxLayout.Y_AXIS));
		fixedNorth.setBackground(BG);

		JPanel header = new JPanel(new BorderLayout(6, 0));
		header.setBackground(BG_DARK);
		header.setBorder(new CompoundBorder(
			new MatteBorder(0, 0, 1, 0, BG_MED), new EmptyBorder(8, 8, 8, 8)));
		JButton back = new JButton("← Rings");
		back.setFont(FontManager.getRunescapeSmallFont());
		back.setBackground(BG_DARK);
		back.setForeground(ACCENT);
		back.setBorderPainted(false);
		back.setFocusPainted(false);
		back.addActionListener(e -> navigateBack());
		JLabel ringLabel = new JLabel(selectedRing.getName());
		ringLabel.setFont(FontManager.getRunescapeBoldFont());
		ringLabel.setForeground(TEXT);
		header.add(back, BorderLayout.WEST);
		header.add(ringLabel, BorderLayout.CENTER);
		fixedNorth.add(header);

		fixedNorth.add(buildNameRow());
		fixedNorth.add(buildHotkeyRow());
		fixedNorth.add(buildDivider("Add Entry"));
		fixedNorth.add(buildSubRingButton());
		fixedNorth.add(buildPickerControls());
		detailRoot.add(fixedNorth, BorderLayout.NORTH);

		entriesContainer = new JPanel()
		{
			{
				setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
				setBackground(BG);
			}

			@Override
			protected void paintChildren(Graphics g)
			{
				super.paintChildren(g);
				if (!dragging) return;
				Graphics2D g2 = (Graphics2D) g.create();
				g2.setColor(ACCENT);
				if (dropIntoSubring && dropTargetSubring != null)
				{
					for (RowInfo ri : renderedRows)
					{
						if (ri.entry == dropTargetSubring)
						{
							Rectangle b = ri.component.getBounds();
							g2.setStroke(new BasicStroke(2));
							g2.drawRect(b.x + 1, b.y + 1, b.width - 2, b.height - 2);
							break;
						}
					}
				}
				else if (dropLineY >= 0)
				{
					g2.fillRect(DRAG_HANDLE_W, dropLineY - 1, getWidth() - DRAG_HANDLE_W, 2);
				}
				g2.dispose();
			}
		};
		populateEntriesContainer();

		JScrollPane entriesScroll = new JScrollPane(entriesContainer,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		entriesScroll.setBorder(new MatteBorder(1, 1, 1, 1, BG_MED));
		entriesScroll.getViewport().setBackground(BG);
		entriesScroll.getVerticalScrollBar().setUnitIncrement(8);

		JPanel entriesWrapper = new JPanel(new BorderLayout());
		entriesWrapper.setBackground(BG);
		entriesWrapper.setBorder(new EmptyBorder(0, 6, 6, 6));
		entriesWrapper.add(buildDivider("Entries"), BorderLayout.NORTH);
		entriesWrapper.add(entriesScroll, BorderLayout.CENTER);
		entriesWrapper.setMinimumSize(new Dimension(0, MIN_ENTRIES_ROWS * ROW_H + 24));

		providersContainer = new TrackWidthPanel(null);
		providersContainer.setLayout(new BoxLayout(providersContainer, BoxLayout.Y_AXIS));
		providersContainer.setBackground(BG);
		populateProvidersContainer();

		JScrollPane providersScroll = new JScrollPane(providersContainer,
			JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
		providersScroll.setBorder(new MatteBorder(1, 1, 1, 1, BG_MED));
		providersScroll.getViewport().setBackground(BG);
		providersScroll.getVerticalScrollBar().setUnitIncrement(8);

		JPanel providersWrapper = new JPanel(new BorderLayout());
		providersWrapper.setBackground(BG);
		providersWrapper.setBorder(new EmptyBorder(0, 6, 6, 6));
		providersWrapper.add(providersScroll, BorderLayout.CENTER);

		splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, entriesWrapper, providersWrapper);
		splitPane.setResizeWeight(0.0);
		splitPane.setDividerSize(6);
		splitPane.setBorder(null);
		splitPane.setDividerLocation(MIN_ENTRIES_ROWS * ROW_H + 30);

		detailRoot.add(splitPane, BorderLayout.CENTER);

		saveBtn = new JButton(dirty ? "Save *" : "Save");
		saveBtn.setFont(FontManager.getRunescapeBoldFont());
		saveBtn.setBackground(dirty ? ACCENT : BG_MED);
		saveBtn.setForeground(dirty ? Color.BLACK : TEXT_DIM);
		saveBtn.setFocusPainted(false);
		saveBtn.setBorderPainted(false);
		saveBtn.addActionListener(e -> saveChanges());

		JButton discardBtn = new JButton("Discard");
		discardBtn.setFont(FontManager.getRunescapeFont());
		discardBtn.setBackground(BG_DARK);
		discardBtn.setForeground(TEXT_DIM);
		discardBtn.setFocusPainted(false);
		discardBtn.setBorderPainted(false);
		discardBtn.addActionListener(e -> discardChanges());

		JPanel actionBar = new JPanel(new GridLayout(1, 2, 6, 0));
		actionBar.setBackground(BG);
		actionBar.setBorder(new EmptyBorder(4, 8, 8, 8));
		actionBar.add(discardBtn);
		actionBar.add(saveBtn);
		detailRoot.add(actionBar, BorderLayout.SOUTH);

		detailRoot.revalidate();
		detailRoot.repaint();
	}

	// ── Edit mode helpers ───────────────────────────────────────────

	private void markDirty()
	{
		dirty = true;
		if (saveBtn != null)
		{
			saveBtn.setText("Save *");
			saveBtn.setBackground(ACCENT);
			saveBtn.setForeground(Color.BLACK);
		}
	}

	private void saveChanges()
	{
		ringManager.save();
		ringSnapshot = gson.toJson(selectedRing);
		dirty = false;
		if (saveBtn != null)
		{
			saveBtn.setText("Save");
			saveBtn.setBackground(BG_MED);
			saveBtn.setForeground(TEXT_DIM);
		}
	}

	private void discardChanges()
	{
		RingDefinition snap = gson.fromJson(ringSnapshot, RingDefinition.class);
		selectedRing.setName(snap.getName());
		selectedRing.setHotkey(snap.getHotkey());
		selectedRing.setEntries(snap.getEntries());
		dirty = false;
		expandedRings.clear();
		activeList = selectedRing.getEntries();
		populateDetail();
	}

	private void navigateBack()
	{
		if (dirty)
		{
			int choice = JOptionPane.showOptionDialog(this,
				"You have unsaved changes to \"" + selectedRing.getName() + "\".",
				"Unsaved Changes",
				JOptionPane.YES_NO_CANCEL_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null,
				new String[]{"Save", "Discard", "Cancel"},
				"Save");
			if (choice == 0)      saveChanges();
			else if (choice == 1) discardChanges();
			else                  return;
		}
		rebuildRingRows();
		cards.show(cardPanel, "list");
	}

	// ── Fixed control builders ──────────────────────────────────────

	private JPanel buildNameRow()
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(BG);
		row.setBorder(new EmptyBorder(6, 8, 3, 8));
		JLabel lbl = new JLabel("Name");
		lbl.setFont(FontManager.getRunescapeSmallFont());
		lbl.setForeground(TEXT_DIM);
		lbl.setPreferredSize(new Dimension(48, 20));
		nameField = new JTextField(selectedRing.getName());
		nameField.setFont(FontManager.getRunescapeFont());
		nameField.setBackground(BG_DARK);
		nameField.setForeground(TEXT);
		nameField.setCaretColor(TEXT);
		nameField.setBorder(new EmptyBorder(4, 6, 4, 6));
		nameField.addActionListener(e -> saveRingName());
		nameField.addFocusListener(new java.awt.event.FocusAdapter()
		{
			@Override public void focusLost(java.awt.event.FocusEvent e) { saveRingName(); }
		});
		row.add(lbl, BorderLayout.WEST);
		row.add(nameField, BorderLayout.CENTER);
		return row;
	}

	private void saveRingName()
	{
		String t = nameField.getText().trim();
		if (!t.isEmpty()) { selectedRing.setName(t); markDirty(); }
	}

	private JPanel buildHotkeyRow()
	{
		JPanel row = new JPanel(new BorderLayout(6, 0));
		row.setBackground(BG);
		row.setBorder(new EmptyBorder(3, 8, 6, 8));
		JLabel lbl = new JLabel("Hotkey");
		lbl.setFont(FontManager.getRunescapeSmallFont());
		lbl.setForeground(TEXT_DIM);
		lbl.setPreferredSize(new Dimension(48, 20));
		String cur = selectedRing.hasHotkey() ? selectedRing.getHotkey().toString() : "Click to set";
		JButton btn = new JButton(cur);
		btn.setFont(FontManager.getRunescapeFont());
		btn.setBackground(BG_DARK);
		btn.setForeground(TEXT);
		btn.setFocusPainted(false);
		btn.setBorder(new EmptyBorder(4, 8, 4, 8));
		btn.addActionListener(e -> {
			btn.setText("Press a key…");
			btn.requestFocusInWindow();
			btn.addKeyListener(new java.awt.event.KeyAdapter()
			{
				@Override public void keyPressed(java.awt.event.KeyEvent ke)
				{
					net.runelite.client.config.Keybind kb = new net.runelite.client.config.Keybind(ke.getKeyCode(), ke.getModifiersEx());
					selectedRing.setHotkey(kb);
					markDirty();
					btn.setText(kb.toString());
					btn.removeKeyListener(this);
				}
			});
		});
		row.add(lbl, BorderLayout.WEST);
		row.add(btn, BorderLayout.CENTER);
		return row;
	}

	private JPanel buildDivider(String label)
	{
		JPanel div = new JPanel(new BorderLayout(6, 0));
		div.setBackground(BG);
		div.setBorder(new EmptyBorder(4, 8, 4, 8));
		JLabel lbl = new JLabel(label.toUpperCase());
		lbl.setFont(FontManager.getRunescapeSmallFont());
		lbl.setForeground(ACCENT);
		JSeparator sep = new JSeparator();
		sep.setForeground(BG_MED);
		div.add(lbl, BorderLayout.WEST);
		div.add(sep, BorderLayout.CENTER);
		return div;
	}

	private JPanel buildSubRingButton()
	{
		JButton btn = new JButton("+ Add Sub-Ring");
		btn.setFont(FontManager.getRunescapeFont());
		btn.setBackground(BG_DARK);
		btn.setForeground(TEXT_DIM);
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.addActionListener(e -> {
			String name = JOptionPane.showInputDialog(this, "Sub-ring name:", "New Sub-Ring", JOptionPane.PLAIN_MESSAGE);
			if (name == null || name.trim().isEmpty()) return;
			activeList.add(RingTreeEntry.subRing(name.trim()));
			markDirty();
			refreshEntries();
		});
		JPanel wrap = new JPanel(new BorderLayout());
		wrap.setBackground(BG);
		wrap.setBorder(new EmptyBorder(0, 8, 4, 8));
		wrap.add(btn, BorderLayout.CENTER);
		return wrap;
	}

	private JPanel buildPickerControls()
	{
		JPanel wrapper = new JPanel(new BorderLayout());
		wrapper.setBackground(BG);
		wrapper.setBorder(new EmptyBorder(0, 8, 4, 8));

		JPanel toggleRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
		toggleRow.setBackground(BG);
		providerToggles.clear();
		for (RingProvider p : ringManager.getProviders())
		{
			JCheckBox cb = new JCheckBox(p.getLabel(), true);
			cb.setFont(FontManager.getRunescapeSmallFont());
			cb.setBackground(BG);
			cb.setForeground(TEXT_DIM);
			cb.setFocusPainted(false);
			cb.addActionListener(e -> refreshProviders());
			providerToggles.add(cb);
			toggleRow.add(cb);
		}

		pickerSearch = new JTextField();
		pickerSearch.setFont(FontManager.getRunescapeFont());
		pickerSearch.setBackground(BG_DARK);
		pickerSearch.setForeground(TEXT);
		pickerSearch.setCaretColor(TEXT);
		pickerSearch.setBorder(new EmptyBorder(4, 6, 4, 6));
		pickerSearch.putClientProperty("JTextField.placeholderText", "Filter…");
		pickerSearch.getDocument().addDocumentListener(new javax.swing.event.DocumentListener()
		{
			@Override public void insertUpdate(javax.swing.event.DocumentEvent e)  { refreshProviders(); }
			@Override public void removeUpdate(javax.swing.event.DocumentEvent e)  { refreshProviders(); }
			@Override public void changedUpdate(javax.swing.event.DocumentEvent e) { refreshProviders(); }
		});

		JButton clearBtn = new JButton("×");
		clearBtn.setFont(FontManager.getRunescapeSmallFont());
		clearBtn.setBackground(BG_DARK);
		clearBtn.setForeground(TEXT_DIM);
		clearBtn.setBorderPainted(false);
		clearBtn.setFocusPainted(false);
		clearBtn.setPreferredSize(new Dimension(22, 0));
		clearBtn.addActionListener(e -> pickerSearch.setText(""));

		JPanel searchRow = new JPanel(new BorderLayout());
		searchRow.setBackground(BG_DARK);
		searchRow.setBorder(new MatteBorder(1, 0, 1, 0, BG_MED));
		searchRow.add(pickerSearch, BorderLayout.CENTER);
		searchRow.add(clearBtn, BorderLayout.EAST);

		wrapper.add(toggleRow, BorderLayout.NORTH);
		wrapper.add(searchRow, BorderLayout.SOUTH);
		return wrapper;
	}

	// ── Entries tree ────────────────────────────────────────────────

	private void populateEntriesContainer()
	{
		renderedRows.clear();
		entriesContainer.removeAll();

		entriesContainer.add(buildRootRow());
		entriesContainer.add(Box.createVerticalStrut(2));

		renderLevel(selectedRing.getEntries(), 0);

		if (renderedRows.isEmpty())
		{
			JLabel empty = new JLabel("No entries yet");
			empty.setFont(FontManager.getRunescapeSmallFont());
			empty.setForeground(TEXT_DIM);
			empty.setBorder(new EmptyBorder(4, 8 + DRAG_HANDLE_W, 4, 0));
			entriesContainer.add(empty);
		}
	}

	private JPanel buildRootRow()
	{
		boolean isActive = (activeList == selectedRing.getEntries());
		JPanel row = new JPanel(new BorderLayout());
		row.setBackground(BG_DARK);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_H));
		if (isActive)
			row.setBorder(new CompoundBorder(
				new MatteBorder(0, 3, 0, 0, ACCENT),
				new EmptyBorder(5, DRAG_HANDLE_W + 5, 5, 4)));
		else
			row.setBorder(new EmptyBorder(5, DRAG_HANDLE_W + 8, 5, 4));

		JLabel lbl = new JLabel("┌ " + selectedRing.getName());
		lbl.setFont(FontManager.getRunescapeBoldFont());
		lbl.setForeground(isActive ? ACCENT : TEXT_DIM);
		lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		lbl.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override public void mouseClicked(java.awt.event.MouseEvent e)
			{
				activeList = selectedRing.getEntries();
				refreshEntries();
			}
		});
		row.add(lbl, BorderLayout.CENTER);
		return row;
	}

	private void renderLevel(List<RingTreeEntry> entries, int depth)
	{
		boolean levelIsActive = (entries == activeList);
		for (int i = 0; i < entries.size(); i++)
		{
			RingTreeEntry entry = entries.get(i);
			JPanel row = buildEntryRow(entry, entries, i, depth, levelIsActive);
			renderedRows.add(new RowInfo(entry, entries, i, row));
			entriesContainer.add(row);
			entriesContainer.add(Box.createVerticalStrut(2));

			if (entry.isSubRing() && expandedRings.contains(entry))
				renderLevel(entry.getChildren(), depth + 1);
		}
	}

	private JPanel buildEntryRow(RingTreeEntry entry, List<RingTreeEntry> parentList,
	                              int idx, int depth, boolean levelIsActive)
	{
		boolean isActiveParent = entry.isSubRing() && entry.getChildren() == activeList;
		boolean expanded       = expandedRings.contains(entry);

		Color textColor = levelIsActive ? TEXT : TEXT_DIM;

		JPanel row = new JPanel(new BorderLayout(0, 0));
		row.setBackground(BG_DARK);
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, ROW_H));

		int leftContentPad = 8 + depth * INDENT_W;
		Border contentBorder;
		if (isActiveParent)
			contentBorder = new CompoundBorder(
				new MatteBorder(0, 3, 0, 0, ACCENT),
				new EmptyBorder(5, leftContentPad - 3 + DRAG_HANDLE_W, 5, 4));
		else
			contentBorder = new EmptyBorder(5, leftContentPad + DRAG_HANDLE_W, 5, 4);

		JPanel handle = new JPanel()
		{
			@Override
			protected void paintComponent(Graphics g)
			{
				super.paintComponent(g);
				g.setColor(BG_MED);
				int cx = getWidth() / 2;
				int cy = getHeight() / 2;
				for (int row = -3; row <= 3; row += 3)
				{
					g.fillRect(cx - 3, cy + row - 1, 2, 2);
					g.fillRect(cx,     cy + row - 1, 2, 2);
				}
			}
		};
		handle.setBackground(BG_DARK);
		handle.setPreferredSize(new Dimension(DRAG_HANDLE_W, 0));
		handle.setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));

		handle.addMouseListener(new java.awt.event.MouseAdapter()
		{
			@Override
			public void mousePressed(java.awt.event.MouseEvent e)
			{
				dragEntry       = entry;
				dragSourceList  = parentList;
				dragSourceIndex = idx;
				dragStartScreen = e.getLocationOnScreen();
				dragging        = false;
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e)
			{
				if (dragging) commitDrop();
				clearDragState();
			}
		});

		handle.addMouseMotionListener(new java.awt.event.MouseMotionAdapter()
		{
			@Override
			public void mouseDragged(java.awt.event.MouseEvent e)
			{
				if (!dragging)
				{
					Point cur = e.getLocationOnScreen();
					int dx = Math.abs(cur.x - dragStartScreen.x);
					int dy = Math.abs(cur.y - dragStartScreen.y);
					if (dx + dy > DRAG_THRESHOLD) dragging = true;
				}
				if (dragging)
				{
					Point inContainer = SwingUtilities.convertPoint(handle, e.getPoint(), entriesContainer);
					updateDropTarget(inContainer.y);
					entriesContainer.repaint();
				}
			}
		});

		JPanel content = new JPanel(new BorderLayout(4, 0));
		content.setBackground(BG_DARK);
		content.setBorder(contentBorder);

		JLabel lbl;
		if (entry.isSubRing())
		{
			String arrow = expanded ? "▼ " : "► ";
			lbl = new JLabel("└ " + arrow + entry.getLabel());
			lbl.setForeground(isActiveParent ? ACCENT : textColor);
			lbl.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			lbl.addMouseListener(new java.awt.event.MouseAdapter()
			{
				@Override
				public void mouseClicked(java.awt.event.MouseEvent e)
				{
					if (e.getX() <= FontManager.getRunescapeFont().getSize() * 2)
					{
						if (expanded) expandedRings.remove(entry);
						else          expandedRings.add(entry);
					}
					else
					{
						activeList = entry.getChildren();
						if (!expanded) expandedRings.add(entry);
					}
					refreshEntries();
				}
			});
		}
		else
		{
			lbl = new JLabel("└ " + entry.getLabel());
			lbl.setForeground(textColor);
		}
		lbl.setFont(FontManager.getRunescapeFont());

		JButton del = smallBtn("×");
		del.setForeground(TEXT_DIM);
		del.setBackground(BG_DARK);
		del.addActionListener(e -> {
			if (entry.isSubRing())
			{
				int ok = JOptionPane.showConfirmDialog(this,
					"Delete sub-ring \"" + entry.getLabel() + "\" and all its entries?",
					"Delete Sub-Ring", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
				if (ok != JOptionPane.YES_OPTION) return;
				expandedRings.remove(entry);
				if (entry.getChildren() == activeList) activeList = parentList;
			}
			parentList.remove(entry);
			markDirty();
			refreshEntries();
		});

		content.add(lbl, BorderLayout.CENTER);
		content.add(del, BorderLayout.EAST);

		row.add(handle, BorderLayout.WEST);
		row.add(content, BorderLayout.CENTER);
		return row;
	}

	private void refreshEntries()
	{
		if (entriesContainer == null) return;
		clearDragIndicator();
		populateEntriesContainer();
		entriesContainer.revalidate();
		entriesContainer.repaint();
	}

	// ── Drag and drop ───────────────────────────────────────────────

	private void updateDropTarget(int mouseY)
	{
		dropLineY         = -1;
		dropIntoSubring   = false;
		dropTargetSubring = null;
		dropTargetList    = null;

		for (int i = 0; i < renderedRows.size(); i++)
		{
			RowInfo ri = renderedRows.get(i);
			if (ri.entry == dragEntry) continue;

			Rectangle b = ri.component.getBounds();
			if (mouseY > b.y + b.height) continue;

			int topZone    = b.y + b.height / 3;
			int bottomZone = b.y + 2 * b.height / 3;

			if (ri.entry.isSubRing() && mouseY > topZone && mouseY < bottomZone)
			{
				dropIntoSubring   = true;
				dropTargetSubring = ri.entry;
				dropTargetList    = ri.entry.getChildren();
				dropTargetIndex   = ri.entry.getChildren().size();
			}
			else if (mouseY <= topZone)
			{
				dropTargetList  = ri.parentList;
				dropTargetIndex = ri.indexInParent;
				dropLineY       = b.y;
			}
			else
			{
				dropTargetList  = ri.parentList;
				dropTargetIndex = ri.indexInParent + 1;
				dropLineY       = b.y + b.height;
			}
			return;
		}

		if (!renderedRows.isEmpty())
		{
			RowInfo last   = renderedRows.get(renderedRows.size() - 1);
			Rectangle b    = last.component.getBounds();
			dropTargetList  = last.parentList;
			dropTargetIndex = last.parentList.size();
			dropLineY       = b.y + b.height;
		}
	}

	private void commitDrop()
	{
		if (dragEntry == null || dropTargetList == null) return;

		if (dropIntoSubring && isAncestor(dragEntry, dropTargetSubring)) return;

		dragSourceList.remove(dragSourceIndex);

		if (dropTargetList == dragSourceList && dropTargetIndex > dragSourceIndex)
			dropTargetIndex--;

		dropTargetIndex = Math.max(0, Math.min(dropTargetIndex, dropTargetList.size()));
		dropTargetList.add(dropTargetIndex, dragEntry);

		markDirty();
		refreshEntries();
	}

	private boolean isAncestor(RingTreeEntry ancestor, RingTreeEntry node)
	{
		if (node == null || !ancestor.isSubRing()) return false;
		for (RingTreeEntry child : ancestor.getChildren())
		{
			if (child == node) return true;
			if (child.isSubRing() && isAncestor(child, node)) return true;
		}
		return false;
	}

	private void clearDragState()
	{
		dragEntry       = null;
		dragSourceList  = null;
		dragStartScreen = null;
		dragging        = false;
		clearDragIndicator();
		if (entriesContainer != null) entriesContainer.repaint();
	}

	private void clearDragIndicator()
	{
		dropLineY         = -1;
		dropIntoSubring   = false;
		dropTargetSubring = null;
		dropTargetList    = null;
	}

	// ── Providers ──────────────────────────────────────────────────

	private void populateProvidersContainer()
	{
		providersContainer.removeAll();
		String query = pickerSearch != null ? pickerSearch.getText().trim().toLowerCase() : "";
		List<RingProvider> providers = ringManager.getProviders();

		for (int i = 0; i < providers.size(); i++)
		{
			if (!providerToggles.isEmpty() && !providerToggles.get(i).isSelected()) continue;
			RingProvider provider = providers.get(i);

			List<RingTreeEntry> available = provider.getAvailableEntries();
			if (!query.isEmpty())
				available = available.stream()
					.filter(e -> e.getLabel().toLowerCase().contains(query))
					.collect(java.util.stream.Collectors.toList());
			if (available.isEmpty()) continue;

			JLabel hdr = new JLabel(provider.getLabel());
			hdr.setFont(FontManager.getRunescapeSmallFont());
			hdr.setForeground(ACCENT);
			hdr.setBorder(new EmptyBorder(10, 4, 4, 0));
			hdr.setAlignmentX(Component.LEFT_ALIGNMENT);
			providersContainer.add(hdr);

			TrackWidthPanel list = new TrackWidthPanel(null);
			list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
			list.setBackground(BG);
			for (RingTreeEntry entry : available)
			{
				list.add(buildPickerRow(entry));
				list.add(Box.createVerticalStrut(1));
			}

			final int cap = 150;
			JScrollPane scroll = new JScrollPane(list,
				JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER)
			{
				@Override public Dimension getPreferredSize()
				{
					return new Dimension(super.getPreferredSize().width,
						Math.min(list.getPreferredSize().height, cap));
				}
				@Override public Dimension getMaximumSize()
				{
					return new Dimension(Integer.MAX_VALUE, cap);
				}
			};
			scroll.setBorder(new MatteBorder(1, 1, 1, 1, BG_MED));
			scroll.getViewport().setBackground(BG);
			scroll.getVerticalScrollBar().setUnitIncrement(8);

			JPanel scrollWrap = new JPanel(new BorderLayout());
			scrollWrap.setBackground(BG);
			scrollWrap.setBorder(new EmptyBorder(0, 4, 6, 4));
			scrollWrap.setAlignmentX(Component.LEFT_ALIGNMENT);
			scrollWrap.add(scroll, BorderLayout.CENTER);
			providersContainer.add(scrollWrap);
		}

		providersContainer.revalidate();
		providersContainer.repaint();
	}

	private void refreshProviders()
	{
		if (providersContainer == null) return;
		populateProvidersContainer();
		if (detailRoot != null) { detailRoot.revalidate(); detailRoot.repaint(); }
	}

	private JPanel buildPickerRow(RingTreeEntry entry)
	{
		JPanel row = new JPanel(new BorderLayout(4, 0));
		row.setBackground(BG_DARK);
		row.setBorder(new EmptyBorder(7, 8, 7, 4));
		row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 36));

		JLabel lbl = new JLabel(entry.getLabel());
		lbl.setFont(FontManager.getRunescapeFont());
		lbl.setForeground(TEXT);

		JButton add = smallBtn("+");
		add.setForeground(ACCENT);
		add.addActionListener(e -> {
			activeList.add(entry);
			markDirty();
			refreshEntries();
		});

		row.add(lbl, BorderLayout.CENTER);
		row.add(add, BorderLayout.EAST);
		return row;
	}

	// ── Helpers ─────────────────────────────────────────────────────

	private JButton smallBtn(String text)
	{
		JButton btn = new JButton(text);
		btn.setFont(FontManager.getRunescapeFont());
		btn.setBackground(BG_DARK);
		btn.setForeground(TEXT);
		btn.setFocusPainted(false);
		btn.setBorderPainted(false);
		btn.setPreferredSize(new Dimension(24, 22));
		btn.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
		return btn;
	}
}
