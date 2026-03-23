package org.tides;

import java.awt.Color;

public enum TidesWaterType
{
	NONE(new Color(0, 0, 0)),
	WATER(new Color(70, 140, 230)),
	SWAMP_WATER(new Color(78, 120, 70)),
	SAILING_WATER(new Color(110, 145, 200)),
	DEEP_WATER(new Color(55, 80, 145));

	private final Color debugColor;

	TidesWaterType(Color debugColor)
	{
		this.debugColor = debugColor;
	}

	public Color getDebugColor()
	{
		return debugColor;
	}

	public static TidesWaterType fromTexture(int textureId)
	{
		switch (textureId)
		{
			case 1:
			case 24:
				return WATER;
			case 25:
				return SWAMP_WATER;
			case 208:
				return DEEP_WATER;
			default:
				if (130 <= textureId && textureId <= 189)
				{
					return SAILING_WATER;
				}
				return NONE;
		}
	}
}
