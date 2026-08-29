package com.mepatrick73.ringmenu.providers;

import com.mepatrick73.ringmenu.data.RingTreeEntry;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Talks to the Inventory Setups plugin over its PluginMessage API instead of reading its config
 * directly. The API lives in {@code inventorysetups.InventorySetupsPluginMessageHandler} (upstream
 * since v1.25.0); this decouples us from the plugin's config schema ({@code setupsV3_} keys) and its
 * layout tag hashing.
 */
@Slf4j
@Singleton
public class InventorySetupsProvider implements RingProvider
{
	public static final String ID = "inventorySetups";

	// Mirrors the API_* constants in inventorysetups.InventorySetupsPluginMessageHandler. Duplicated as
	// literals because the two plugins share no compile-time dependency.
	private static final String IS_NAMESPACE = "inventory-setups";
	private static final String MSG_GET_SETUPS = "get-setups";
	private static final String MSG_SETUPS_CHANGED = "setups-changed";
	private static final String MSG_VIEW = "view";
	private static final String MSG_CLEAR = "clear";
	private static final String DATA_SETUPS = "setups";
	private static final String DATA_SETUP = "setup";
	private static final String DATA_VERSION = "version";
	// Highest API_VERSION we were written against. A higher one means the contract changed in a way we
	// have not been updated for, so we warn once and carry on reading the fields we know.
	private static final int SUPPORTED_API_VERSION = 1;

	@Inject private EventBus eventBus;

	// Setup names, kept current by the setups-changed broadcast. Seeded once in onLoad.
	private volatile List<String> cachedNames = List.of();

	// True once Inventory Setups has actually told us its setups. While false we know nothing, which is
	// indistinguishable from the plugin being disabled or not yet started, so entries stay unflagged.
	private volatile boolean setupsKnown;

	// The setup the ring last opened, so cancel only clears that one (and only while it is still active).
	private volatile String openedSetup;

	// So an unknown API version is reported once per session rather than on every broadcast.
	private volatile boolean apiVersionWarned;

	// Fired after every setups-changed broadcast, from the client thread.
	private volatile Runnable changeListener;

	@Override
	public void setChangeListener(Runnable listener)
	{
		changeListener = listener;
	}

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
	public void onLoad()
	{
		eventBus.register(this);

		// Bootstrap, since Inventory Setups only broadcasts when its setups change and may have started
		// before us. An empty result is not an answer: with no acknowledgement in the API it is
		// indistinguishable from Inventory Setups not being loaded at all, so leave setupsKnown false and
		// flag nothing. The setupsKnown re-check guards the narrow case where a broadcast lands on the
		// client thread while this runs, whose payload is at least as fresh as ours.
		List<String> names = new ArrayList<>();
		eventBus.post(new PluginMessage(IS_NAMESPACE, MSG_GET_SETUPS, Map.of(DATA_SETUPS, names)));
		if (!names.isEmpty() && !setupsKnown)
		{
			cachedNames = toSetupNames(names);
			setupsKnown = true;
		}
	}

	@Override
	public void onUnload()
	{
		eventBus.unregister(this);
		// This is a singleton, so its state outlives a disable/enable of the plugin. Drop what we know:
		// while unregistered we miss every setups-changed, and Inventory Setups does not re-broadcast an
		// unchanged list, so a stale cache would otherwise survive forever and mis-report deleted setups.
		cachedNames = List.of();
		setupsKnown = false;
		openedSetup = null;
	}

	@Subscribe
	public void onPluginMessage(PluginMessage message)
	{
		if (!IS_NAMESPACE.equals(message.getNamespace()) || !MSG_SETUPS_CHANGED.equals(message.getName()))
		{
			return;
		}
		checkApiVersion(message);

		Object data = message.getData().get(DATA_SETUPS);
		if (data instanceof Collection)
		{
			cachedNames = toSetupNames((Collection<?>) data);
			setupsKnown = true;
			Runnable listener = changeListener;
			if (listener != null)
			{
				listener.run();
			}
		}
	}

	// Inventory Setups bumps its API_VERSION when the contract changes in a breaking way. We keep reading
	// the payload, since the setups field is the part we use and is most likely to survive, but say so.
	private void checkApiVersion(PluginMessage message)
	{
		Object version = message.getData().get(DATA_VERSION);
		if (version instanceof Integer && (Integer) version > SUPPORTED_API_VERSION && !apiVersionWarned)
		{
			apiVersionWarned = true;
			log.warn("Inventory Setups reports API version {}, this plugin was written against {}",
				version, SUPPORTED_API_VERSION);
		}
	}

	// The payload crosses a plugin boundary with no shared types, so its element type is only a
	// convention. Keep the Strings and drop anything else here, rather than letting an unchecked cast
	// surface as a ClassCastException somewhere further from the cause.
	private static List<String> toSetupNames(Collection<?> data)
	{
		List<String> names = new ArrayList<>(data.size());
		for (Object o : data)
		{
			if (o instanceof String)
			{
				names.add((String) o);
			}
		}
		if (names.size() != data.size())
		{
			log.warn("Ignored {} non-String entries in an {} payload", data.size() - names.size(), IS_NAMESPACE);
		}
		return List.copyOf(names);
	}

	@Override
	public List<RingTreeEntry> getAvailableEntries()
	{
		List<String> names = cachedNames;
		List<RingTreeEntry> entries = new ArrayList<>(names.size());
		for (String name : names)
		{
			entries.add(RingTreeEntry.action(name, ID, name));
		}
		return entries;
	}

	// A saved entry is broken once its setup has been deleted or renamed in Inventory Setups. Only
	// answered once we have a real list: an empty cache means "we haven't been told", not "no setups".
	@Override
	public boolean isEntryValid(RingTreeEntry entry)
	{
		return !setupsKnown || cachedNames.contains(entry.getEntryId());
	}

	// Called before any ring entry's action runs. Forget what we opened, so cancel does not later close a
	// setup on behalf of an entry belonging to another provider.
	@Override
	public void deactivate()
	{
		openedSetup = null;
	}

	// The returned action must run on the client thread; RingManager arranges that.
	@Override
	public Runnable buildAction(RingTreeEntry entry)
	{
		String name = entry.getEntryId();
		return () ->
		{
			openedSetup = name;
			eventBus.post(new PluginMessage(IS_NAMESPACE, MSG_VIEW, Map.of(DATA_SETUP, name)));
		};
	}

	// Triggered by the ring's cancel (centre X). Asks Inventory Setups to clear the setup the ring opened,
	// tagged with its name so it is left alone if the user has since switched to a different setup.
	@Override
	public Runnable cancelAction()
	{
		String name = openedSetup;
		if (name == null)
		{
			return null;
		}
		return () ->
		{
			openedSetup = null;
			eventBus.post(new PluginMessage(IS_NAMESPACE, MSG_CLEAR, Map.of(DATA_SETUP, name)));
		};
	}
}
