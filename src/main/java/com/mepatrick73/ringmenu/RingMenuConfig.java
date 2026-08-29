package com.mepatrick73.ringmenu;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(RingMenuConfig.GROUP)
public interface RingMenuConfig extends Config
{
	String GROUP = "ringmenu";

	@Range(
		min = 80,
		max = 250
	)
	@ConfigItem(
		keyName = "ringRadius",
		name = "Ring size",
		description = "Radius of the ring menu, in pixels. The inner circle scales with it.",
		position = 0
	)
	default int ringRadius()
	{
		return 135;
	}

	@ConfigItem(
		keyName = "fontType",
		name = "Font",
		description = "Font used for the ring menu labels",
		position = 1
	)
	default RingFontType fontType()
	{
		return RingFontType.RUNESCAPE;
	}

	@Range(
		min = 8,
		max = 40
	)
	@ConfigItem(
		keyName = "fontSize",
		name = "Font size",
		description = "Size of the ring menu label text",
		position = 2
	)
	default int fontSize()
	{
		return 17;
	}

	@ConfigItem(
		keyName = "shrinkTextToFit",
		name = "Shrink text to fit",
		description = "Reduce the label font size (down to a minimum) so long names fit in their slice before truncating",
		position = 3
	)
	default boolean shrinkTextToFit()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hoverTooltip",
		name = "Full name on hover",
		description = "Show the full entry name in a tooltip when hovering a slice whose label is shortened",
		position = 4
	)
	default boolean hoverTooltip()
	{
		return true;
	}
}
