package com.mepatrick73.ringmenu.engine.model;

import com.mepatrick73.ringmenu.engine.runtime.RingController;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class RingAction implements RingEntry
{
	private final String label;
	private final Runnable action;
	private final boolean missing;

	@Override
	public String getLabel()
	{
		return label;
	}

	@Override
	public boolean isMissing()
	{
		return missing;
	}

	@Override
	public void onSelect(RingController controller)
	{
		controller.close();
		action.run();
	}
}
