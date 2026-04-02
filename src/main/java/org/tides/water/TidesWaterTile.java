package org.tides.water;

import net.runelite.api.coords.WorldPoint;

public final class TidesWaterTile
{
	public static final int SHORE_WEST = 1;
	public static final int SHORE_EAST = 1 << 1;
	public static final int SHORE_SOUTH = 1 << 2;
	public static final int SHORE_NORTH = 1 << 3;

	private final WorldPoint worldPoint;
	private final TidesWaterType waterType;
	private final int surfaceHeight;
	private final boolean paintBacked;
	private final boolean modelBacked;
	private final int shorelineMask;
	private final int bodyTileCount;
	private final float waveAmplitudeScale;
	private final float shoreDepthScale;
	private final boolean deepOcean;

	public TidesWaterTile(
		WorldPoint worldPoint,
		TidesWaterType waterType,
		int surfaceHeight,
		boolean paintBacked,
		boolean modelBacked,
		int shorelineMask,
		int bodyTileCount,
		float waveAmplitudeScale,
		float shoreDepthScale,
		boolean deepOcean
	)
	{
		this.worldPoint = worldPoint;
		this.waterType = waterType;
		this.surfaceHeight = surfaceHeight;
		this.paintBacked = paintBacked;
		this.modelBacked = modelBacked;
		this.shorelineMask = shorelineMask;
		this.bodyTileCount = bodyTileCount;
		this.waveAmplitudeScale = waveAmplitudeScale;
		this.shoreDepthScale = shoreDepthScale;
		this.deepOcean = deepOcean;
	}

	public WorldPoint getWorldPoint()
	{
		return worldPoint;
	}

	public TidesWaterType getWaterType()
	{
		return waterType;
	}

	public int getSurfaceHeight()
	{
		return surfaceHeight;
	}

	public boolean isPaintBacked()
	{
		return paintBacked;
	}

	public boolean isModelBacked()
	{
		return modelBacked;
	}

	public int getShorelineMask()
	{
		return shorelineMask;
	}

	public boolean isShoreline()
	{
		return shorelineMask != 0;
	}

	public boolean hasShore(int edgeMask)
	{
		return (shorelineMask & edgeMask) != 0;
	}

	public int getBodyTileCount()
	{
		return bodyTileCount;
	}

	public float getWaveAmplitudeScale()
	{
		return waveAmplitudeScale;
	}

	public float getShoreDepthScale()
	{
		return shoreDepthScale;
	}

	public boolean isDeepOcean()
	{
		return deepOcean;
	}
}
