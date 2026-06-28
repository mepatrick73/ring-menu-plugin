package com.mepatrick73.ringmenu.engine.model;

import com.mepatrick73.ringmenu.engine.runtime.RingController;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@RequiredArgsConstructor
public class RingNode implements RingEntry
{
    @Getter
    private final String label;

    @Getter
    private final List<RingEntry> children = new ArrayList<>();

    @Override
    public void onSelect(RingController controller)
    {
        controller.pushRing(this);
    }
}
