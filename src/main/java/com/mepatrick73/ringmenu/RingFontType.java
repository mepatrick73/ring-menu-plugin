package com.mepatrick73.ringmenu;

import java.awt.Font;
import java.util.function.Supplier;
import lombok.AllArgsConstructor;
import net.runelite.client.ui.FontManager;

@AllArgsConstructor
public enum RingFontType
{
	// RuneScape fonts derive from the bundled glyphs loaded by FontManager.
	RUNESCAPE("RuneScape", FontManager::getRunescapeFont, null),
	RUNESCAPE_SMALL("RuneScape Small", FontManager::getRunescapeSmallFont, null),
	RUNESCAPE_BOLD("RuneScape Bold", FontManager::getRunescapeBoldFont, null),
	// System fonts are resolved by family name; Java falls back to a default if not installed.
	ARIAL("Arial", null, "Arial"),
	CAMBRIA("Cambria", null, "Cambria"),
	ROCKWELL("Rockwell", null, "Rockwell"),
	SEGOE_UI("Segoe UI", null, "Segoe UI"),
	TIMES_NEW_ROMAN("Times New Roman", null, "Times New Roman"),
	VERDANA("Verdana", null, "Verdana");

	private final String label;
	private final Supplier<Font> baseFont; // RuneScape fonts: derive from this loaded font
	private final String family;           // system fonts: resolve by family name

	public Font getFont(int size)
	{
		return baseFont != null
			? baseFont.get().deriveFont((float) size)
			: new Font(family, Font.PLAIN, size);
	}

	@Override
	public String toString()
	{
		return label;
	}
}
