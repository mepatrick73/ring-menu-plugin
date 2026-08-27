package com.mepatrick73.ringmenu.engine.model;

import com.mepatrick73.ringmenu.engine.runtime.RingController;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class RingNode implements RingEntry
{
	@Getter
	private final String label;

	@Getter
	private final List<RingEntry> children = new ArrayList<>();

	// Set by the builder once the children are in: true when anything inside this sub-ring is missing,
	// so a broken entry is still visible while its sub-ring is collapsed.
	@Setter
	private boolean missing;

	@Override
	public boolean isMissing()
	{
		return missing;
	}

	@Override
	public void onSelect(RingController controller)
	{
		controller.pushRing(this);
	}
}
