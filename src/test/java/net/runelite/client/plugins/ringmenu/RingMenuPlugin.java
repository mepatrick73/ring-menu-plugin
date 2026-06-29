package net.runelite.client.plugins.ringmenu;

import com.mepatrick73.ringmenu.RingManager;
import com.mepatrick73.ringmenu.editor.RingEditorPanel;
import com.mepatrick73.ringmenu.engine.runtime.RingController;
import com.mepatrick73.ringmenu.engine.runtime.RingMenuOverlay;
import com.mepatrick73.ringmenu.providers.BankTagsProvider;
import com.mepatrick73.ringmenu.providers.InventorySetupsProvider;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.ScriptID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
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
	@Inject private RingController ringController;
	@Inject private RingMenuOverlay overlay;
	@Inject private OverlayManager overlayManager;
	@Inject private MouseManager mouseManager;
	@Inject private InventorySetupsProvider inventorySetupsProvider;
	@Inject private BankTagsProvider bankTagsProvider;
	@Inject private RingManager ringManager;
	@Inject private RingEditorPanel editorPanel;
	@Inject private ClientToolbar clientToolbar;

	private NavigationButton navButton;

	private boolean bankWasOpen;
	private boolean pendingReapply;

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
					ringController.close();
				}
				event.consume();
				return event;
			}

			return event;
		}
	};

	@Override
	protected void startUp()
	{
		ringManager.setProviders(List.of(inventorySetupsProvider, bankTagsProvider));
		ringManager.load();
		editorPanel.rebuildRingRows();

		overlayManager.add(overlay);
		mouseManager.registerMouseListener(mouseListener);

		navButton = NavigationButton.builder()
			.tooltip("Ring Menu")
			.icon(ImageUtil.loadImageResource(com.mepatrick73.ringmenu.RingMenuPlugin.class, "ring_icon.png"))
			.priority(6)
			.panel(editorPanel)
			.build();
		clientToolbar.addNavigation(navButton);
	}

	@Override
	protected void shutDown()
	{
		mouseManager.unregisterMouseListener(mouseListener);
		overlayManager.remove(overlay);
		ringController.close();
		ringManager.unload();
		if (navButton != null) clientToolbar.removeNavigation(navButton);
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.BANKMAIN)
		{
			bankWasOpen = false;
			pendingReapply = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!pendingReapply) return;
		pendingReapply = false;
		inventorySetupsProvider.reapplyIfNeeded();
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarbitId() != VarbitID.BANK_CURRENTTAB) return;
		if (!bankWasOpen) return;
		if (inventorySetupsProvider.getActiveSetupName() == null) return;

		if (event.getValue() != 0 || !inventorySetupsProvider.isActiveTagCurrent())
		{
			inventorySetupsProvider.clear();
			pendingReapply = false;
		}
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		if (event.getScriptId() != ScriptID.BANKMAIN_FINISHBUILDING) return;

		bankWasOpen = true;

		String setupName = inventorySetupsProvider.getActiveSetupName();
		if (setupName == null) return;

		if (inventorySetupsProvider.isActiveTagCurrent())
		{
			Widget title = client.getWidget(InterfaceID.Bankmain.TITLE);
			if (title != null)
			{
				title.setText("Setup <col=ff0000>" + setupName + "</col>");
			}
		}
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
