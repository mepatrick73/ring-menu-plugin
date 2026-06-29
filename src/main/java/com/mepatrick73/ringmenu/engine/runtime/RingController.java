package com.mepatrick73.ringmenu.engine.runtime;

import com.mepatrick73.ringmenu.engine.model.RingEntry;
import com.mepatrick73.ringmenu.engine.model.RingNode;

import javax.inject.Singleton;
import java.util.ArrayDeque;
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

    public synchronized boolean isOpen()
    {
        return !stack.isEmpty();
    }

    public synchronized boolean canGoBack()
    {
        return stack.size() > 1;
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
