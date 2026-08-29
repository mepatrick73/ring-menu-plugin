package com.mepatrick73.ringmenu.data;

/**
 * How a ring draws its slice labels: horizontally, or rotated to run along the radius
 * (inside → outside). Radial keeps long labels readable on rings with many slices,
 * where the horizontal room per slice shrinks with the entry count.
 */
public enum TextOrientation
{
	HORIZONTAL,
	RADIAL;

	// The single home of the storage rule: HORIZONTAL is the default and is stored as null, so old
	// JSON (which has no such field) and default-valued fields round-trip identically.
	public static TextOrientation orDefault(TextOrientation orientation)
	{
		return orientation == null ? HORIZONTAL : orientation;
	}

	public static TextOrientation storedForm(TextOrientation orientation)
	{
		return orientation == HORIZONTAL ? null : orientation;
	}
}
