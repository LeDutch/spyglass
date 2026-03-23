package org.tides;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;

public final class TidesSceneSnapshot
{
	private static final TidesSceneSnapshot EMPTY = new TidesSceneSnapshot(-1, 0, 0, -1, Collections.emptyList(), Collections.emptyMap());

	private final int worldViewId;
	private final int baseX;
	private final int baseY;
	private final int plane;
	private final List<TidesWaterTile> waterTiles;
	private final Map<Long, TidesWaterTile> waterTilesByKey;

	private TidesSceneSnapshot(
		int worldViewId,
		int baseX,
		int baseY,
		int plane,
		List<TidesWaterTile> waterTiles,
		Map<Long, TidesWaterTile> waterTilesByKey
	)
	{
		this.worldViewId = worldViewId;
		this.baseX = baseX;
		this.baseY = baseY;
		this.plane = plane;
		this.waterTiles = waterTiles;
		this.waterTilesByKey = waterTilesByKey;
	}

	public static TidesSceneSnapshot empty()
	{
		return EMPTY;
	}

	public static TidesSceneSnapshot fromSeeds(WorldView worldView, List<TidesPlugin.WaterTileSeed> seeds)
	{
		if (worldView == null || seeds.isEmpty())
		{
			return empty();
		}

		Map<Long, TidesPlugin.WaterTileSeed> seedsByKey = new HashMap<>();
		for (TidesPlugin.WaterTileSeed seed : seeds)
		{
			seedsByKey.put(TidesPlugin.tileKey(seed.worldPoint), seed);
		}

		List<TidesWaterTile> waterTiles = new ArrayList<>(seeds.size());
		Map<Long, TidesWaterTile> waterTilesByKey = new HashMap<>(seeds.size());
		for (TidesPlugin.WaterTileSeed seed : seeds)
		{
			int shorelineMask = 0;
			WorldPoint worldPoint = seed.worldPoint;
			if (!seedsByKey.containsKey(TidesPlugin.tileKey(worldPoint.dx(-1))))
			{
				shorelineMask |= TidesWaterTile.SHORE_WEST;
			}
			if (!seedsByKey.containsKey(TidesPlugin.tileKey(worldPoint.dx(1))))
			{
				shorelineMask |= TidesWaterTile.SHORE_EAST;
			}
			if (!seedsByKey.containsKey(TidesPlugin.tileKey(worldPoint.dy(-1))))
			{
				shorelineMask |= TidesWaterTile.SHORE_SOUTH;
			}
			if (!seedsByKey.containsKey(TidesPlugin.tileKey(worldPoint.dy(1))))
			{
				shorelineMask |= TidesWaterTile.SHORE_NORTH;
			}

			TidesWaterTile tile = new TidesWaterTile(
				worldPoint,
				seed.waterType,
				seed.surfaceHeight,
				seed.paintBacked,
				seed.modelBacked,
				shorelineMask
			);
			long key = TidesPlugin.tileKey(worldPoint);
			waterTiles.add(tile);
			waterTilesByKey.put(key, tile);
		}

		return new TidesSceneSnapshot(
			worldView.getId(),
			worldView.getBaseX(),
			worldView.getBaseY(),
			worldView.getPlane(),
			Collections.unmodifiableList(waterTiles),
			Collections.unmodifiableMap(waterTilesByKey)
		);
	}

	public int getWorldViewId()
	{
		return worldViewId;
	}

	public int getBaseX()
	{
		return baseX;
	}

	public int getBaseY()
	{
		return baseY;
	}

	public int getPlane()
	{
		return plane;
	}

	public Collection<TidesWaterTile> getWaterTiles()
	{
		return waterTiles;
	}

	public TidesWaterTile getTile(WorldPoint worldPoint)
	{
		return waterTilesByKey.get(TidesPlugin.tileKey(worldPoint));
	}

	public boolean isWater(WorldPoint worldPoint)
	{
		return waterTilesByKey.containsKey(TidesPlugin.tileKey(worldPoint));
	}

	public boolean isEmpty()
	{
		return waterTiles.isEmpty();
	}

	public int size()
	{
		return waterTiles.size();
	}

	public boolean matches(WorldView worldView)
	{
		return worldView != null
			&& worldViewId == worldView.getId()
			&& baseX == worldView.getBaseX()
			&& baseY == worldView.getBaseY()
			&& plane == worldView.getPlane();
	}
}
