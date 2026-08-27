package com.mepatrick73.ringmenu.providers;

import com.mepatrick73.ringmenu.data.RingTreeEntry;

import java.util.List;

public interface RingProvider
{
	String getId();
	String getLabel();
	List<RingTreeEntry> getAvailableEntries();

	// The returned action is run on the client thread, so it may touch client state directly.
	Runnable buildAction(RingTreeEntry entry);

	// Called before any ring entry's action runs, so a provider can drop state tied to what it last did.
	default void deactivate()
	{
	}

	// Undoes what this provider last did, or null when there is nothing to undo. Run on the client thread.
	default Runnable cancelAction()
	{
		return null;
	}

	// Called when the ring manager loads/unloads, for providers that need to hook the event bus.
	default void onLoad()
	{
	}

	default void onUnload()
	{
	}

	// Whether a saved entry still points at something this provider can act on. Providers that cannot
	// tell right now (their backing plugin isn't loaded, or hasn't reported yet) must return true, so a
	// saved entry is never flagged as broken just because we are uninformed.
	default boolean isEntryValid(RingTreeEntry entry)
	{
		return true;
	}
}
