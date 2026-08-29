package com.mepatrick73.ringmenu.engine.runtime;

import com.mepatrick73.ringmenu.engine.model.RingEntry;
import com.mepatrick73.ringmenu.engine.model.RingNode;

import javax.inject.Singleton;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

@Singleton
public class RingController
{
	// Mutated on the AWT EDT (mouse listener, hotkey listener) and read on the client thread
	// in render(). All public methods are synchronized to prevent data races.
	private final Deque<RingNode> stack = new ArrayDeque<>();

	public synchronized void open(RingNode root)
	{
		stack.clear();
		stack.push(root);
	}

	public synchronized void pushRing(RingNode node)
	{
		stack.push(node);
	}

	public synchronized void back()
	{
		if (stack.size() > 1)
		{
			stack.pop();
		}
	}

	public synchronized void close()
	{
		stack.clear();
	}

	// Swaps in a freshly built tree while the ring is open, used for live updates from the editor.
	// Re-descends to the level the user was viewing by matching sub-ring labels; a level that no
	// longer exists (renamed, deleted, toggled off) drops the user at the deepest surviving one.
	public synchronized void replaceRoot(RingNode newRoot)
	{
		if (stack.isEmpty())
		{
			return;
		}
		List<RingNode> oldPath = new ArrayList<>(stack);
		Collections.reverse(oldPath); // deque iterates top-first; we want root → current
		stack.clear();
		stack.push(newRoot);
		RingNode cur = newRoot;
		for (int i = 1; i < oldPath.size(); i++)
		{
			// Labels aren't unique among siblings, so match by (label, occurrence rank): the level
			// the user was in keeps its place among equally-labeled siblings in the rebuilt tree,
			// rather than always resolving to the first one.
			RingNode oldNode = oldPath.get(i);
			RingNode next = nthLabeled(cur, oldNode.getLabel(),
				occurrenceRank(oldPath.get(i - 1), oldNode));
			if (next == null)
			{
				break;
			}
			stack.push(next);
			cur = next;
		}
	}

	// How many sub-rings with the same label precede node among parent's children.
	private static int occurrenceRank(RingNode parent, RingNode node)
	{
		int rank = 0;
		for (RingEntry child : parent.getChildren())
		{
			if (child == node)
			{
				break;
			}
			if (child instanceof RingNode && child.getLabel().equals(node.getLabel()))
			{
				rank++;
			}
		}
		return rank;
	}

	// The rank-th sub-ring child with the given label, or null when fewer exist.
	private static RingNode nthLabeled(RingNode parent, String label, int rank)
	{
		int seen = 0;
		for (RingEntry child : parent.getChildren())
		{
			if (child instanceof RingNode && child.getLabel().equals(label))
			{
				if (seen == rank)
				{
					return (RingNode) child;
				}
				seen++;
			}
		}
		return null;
	}

	public synchronized boolean isOpen()
	{
		return !stack.isEmpty();
	}

	public synchronized boolean canGoBack()
	{
		return stack.size() > 1;
	}

	// The ring level currently on screen, or null when closed. The overlay uses its identity to
	// cache per-label layout, and its orientation to pick the label style.
	public synchronized RingNode currentNode()
	{
		return stack.peek();
	}

	public synchronized List<RingEntry> currentEntries()
	{
		if (stack.isEmpty())
		{
			return Collections.emptyList();
		}
		return Collections.unmodifiableList(stack.peek().getChildren());
	}
}
