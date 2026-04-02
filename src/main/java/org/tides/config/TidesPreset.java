package org.tides.config;

public enum TidesPreset
{
	CUSTOM,
	DEEP_OCEAN;

	@Override
	public String toString()
	{
		switch (this)
		{
			case DEEP_OCEAN:
				return "Deep Ocean";
			case CUSTOM:
			default:
				return "Custom";
		}
	}
}
