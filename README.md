# Ring Menu

A radial hotkey menu for Old School RuneScape that lets you open any bank tag or inventory setup with a single keypress.

![Ring Menu in action](hero.gif)

---

## Overview

Ring Menu lets you bind any number of radial menus to hotkeys. Each ring holds entries — bank tags, inventory setups, or sub-rings that lead to another ring. Press the hotkey, hover the slice you want, and click. The bank opens with your layout applied instantly.

**Requires:** [Bank Tags](https://github.com/runelite/runelite/wiki/Bank-Tags) · [Inventory Setups](https://runelite.net/plugin-hub/show/inventory-setups)

---

## Features

- Bind multiple rings to different hotkeys
- Mix bank tags and inventory setups in the same ring
- Nest rings inside rings with sub-rings
- Search and filter your setups directly in the panel
- Drag and drop to reorder entries
- Rename rings and sub-rings in place

---

## Setup

### Creating a ring

Open the Ring Menu panel from the sidebar and click **+ New Ring**.

![Ring list](ring-list.png)

Give the ring a name, assign a hotkey, then open it to start adding entries.

### Adding entries

Inside a ring's detail view, use the provider picker at the bottom to browse your bank tags and inventory setups. Type in the filter box to narrow results, then click **+** to add an entry to the ring.

![Adding an entry](add-entry.png)

Click **Save** when you're done.

### Sub-rings

Click **+ Add Sub-Ring** to create a nested ring. Sub-rings appear as slices in the parent ring and open their own ring when selected. Use the back arrow in the center of the overlay to return to the parent.

![Sub-ring navigation](sub-ring-nav.gif)

### The overlay

Press your hotkey anywhere in-game. The ring appears centered on screen — hover a slice to highlight it, click to activate. Right-click or click outside the ring to dismiss without selecting anything.

![Ring detail and overlay](ring-detail.png)

---

## Usage tips

- **Dismiss without acting** — right-click or click anywhere outside the ring
- **Navigate back** — click the center button (shows ‹) when inside a sub-ring
- **Reorder entries** — drag the grip handle on the left of any entry in the detail view
- **Rename** — click the ✎ button on any ring row or sub-ring entry

---

## Requirements

Ring Menu depends on the **Bank Tags** and **Inventory Setups** plugins. Both must be installed and enabled. Bank Tags ships with the default RuneLite client; Inventory Setups is available on the Plugin Hub.

---

## Future

Currently Ring Menu supports bank tags and inventory setups. Other integrations could be added if they make sense — open an issue if you have an idea.
