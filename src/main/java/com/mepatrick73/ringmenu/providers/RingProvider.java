package com.mepatrick73.ringmenu.providers;

import com.mepatrick73.ringmenu.data.RingTreeEntry;

import java.util.List;

public interface RingProvider
{
	String getId();
	String getLabel();
	List<RingTreeEntry> getAvailableEntries();
	Runnable buildAction(RingTreeEntry entry);

	default void deactivate()
	{
	}

	default Runnable cancelAction()
	{
		return null;
	}
}
