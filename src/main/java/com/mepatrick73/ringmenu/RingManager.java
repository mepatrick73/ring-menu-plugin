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
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.vars.InputType;
import net.runelite.api.widgets.Widget;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Point;
import java.awt.event.KeyEvent;
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

	// Sentinel the KeyRemapping plugin writes into the chatbox input while "Press Enter to Chat"
	// is locked/idle. Its presence means a character key won't type, so it may open the ring.
	private static final String PRESS_ENTER_TO_CHAT = "Press Enter to Chat...";

	@Inject private ConfigManager configManager;
	@Inject private KeyManager keyManager;
	@Inject private RingController ringController;
	@Inject private RingMenuOverlay overlay;
	@Inject private Client client;
	@Inject private Gson gson;

	private final List<RingDefinition> rings     = new ArrayList<>();
	private final Map<String, KeyListener> listeners = new HashMap<>();
	private List<RingProvider> providers = new ArrayList<>();

	// Typing context, computed on the client thread (see updateInputState) and read from the AWT
	// key-dispatch thread. Reading widget/varc state directly in the key listener can be momentarily
	// out of sync with the client thread, so we cache it here instead.
	private volatile boolean inputFieldActive; // a text prompt owns the keyboard (search, PM, input widget)
	private volatile boolean chatTypeable;     // a character key would type into the chatbox right now

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
				Type type = new TypeToken<List<RingDefinition>>()
				{
				}.getType();
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
		KeyListener listener = listeners.remove(ring.getId());
		if (listener != null) keyManager.unregisterKeyListener(listener);
		save();
	}

	public void save()
	{
		configManager.setConfiguration(CONFIG_GROUP, CONFIG_KEY_RINGS, gson.toJson(rings));
	}

	// Recomputes the typing context. MUST be called on the client thread (driven by ClientTick),
	// where widget and varc reads are consistent. Mirrors the signals used by RuneLite's
	// KeyRemapping / chat plugins.
	public void updateInputState()
	{
		// An INPUT_FIELD widget owns the keyboard (GE quantity, fairy ring code, etc.), or a
		// message-layer prompt is open: bank/GE search (SEARCH) or private message input.
		inputFieldActive = client.getFocusedInputFieldWidget() != null
			|| client.getVarcIntValue(VarClientID.MESLAYERMODE) != InputType.NONE.getType();

		// A character key types into regular chat only when the chatbox is accepting input and is
		// not sitting idle under KeyRemapping's "Press Enter to Chat" lock.
		Widget chatbox = client.getWidget(InterfaceID.Chatbox.UNIVERSE);
		chatTypeable = chatbox != null && chatbox.getOnKeyListener() != null && !isPressEnterToChatIdle();
	}

	// True when KeyRemapping's "Press Enter to Chat" lock is active and idle (not mid-message),
	// detected by the sentinel text it places in the chatbox input widget.
	private boolean isPressEnterToChatIdle()
	{
		Widget input = client.getWidget(InterfaceID.Chatbox.INPUT);
		return input != null && input.getText() != null && input.getText().endsWith(PRESS_ENTER_TO_CHAT);
	}

	// Returns true when this keypress should fall through to the game instead of opening the ring,
	// because the player is (or could be) typing it into a text input. Reads only the cached flags
	// computed by updateInputState, so it is safe to call from the AWT key-dispatch thread.
	private boolean isTyping(KeyEvent e)
	{
		// A text prompt owns the keyboard — suppress every key.
		if (inputFieldActive) return true;

		// Ctrl/Alt/Meta combos never produce a typed character, and non-character keys (F-keys, etc.)
		// are handled by OSRS even while the chatbox is active — let all of those open the ring.
		if ((e.getModifiersEx() & (KeyEvent.CTRL_DOWN_MASK | KeyEvent.ALT_DOWN_MASK | KeyEvent.META_DOWN_MASK)) != 0)
		{
			return false;
		}
		if (!isCharacterKey(e.getKeyCode())) return false;

		return chatTypeable;
	}

	private static boolean isCharacterKey(int keyCode)
	{
		if (keyCode >= KeyEvent.VK_A && keyCode <= KeyEvent.VK_Z) return true;
		if (keyCode >= KeyEvent.VK_0 && keyCode <= KeyEvent.VK_9) return true;
		if (keyCode >= KeyEvent.VK_NUMPAD0 && keyCode <= KeyEvent.VK_NUMPAD9) return true;
		return keyCode == KeyEvent.VK_SPACE
			|| keyCode == KeyEvent.VK_COMMA
			|| keyCode == KeyEvent.VK_PERIOD
			|| keyCode == KeyEvent.VK_SLASH
			|| keyCode == KeyEvent.VK_SEMICOLON
			|| keyCode == KeyEvent.VK_EQUALS
			|| keyCode == KeyEvent.VK_MINUS
			|| keyCode == KeyEvent.VK_OPEN_BRACKET
			|| keyCode == KeyEvent.VK_CLOSE_BRACKET
			|| keyCode == KeyEvent.VK_BACK_SLASH
			|| keyCode == KeyEvent.VK_QUOTE
			|| keyCode == KeyEvent.VK_BACK_QUOTE;
	}

	private void registerListener(RingDefinition ring)
	{
		KeyListener listener = new RingKeyListener(ring);
		keyManager.registerKeyListener(listener);
		listeners.put(ring.getId(), listener);
	}

	// Custom KeyListener instead of HotkeyListener: HotkeyListener always consumes a matching
	// keypress, which would swallow the character before it could reach the chatbox. We only
	// consume when the ring actually opens, so a hotkey key still types in chat when appropriate.
	private final class RingKeyListener implements KeyListener
	{
		private final RingDefinition ring;
		private boolean consumingTyped;

		RingKeyListener(RingDefinition ring)
		{
			this.ring = ring;
		}

		@Override
		public void keyTyped(KeyEvent e)
		{
			if (consumingTyped)
			{
				e.consume();
			}
		}

		@Override
		public void keyPressed(KeyEvent e)
		{
			if (!ring.getHotkey().matches(e))
			{
				return;
			}

			if (!ring.isEnabled() || isTyping(e))
			{
				// Let the keypress fall through to the game (e.g. typing in chat).
				return;
			}

			if (ringController.isOpen())
			{
				ringController.close();
			}
			else
			{
				overlay.setCenter(new Point(client.getCanvasWidth() / 2, client.getCanvasHeight() / 2));
				ringController.open(buildRingNode(ring.getEntries(), ring.getName()));
			}

			// Consume so the key doesn't also reach the game. Modifier keys are never consumed.
			if (Keybind.getModifierForKeyCode(e.getKeyCode()) == null)
			{
				consumingTyped = true;
				e.consume();
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
			if (ring.getHotkey().matches(e))
			{
				consumingTyped = false;
			}
		}
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
