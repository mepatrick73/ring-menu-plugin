package com.mepatrick73.ringmenu.data;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
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
	// Use setHotkey(Keybind) to write these fields — raw setters are suppressed.
	@Setter(AccessLevel.NONE)
	private int hotkeyCode      = 0;
	@Setter(AccessLevel.NONE)
	private int hotkeyModifiers = 0;
	// Boxed Boolean so missing field in old JSON deserializes as null (treated as true).
	private Boolean enabled;
	// How the root ring draws its slice labels. Stored as null when it is the default
	// (HORIZONTAL) so old plugin versions and old JSON round-trip cleanly.
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private TextOrientation textOrientation;
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

	public TextOrientation getTextOrientation()
	{
		return TextOrientation.orDefault(textOrientation);
	}

	public void setTextOrientation(TextOrientation orientation)
	{
		textOrientation = TextOrientation.storedForm(orientation);
	}

	public boolean isEnabled()
	{
		return enabled == null || enabled;
	}

	public void setEnabled(boolean value)
	{
		enabled = value ? null : Boolean.FALSE;
	}

	public List<RingTreeEntry> getEntries()
	{
		if (entries == null) entries = new ArrayList<>();
		return entries;
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
