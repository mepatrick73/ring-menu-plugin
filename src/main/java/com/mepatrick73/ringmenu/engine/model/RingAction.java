package com.mepatrick73.ringmenu.engine.model;

import com.mepatrick73.ringmenu.engine.runtime.RingController;

import java.awt.image.BufferedImage;

public class RingAction implements RingEntry
{
	private final String label;
	private final Runnable action;
	private final boolean missing;
	// Icon shown in list-view mode; null when the provider does not supply one.
	private final BufferedImage icon;

	public RingAction(String label, Runnable action, boolean missing)
	{
		this(label, action, missing, null);
	}

	public RingAction(String label, Runnable action, boolean missing, BufferedImage icon)
	{
		this.label   = label;
		this.action  = action;
		this.missing = missing;
		this.icon    = icon;
	}

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
	public BufferedImage getIcon()
	{
		return icon;
	}

	@Override
	public void onSelect(RingController controller)
	{
		controller.close();
		action.run();
	}
}
