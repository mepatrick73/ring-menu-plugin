package com.mepatrick73.ringmenu.providers;

import com.mepatrick73.ringmenu.data.RingTreeEntry;
import net.runelite.client.config.ConfigManager;
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

	@Override
	public Runnable buildAction(RingTreeEntry entry)
	{
		String tag = entry.getEntryId();
		return () -> bankTagsService.openBankTag(tag, BankTagsService.OPTION_ALLOW_MODIFICATIONS);
	}
}
