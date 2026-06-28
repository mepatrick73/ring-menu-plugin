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
    private final Deque<RingNode> stack = new ArrayDeque<>();

    public void open(RingNode root)
    {
        stack.clear();
        stack.push(root);
    }

    public void pushRing(RingNode node)
    {
        stack.push(node);
    }

    public void back()
    {
        if (stack.size() > 1)
        {
            stack.pop();
        }
    }

    public void close()
    {
        stack.clear();
    }

    public boolean isOpen()
    {
        return !stack.isEmpty();
    }

    public boolean canGoBack()
    {
        return stack.size() > 1;
    }

    public List<RingEntry> currentEntries()
    {
        if (stack.isEmpty())
        {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(stack.peek().getChildren());
    }
}
