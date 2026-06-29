package com.mepatrick73.ringmenu;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mepatrick73.ringmenu.data.RingDefinition;
import com.mepatrick73.ringmenu.data.RingTreeEntry;
import com.mepatrick73.ringmenu.engine.model.RingAction;
import com.mepatrick73.ringmenu.engine.model.RingNode;
import com.mepatrick73.ringmenu.engine.runtime.RingController;
import com.mepatrick73.ringmenu.engine.runtime.RingMenuOverlay;
import com.mepatrick73.ringmenu.providers.RingProvider;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.util.HotkeyListener;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Point;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Singleton
public class RingManager
{
	private static final String CONFIG_GROUP    = "ringmenu";
	private static final String CONFIG_KEY_RINGS = "rings";

	@Inject private ConfigManager configManager;
	@Inject private KeyManager keyManager;
	@Inject private RingController ringController;
	@Inject private RingMenuOverlay overlay;
	@Inject private Client client;
	@Inject private Gson gson;

	private final List<RingDefinition> rings     = new ArrayList<>();
	private final Map<String, HotkeyListener> listeners = new HashMap<>();
	private List<RingProvider> providers = new ArrayList<>();

	public void setProviders(List<RingProvider> providers)
	{
		this.providers = providers;
	}

	public void load()
	{
		String json = configManager.getConfiguration(CONFIG_GROUP, CONFIG_KEY_RINGS);
		if (json != null && !json.isEmpty())
		{
			try
			{
				Type type = new TypeToken<List<RingDefinition>>(){}.getType();
				List<RingDefinition> loaded = gson.fromJson(json, type);
				if (loaded != null)
				{
					rings.addAll(loaded);
				}
			}
			catch (Exception e)
			{
				log.warn("Failed to load rings", e);
			}
		}
		rings.forEach(this::registerListener);
	}

	public void unload()
	{
		listeners.values().forEach(keyManager::unregisterKeyListener);
		listeners.clear();
		rings.clear();
	}

	public List<RingDefinition> getRings()
	{
		return rings;
	}

	public List<RingProvider> getProviders()
	{
		return providers;
	}

	public RingDefinition addRing(String name)
	{
		RingDefinition ring = RingDefinition.create(name);
		rings.add(ring);
		registerListener(ring);
		save();
		return ring;
	}

	public void removeRing(RingDefinition ring)
	{
		rings.remove(ring);
		HotkeyListener listener = listeners.remove(ring.getId());
		if (listener != null) keyManager.unregisterKeyListener(listener);
		save();
	}

	public void save()
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_RINGS, gson.toJson(rings));
	}

	private void registerListener(RingDefinition ring)
	{
		HotkeyListener listener = new HotkeyListener(() -> ring.getHotkey())
		{
			@Override
			public void hotkeyPressed()
			{
				if (ringController.isOpen()) { ringController.close(); return; }
				overlay.setCenter(new Point(client.getCanvasWidth() / 2, client.getCanvasHeight() / 2));
				ringController.open(buildRingNode(ring.getEntries(), ring.getName()));
			}
		};
		keyManager.registerKeyListener(listener);
		listeners.put(ring.getId(), listener);
	}

	private RingNode buildRingNode(List<RingTreeEntry> entries, String label)
	{
		RingNode node = new RingNode(label);
		for (RingTreeEntry entry : entries)
		{
			if (entry.isSubRing())
			{
				node.getChildren().add(buildRingNode(entry.getChildren(), entry.getLabel()));
			}
			else
			{
				RingProvider provider = findProvider(entry.getProviderId());
				if (provider == null) continue;
				Runnable action = provider.buildAction(entry);
				// RingAction.onSelect() already calls controller.close() before invoking the action.
				node.getChildren().add(new RingAction(entry.getLabel(), () ->
				{
					providers.forEach(RingProvider::deactivate);
					action.run();
				}));
			}
		}
		return node;
	}

	private RingProvider findProvider(String id)
	{
		return providers.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
	}
}
