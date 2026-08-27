package com.mepatrick73.ringmenu.engine.model;

import com.mepatrick73.ringmenu.engine.runtime.RingController;

public interface RingEntry
{
	String getLabel();
	void onSelect(RingController controller);

	// True when this entry (or, for a sub-ring, anything inside it) no longer resolves to a real
	// target — e.g. an Inventory Setup that has since been deleted. Drawn in red by the overlay.
	default boolean isMissing()
	{
		return false;
	}
}
