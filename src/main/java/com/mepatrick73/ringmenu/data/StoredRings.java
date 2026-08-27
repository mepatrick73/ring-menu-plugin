package com.mepatrick73.ringmenu.data;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Versioned envelope for the saved ring list (config {@code ringmenu}/{@code rings}).
 *
 * Version 1 is the first explicit version. Anything written before it is a bare JSON array of
 * {@link RingDefinition} with no envelope; {@code RingManager} detects that shape, loads it as
 * version 0 and rewrites it in this form on the next save.
 */
@AllArgsConstructor
@NoArgsConstructor
@Getter
public class StoredRings
{
	private int version;
	private List<RingDefinition> rings;
}
