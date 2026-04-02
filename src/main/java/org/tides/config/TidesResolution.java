package org.tides.config;

public enum TidesResolution
{
	FFT_64(64),
	FFT_128(128),
	FFT_256(256);

	private final int size;

	TidesResolution(int size)
	{
		this.size = size;
	}

	public int size()
	{
		return size;
	}
}
