package com.mepatrick73.ringmenu.providers;

import com.mepatrick73.ringmenu.data.RingTreeEntry;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.banktags.BankTagsService;
import net.runelite.client.util.Text;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Singleton
public class BankTagsProvider implements RingProvider
{
	public static final String ID = "bankTags";

	private static final String CONFIG_GROUP   = "banktags";
	private static final String TAG_TABS_CONFIG = "tagtabs";

	@Inject private ConfigManager configManager;
	@Inject private BankTagsService bankTagsService;
	@Inject private EventBus eventBus;

	private volatile String activeTag;

	// Fired when the tag tab list changes; may be invoked from any thread.
	private volatile Runnable changeListener;

	@Override
	public void setChangeListener(Runnable listener)
	{
		changeListener = listener;
	}

	@Override
	public void onLoad()
	{
		eventBus.register(this);
	}

	@Override
	public void onUnload()
	{
		eventBus.unregister(this);
	}

	// The available entries are the Bank Tags plugin's tag tabs, which live in its config — a change
	// to that key is a change to our entry list.
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!CONFIG_GROUP.equals(event.getGroup()) || !TAG_TABS_CONFIG.equals(event.getKey()))
		{
			return;
		}
		Runnable listener = changeListener;
		if (listener != null)
		{
			listener.run();
		}
	}

	@Override
	public String getId()
	{
		return ID;
	}

	@Override
	public String getLabel()
	{
		return "Bank Tags";
	}

	@Override
	public List<RingTreeEntry> getAvailableEntries()
	{
		List<RingTreeEntry> entries = new ArrayList<>();
		String csv = configManager.getConfiguration(CONFIG_GROUP, TAG_TABS_CONFIG);
		if (csv == null || csv.isEmpty()) return entries;

		for (String tag : Text.fromCSV(csv))
		{
			entries.add(RingTreeEntry.action(tag, ID, tag));
		}
		return entries;
	}

	// Must be called on the client thread.
	@Override
	public Runnable buildAction(RingTreeEntry entry)
	{
		String tag = entry.getEntryId();
		return () ->
		{
			activeTag = tag;
			bankTagsService.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
		};
	}

	@Override
	public void deactivate()
	{
		activeTag = null;
	}

	// Must be called on the client thread.
	@Override
	public Runnable cancelAction()
	{
		if (activeTag == null) return null;
		return () ->
		{
			activeTag = null;
			bankTagsService.closeBankTag();
		};
	}
}
