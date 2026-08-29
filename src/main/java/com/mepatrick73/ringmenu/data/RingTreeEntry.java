package com.mepatrick73.ringmenu.data;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ToString
public class RingTreeEntry
{
	public enum Type
	{
		ACTION, SUB_RING
	}

	private Type type;
	private String label;

	// Boxed Boolean so missing field in old JSON deserializes as null (treated as true).
	// Accessors below normalize; raw lombok accessors are suppressed.
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private Boolean enabled;

	// SUB_RING only: how this sub-ring draws its slice labels. Stored as null when it is the
	// default (HORIZONTAL) so old plugin versions and old JSON round-trip cleanly.
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private TextOrientation textOrientation;

	// ACTION fields
	private String providerId;
	private String entryId;

	// SUB_RING fields
	private List<RingTreeEntry> children;

	@Override
	public boolean equals(Object o)
	{
		return this == o;
	}

	@Override
	public int hashCode()
	{
		return System.identityHashCode(this);
	}

	public static RingTreeEntry action(String label, String providerId, String entryId)
	{
		RingTreeEntry e = new RingTreeEntry();
		e.type = Type.ACTION;
		e.label = label;
		e.providerId = providerId;
		e.entryId = entryId;
		return e;
	}

	public static RingTreeEntry subRing(String label)
	{
		RingTreeEntry e = new RingTreeEntry();
		e.type = Type.SUB_RING;
		e.label = label;
		e.children = new ArrayList<>();
		return e;
	}

	public boolean isEnabled()
	{
		return enabled == null || enabled;
	}

	public void setEnabled(boolean value)
	{
		enabled = value ? null : Boolean.FALSE;
	}

	public TextOrientation getTextOrientation()
	{
		return TextOrientation.orDefault(textOrientation);
	}

	public void setTextOrientation(TextOrientation orientation)
	{
		textOrientation = TextOrientation.storedForm(orientation);
	}

	public boolean isAction()
	{
		return type == Type.ACTION;
	}

	public boolean isSubRing()
	{
		return type == Type.SUB_RING;
	}
}
