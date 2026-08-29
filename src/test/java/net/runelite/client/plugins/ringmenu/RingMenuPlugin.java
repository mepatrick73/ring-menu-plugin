package net.runelite.client.plugins.ringmenu;

import com.google.inject.Provides;
import com.mepatrick73.ringmenu.RingManager;
import com.mepatrick73.ringmenu.RingMenuConfig;
import com.mepatrick73.ringmenu.editor.RingEditorPanel;
import com.mepatrick73.ringmenu.engine.runtime.RingController;
import com.mepatrick73.ringmenu.engine.runtime.RingMenuOverlay;
import com.mepatrick73.ringmenu.providers.BankTagsProvider;
import com.mepatrick73.ringmenu.providers.InventorySetupsProvider;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDependency;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.banktags.BankTagsPlugin;
import net.runelite.client.ui.ClientToolbar;
import net.runelite.client.ui.NavigationButton;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import javax.swing.SwingUtilities;
import java.awt.event.MouseEvent;
import java.util.List;

// Test-only entry point. RuneLite's scanner requires getSuperclass() == Plugin.class,
// so a bridge class extending com.mepatrick73.ringmenu.RingMenuPlugin cannot work.
// All logic delegates to the shared supporting classes in com.mepatrick73.ringmenu.*.
@PluginDependency(BankTagsPlugin.class)
@PluginDescriptor(
	name = "Ring Menu",
	description = "Radial ring menu for quickly accessing bank tags and inventory setups",
	tags = {"ring", "menu", "radial", "bank", "inventory", "setups"}
)
public class RingMenuPlugin extends Plugin
{
	@Inject private Client client;
	@Inject private ClientThread clientThread;
	@Inject private RingController ringController;
	@Inject private RingMenuOverlay overlay;
	@Inject private OverlayManager overlayManager;
	@Inject private MouseManager mouseManager;
	@Inject private InventorySetupsProvider inventorySetupsProvider;
	@Inject private BankTagsProvider bankTagsProvider;
	@Inject private RingManager ringManager;
	@Inject private RingEditorPanel editorPanel;
	@Inject private ClientToolbar clientToolbar;

	private volatile NavigationButton navButton;

	private final MouseAdapter mouseListener = new MouseAdapter()
	{
		@Override
		public MouseEvent mousePressed(MouseEvent event)
		{
			if (!ringController.isOpen()) return event;

			if (SwingUtilities.isRightMouseButton(event))
			{
				ringController.close();
				event.consume();
				return event;
			}

			if (SwingUtilities.isLeftMouseButton(event))
			{
				if (overlay.isOutsideRing(event.getX(), event.getY()))
				{
					ringController.close();
					event.consume();
					return event;
				}

				int idx = overlay.getHighlightedIndex();
				if (idx >= 0)
				{
					ringController.currentEntries().get(idx).onSelect(ringController);
				}
				else if (ringController.canGoBack())
				{
					ringController.back();
				}
				else
				{
					clientThread.invoke(() ->
						ringManager.getProviders().forEach(p ->
						{
							Runnable cancel = p.cancelAction();
							if (cancel != null) cancel.run();
						})
					);
					ringController.close();
				}
				event.consume();
				return event;
			}

			return event;
		}
	};

	@Provides
	RingMenuConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RingMenuConfig.class);
	}

	@Override
	protected void startUp()
	{
		ringManager.setProviders(List.of(inventorySetupsProvider, bankTagsProvider));
		ringManager.getProviders().forEach(p -> p.setChangeListener(editorPanel::refreshProviderEntries));
		ringManager.load();
		overlayManager.add(overlay);
		mouseManager.registerMouseListener(mouseListener);
		SwingUtilities.invokeLater(() ->
		{
			editorPanel.rebuildRingRows();
			navButton = NavigationButton.builder()
				.tooltip("Ring Menu")
				.icon(ImageUtil.loadImageResource(com.mepatrick73.ringmenu.RingMenuPlugin.class, "ring_icon.png"))
				.priority(6)
				.panel(editorPanel)
				.build();
			clientToolbar.addNavigation(navButton);
		});
	}

	@Override
	protected void shutDown()
	{
		mouseManager.unregisterMouseListener(mouseListener);
		overlayManager.remove(overlay);
		ringController.close();
		ringManager.unload();
		SwingUtilities.invokeLater(() ->
		{
			if (navButton != null) clientToolbar.removeNavigation(navButton);
		});
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		ringManager.updateInputState();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		if (!ringController.isOpen()) return;
		Point mouse = client.getMouseCanvasPosition();
		if (!overlay.isOutsideRing(mouse.getX(), mouse.getY()))
		{
			client.getMenu().setMenuEntries(new MenuEntry[0]);
		}
	}
}
