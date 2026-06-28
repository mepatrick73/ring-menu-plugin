package com.mepatrick73.ringmenu.data;

import lombok.Data;
import net.runelite.client.config.Keybind;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class RingDefinition
{
	private String id;
	private String name;
	// Stored as plain ints so Gson can (de)serialize them.
	// Keybind has final fields + no no-args constructor, which breaks Gson on Java 17+.
	private int hotkeyCode      = 0;
	private int hotkeyModifiers = 0;
	private List<RingTreeEntry> entries;

	public Keybind getHotkey()
	{
		return new Keybind(hotkeyCode, hotkeyModifiers);
	}

	public void setHotkey(Keybind kb)
	{
		if (kb == null || kb.equals(Keybind.NOT_SET))
		{
			hotkeyCode      = 0;
			hotkeyModifiers = 0;
		}
		else
		{
			hotkeyCode      = kb.getKeyCode();
			hotkeyModifiers = kb.getModifiers();
		}
	}

	public boolean hasHotkey()
	{
		return !getHotkey().equals(Keybind.NOT_SET);
	}

	public static RingDefinition create(String name)
	{
		RingDefinition r = new RingDefinition();
		r.id      = UUID.randomUUID().toString();
		r.name    = name;
		r.entries = new ArrayList<>();
		return r;
	}
}
