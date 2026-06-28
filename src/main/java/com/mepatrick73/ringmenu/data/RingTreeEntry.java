package com.mepatrick73.ringmenu.data;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class RingTreeEntry
{
	public enum Type { ACTION, SUB_RING }

	private Type type;
	private String label;

	// ACTION fields
	private String providerId;
	private String entryId;

	// SUB_RING fields
	private List<RingTreeEntry> children;

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

	public boolean isAction()
	{
		return type == Type.ACTION;
	}

	public boolean isSubRing()
	{
		return type == Type.SUB_RING;
	}
}
