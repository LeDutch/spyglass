package org.tides.water;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import org.tides.TidesPlugin;

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
			seedsByKey.put(TidesPlugin.tileKey(seed.getWorldPoint()), seed);
		}

		List<TidesWaterTile> waterTiles = new ArrayList<>(seeds.size());
		Map<Long, TidesWaterTile> waterTilesByKey = new HashMap<>(seeds.size());
		Map<Long, Integer> shoreDistances = computeShoreDistances(seeds, seedsByKey);
		Map<Long, BodyStats> bodyStatsByKey = computeBodyStats(seeds, seedsByKey, shoreDistances);
		for (TidesPlugin.WaterTileSeed seed : seeds)
		{
			int shorelineMask = 0;
			WorldPoint worldPoint = seed.getWorldPoint();
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

			BodyStats bodyStats = bodyStatsByKey.getOrDefault(TidesPlugin.tileKey(worldPoint), BodyStats.SHALLOW_SINGLE);
			int bodyTileCount = bodyStats.tileCount;
			int shoreDistance = shoreDistances.getOrDefault(TidesPlugin.tileKey(worldPoint), 0);

			TidesWaterTile tile = new TidesWaterTile(
				worldPoint,
				seed.getWaterType(),
				seed.getSurfaceHeight(),
				seed.isPaintBacked(),
				seed.isModelBacked(),
				shorelineMask,
				bodyTileCount,
				computeWaveAmplitudeScale(bodyTileCount, bodyStats.maxShoreDistance),
				computeShoreDepthScale(shoreDistance),
				bodyStats.maxShoreDistance >= 15
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

	private static Map<Long, BodyStats> computeBodyStats(
		List<TidesPlugin.WaterTileSeed> seeds,
		Map<Long, TidesPlugin.WaterTileSeed> seedsByKey,
		Map<Long, Integer> shoreDistances
	)
	{
		Map<Long, BodyStats> bodyStatsByKey = new HashMap<>(seeds.size());
		Set<Long> visited = new HashSet<>(seeds.size());
		for (TidesPlugin.WaterTileSeed seed : seeds)
		{
			long originKey = TidesPlugin.tileKey(seed.getWorldPoint());
			if (!visited.add(originKey))
			{
				continue;
			}

			List<Long> componentKeys = new ArrayList<>();
			int maxShoreDistance = 0;
			ArrayDeque<TidesPlugin.WaterTileSeed> queue = new ArrayDeque<>();
			queue.add(seed);
			while (!queue.isEmpty())
			{
				TidesPlugin.WaterTileSeed current = queue.removeFirst();
				long currentKey = TidesPlugin.tileKey(current.getWorldPoint());
				componentKeys.add(currentKey);
				maxShoreDistance = Math.max(maxShoreDistance, shoreDistances.getOrDefault(currentKey, 0));

				enqueueNeighbor(current.getWorldPoint().dx(-1), current.getWaterType(), seedsByKey, visited, queue);
				enqueueNeighbor(current.getWorldPoint().dx(1), current.getWaterType(), seedsByKey, visited, queue);
				enqueueNeighbor(current.getWorldPoint().dy(-1), current.getWaterType(), seedsByKey, visited, queue);
				enqueueNeighbor(current.getWorldPoint().dy(1), current.getWaterType(), seedsByKey, visited, queue);
			}

			int size = componentKeys.size();
			BodyStats stats = new BodyStats(size, maxShoreDistance);
			for (long key : componentKeys)
			{
				bodyStatsByKey.put(key, stats);
			}
		}

		return bodyStatsByKey;
	}

	private static Map<Long, Integer> computeShoreDistances(
		List<TidesPlugin.WaterTileSeed> seeds,
		Map<Long, TidesPlugin.WaterTileSeed> seedsByKey
	)
	{
		Map<Long, Integer> distances = new HashMap<>(seeds.size());
		ArrayDeque<TidesPlugin.WaterTileSeed> queue = new ArrayDeque<>();
		for (TidesPlugin.WaterTileSeed seed : seeds)
		{
			WorldPoint worldPoint = seed.getWorldPoint();
			if (isShorelineSeed(worldPoint, seedsByKey))
			{
				long key = TidesPlugin.tileKey(worldPoint);
				distances.put(key, 0);
				queue.addLast(seed);
			}
		}

		while (!queue.isEmpty())
		{
			TidesPlugin.WaterTileSeed current = queue.removeFirst();
			long currentKey = TidesPlugin.tileKey(current.getWorldPoint());
			int nextDistance = distances.get(currentKey) + 1;
			enqueueShoreNeighbor(current.getWorldPoint().dx(-1), current.getWaterType(), seedsByKey, distances, queue, nextDistance);
			enqueueShoreNeighbor(current.getWorldPoint().dx(1), current.getWaterType(), seedsByKey, distances, queue, nextDistance);
			enqueueShoreNeighbor(current.getWorldPoint().dy(-1), current.getWaterType(), seedsByKey, distances, queue, nextDistance);
			enqueueShoreNeighbor(current.getWorldPoint().dy(1), current.getWaterType(), seedsByKey, distances, queue, nextDistance);
		}

		for (TidesPlugin.WaterTileSeed seed : seeds)
		{
			distances.putIfAbsent(TidesPlugin.tileKey(seed.getWorldPoint()), 6);
		}
		return distances;
	}

	private static void enqueueNeighbor(
		WorldPoint worldPoint,
		TidesWaterType waterType,
		Map<Long, TidesPlugin.WaterTileSeed> seedsByKey,
		Set<Long> visited,
		ArrayDeque<TidesPlugin.WaterTileSeed> queue
	)
	{
		long key = TidesPlugin.tileKey(worldPoint);
		TidesPlugin.WaterTileSeed neighbor = seedsByKey.get(key);
		if (neighbor == null || neighbor.getWaterType() != waterType || !visited.add(key))
		{
			return;
		}
		queue.addLast(neighbor);
	}

	private static boolean isShorelineSeed(WorldPoint worldPoint, Map<Long, TidesPlugin.WaterTileSeed> seedsByKey)
	{
		return !seedsByKey.containsKey(TidesPlugin.tileKey(worldPoint.dx(-1)))
			|| !seedsByKey.containsKey(TidesPlugin.tileKey(worldPoint.dx(1)))
			|| !seedsByKey.containsKey(TidesPlugin.tileKey(worldPoint.dy(-1)))
			|| !seedsByKey.containsKey(TidesPlugin.tileKey(worldPoint.dy(1)));
	}

	private static void enqueueShoreNeighbor(
		WorldPoint worldPoint,
		TidesWaterType waterType,
		Map<Long, TidesPlugin.WaterTileSeed> seedsByKey,
		Map<Long, Integer> distances,
		ArrayDeque<TidesPlugin.WaterTileSeed> queue,
		int distance
	)
	{
		long key = TidesPlugin.tileKey(worldPoint);
		TidesPlugin.WaterTileSeed neighbor = seedsByKey.get(key);
		if (neighbor == null || neighbor.getWaterType() != waterType || distances.containsKey(key))
		{
			return;
		}

		distances.put(key, distance);
		queue.addLast(neighbor);
	}

	private static float computeWaveAmplitudeScale(int bodyTileCount, int bodyMaxShoreDistance)
	{
		float t = (float) ((Math.log(bodyTileCount) - Math.log(64.0)) / (Math.log(4096.0) - Math.log(64.0)));
		t = Math.max(0.0f, Math.min(1.0f, t));
		t = t * t * (3.0f - (2.0f * t));

		float deepWaterT = Math.max(0.0f, Math.min(1.0f, (bodyMaxShoreDistance - 6.0f) / 9.0f));
		deepWaterT = deepWaterT * deepWaterT * (3.0f - (2.0f * deepWaterT));

		float shallowScale = 0.95f + (t * 0.10f);
		float deepScale = 1.35f + (t * 0.90f);
		return shallowScale + ((deepScale - shallowScale) * deepWaterT);
	}

	private static float computeShoreDepthScale(int shoreDistance)
	{
		return 1.0f;
	}

	private static final class BodyStats
	{
		private static final BodyStats SHALLOW_SINGLE = new BodyStats(1, 0);

		private final int tileCount;
		private final int maxShoreDistance;

		private BodyStats(int tileCount, int maxShoreDistance)
		{
			this.tileCount = tileCount;
			this.maxShoreDistance = maxShoreDistance;
		}
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

	public TidesWaterTile getTile(int worldX, int worldY, int plane)
	{
		return waterTilesByKey.get(TidesPlugin.tileKey(new WorldPoint(worldX, worldY, plane)));
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
