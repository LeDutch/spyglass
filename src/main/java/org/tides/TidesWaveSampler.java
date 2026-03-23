package org.tides;

public final class TidesWaveSampler
{
	private static final int SHORE_FADE_WIDTH = 14;

	public int sampleHeight(TidesWaterTile tile, int localX, int localZ, long nowNanos, TidesConfig config)
	{
		double seconds = nowNanos / 1_000_000_000.0;
		double amplitude = config.amplitude();
		double wavelength = Math.max(16.0, config.wavelength());
		double speed = config.speed() / 100.0;

		int clampedX = clamp(localX, 0, TidesPlugin.LOCAL_TILE_SIZE);
		int clampedZ = clamp(localZ, 0, TidesPlugin.LOCAL_TILE_SIZE);
		double worldX = (tile.getWorldPoint().getX() * (double) TidesPlugin.LOCAL_TILE_SIZE) + clampedX;
		double worldZ = (tile.getWorldPoint().getY() * (double) TidesPlugin.LOCAL_TILE_SIZE) + clampedZ;

		double primary = Math.sin((worldX / wavelength) + (seconds * speed));
		double secondary = Math.cos((worldZ / (wavelength * 0.78)) - (seconds * speed * 1.28));
		double diagonal = Math.sin(((worldX + worldZ) / (wavelength * 1.45)) + (seconds * speed * 0.73));
		double combined = (primary * 0.55) + (secondary * 0.3) + (diagonal * 0.15);
		double fade = config.shorelineFade() ? shorelineFade(tile, clampedX, clampedZ) : 1.0;

		return (int) Math.round(combined * amplitude * fade);
	}

	private double shorelineFade(TidesWaterTile tile, int localX, int localZ)
	{
		double fade = 1.0;
		if (tile.hasShore(TidesWaterTile.SHORE_WEST))
		{
			fade *= smooth(localX / (double) SHORE_FADE_WIDTH);
		}
		if (tile.hasShore(TidesWaterTile.SHORE_EAST))
		{
			fade *= smooth((TidesPlugin.LOCAL_TILE_SIZE - localX) / (double) SHORE_FADE_WIDTH);
		}
		if (tile.hasShore(TidesWaterTile.SHORE_SOUTH))
		{
			fade *= smooth(localZ / (double) SHORE_FADE_WIDTH);
		}
		if (tile.hasShore(TidesWaterTile.SHORE_NORTH))
		{
			fade *= smooth((TidesPlugin.LOCAL_TILE_SIZE - localZ) / (double) SHORE_FADE_WIDTH);
		}
		return fade;
	}

	private static double smooth(double value)
	{
		double clamped = Math.max(0.0, Math.min(1.0, value));
		return clamped * clamped * (3.0 - (2.0 * clamped));
	}

	private static int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}
}
