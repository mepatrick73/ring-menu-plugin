package com.mepatrick73.ringmenu;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(RingMenuConfig.GROUP)
public interface RingMenuConfig extends Config
{
	String GROUP = "ringmenu";

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
}
