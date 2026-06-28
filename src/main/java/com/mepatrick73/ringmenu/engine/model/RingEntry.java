package com.mepatrick73.ringmenu.engine.model;

import com.mepatrick73.ringmenu.engine.runtime.RingController;

public interface RingEntry
{
    String getLabel();
    void onSelect(RingController controller);
}
