package com.mepatrick73.ringmenu.providers;

import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mepatrick73.ringmenu.data.RingTreeEntry;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.banktags.BankTagsService;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Singleton
public class InventorySetupsProvider implements RingProvider
{
	public static final String ID = "inventorySetups";

	private static final String IS_CONFIG_GROUP     = "inventorysetups";
	// Key prefix matches InventorySetups plugin v3 config schema. If that plugin migrates
	// to a v4 key, this prefix must be updated to match.
	private static final String SETUPS_V3_PREFIX    = "setupsV3_";
	// Murmur3-128 hash prefix used by InventorySetups to name its bank-tag layouts.
	// Guava Hashing is a transitive dependency from runelite-client — not declared in build.gradle.
	private static final String LAYOUT_PREFIX_MARKER = "_invsetup_";

	@Inject private ConfigManager configManager;
	@Inject private BankTagsService bankTagsService;
	@Inject private Gson gson;

	private volatile String activeSetupName;
	private volatile String activeTagName;

	@Override
	public String getId()
	{
		return ID;
	}

	@Override
	public String getLabel()
	{
		return "Inventory Setups";
	}

	@Override
	public List<RingTreeEntry> getAvailableEntries()
	{
		List<RingTreeEntry> entries = new ArrayList<>();
		String wholePrefix = ConfigManager.getWholeKey(IS_CONFIG_GROUP, null, SETUPS_V3_PREFIX);
		List<String> keys = configManager.getConfigurationKeys(wholePrefix);

		for (String wholeKey : keys)
		{
			String configKey = wholeKey.substring(wholePrefix.length() - SETUPS_V3_PREFIX.length());
			String json = configManager.getConfiguration(IS_CONFIG_GROUP, configKey);
			if (json == null) continue;
			try
			{
				JsonObject obj = gson.fromJson(json, JsonObject.class);
				String name = obj.get("name").getAsString();
				entries.add(RingTreeEntry.action(name, ID, name));
			}
			catch (Exception e)
			{
				log.warn("Failed to read setup name from key {}", configKey, e);
			}
		}
		return entries;
	}

	@Override
	public Runnable buildAction(RingTreeEntry entry)
	{
		String name = entry.getEntryId();
		// Must use murmur3_128 to match the tag name InventorySetups itself created.
		String tagName = LAYOUT_PREFIX_MARKER + Hashing.murmur3_128().hashUnencodedChars(name).toString();
		return () ->
		{
			activeSetupName = name;
			activeTagName = tagName;
			bankTagsService.openBankTag(tagName, BankTagsService.OPTION_ALLOW_MODIFICATIONS | BankTagsService.OPTION_HIDE_TAG_NAME);
		};
	}

	public String getActiveSetupName()
	{
		return activeSetupName;
	}

	public boolean isActiveTagCurrent()
	{
		return activeTagName != null && activeTagName.equals(bankTagsService.getActiveTag());
	}

	public void clear()
	{
		activeSetupName = null;
		activeTagName = null;
	}

	@Override
	public void deactivate()
	{
		clear();
	}

	public void reapplyIfNeeded()
	{
		if (activeTagName != null)
		{
			bankTagsService.openBankTag(activeTagName, BankTagsService.OPTION_ALLOW_MODIFICATIONS | BankTagsService.OPTION_HIDE_TAG_NAME);
		}
	}
}
