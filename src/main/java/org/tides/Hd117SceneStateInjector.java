package org.tides;

import java.util.Map;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Scene;
import net.runelite.api.coords.WorldPoint;

@Slf4j
@Singleton
public class Hd117SceneStateInjector
{
	private static final int LOCAL_TILE_SIZE = 128;

	private Object lastPatchedContext;
	private int lastPatchedCount;
	private String status = "scene state idle";

	public String getStatus()
	{
		return status;
	}

	public void reset()
	{
		lastPatchedContext = null;
		lastPatchedCount = 0;
		status = "scene state idle";
	}

	public void refresh(Hd117Handle handle, TidesPlugin plugin)
	{
		if (!plugin.getConfig().inject117HdWaterState())
		{
			status = "scene state injection disabled";
			return;
		}

		try
		{
			Object context = resolveSceneContext(handle);
			if (context == null)
			{
				status = "scene context unavailable";
				return;
			}

			if (context == lastPatchedContext && lastPatchedCount == plugin.getSceneSnapshot().size())
			{
				return;
			}

			int patched = patchSceneContext(context, plugin);
			lastPatchedContext = context;
			lastPatchedCount = plugin.getSceneSnapshot().size();
			status = "scene water flags injected: " + patched;
		}
		catch (ReflectiveOperationException ex)
		{
			status = "scene state injection failed";
			log.debug("Unable to inject 117HD scene state", ex);
		}
	}

	private Object resolveSceneContext(Hd117Handle handle) throws ReflectiveOperationException
	{
		Object current = Hd117Reflection.invoke(handle.renderer, "getSceneContext", new Class<?>[0]);
		if (current != null)
		{
			return current;
		}

		if ("LegacyRenderer".equals(handle.rendererName))
		{
			return Hd117Reflection.getField(handle.renderer, "nextSceneContext");
		}

		if ("ZoneRenderer".equals(handle.rendererName))
		{
			Object sceneManager = Hd117Reflection.getField(handle.renderer, "sceneManager");
			Object next = Hd117Reflection.getField(sceneManager, "nextSceneContext");
			return next;
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	private int patchSceneContext(Object sceneContext, TidesPlugin plugin) throws ReflectiveOperationException
	{
		TidesSceneSnapshot snapshot = plugin.getSceneSnapshot();
		Scene scene = (Scene) Hd117Reflection.getField(sceneContext, "scene");
		int sceneOffset = (int) Hd117Reflection.getField(sceneContext, "sceneOffset");
		int[] sceneBase = (int[]) Hd117Reflection.getField(sceneContext, "sceneBase");
		boolean[][][] tileIsWater = (boolean[][][]) Hd117Reflection.getField(sceneContext, "tileIsWater");
		Map<Integer, Boolean> vertexIsWater = (Map<Integer, Boolean>) Hd117Reflection.getField(sceneContext, "vertexIsWater");
		int[][][] underwaterDepthLevels = (int[][][]) Hd117Reflection.getField(sceneContext, "underwaterDepthLevels");

		if (scene == null || tileIsWater == null || vertexIsWater == null)
		{
			return 0;
		}

		int[][][] heights = scene.getTileHeights();
		int patched = 0;
		for (TidesWaterTile tile : plugin.getWaterTiles())
		{
			WorldPoint point = tile.getWorldPoint();
			int plane = sceneBase == null ? point.getPlane() : point.getPlane() - sceneBase[2];
			if (plane < 0 || plane >= tileIsWater.length)
			{
				continue;
			}

			int sceneX = point.getX() - snapshot.getBaseX();
			int sceneY = point.getY() - snapshot.getBaseY();
			int exX = sceneX + sceneOffset;
			int exY = sceneY + sceneOffset;
			if (!inBounds(tileIsWater, plane, exX, exY))
			{
				continue;
			}

			tileIsWater[plane][exX][exY] = true;
			if (underwaterDepthLevels != null && plane < underwaterDepthLevels.length
				&& exX + 1 < underwaterDepthLevels[plane].length
				&& exY + 1 < underwaterDepthLevels[plane][exX].length)
			{
				underwaterDepthLevels[plane][exX][exY] = Math.max(underwaterDepthLevels[plane][exX][exY], 1);
				underwaterDepthLevels[plane][exX + 1][exY] = Math.max(underwaterDepthLevels[plane][exX + 1][exY], 1);
				underwaterDepthLevels[plane][exX][exY + 1] = Math.max(underwaterDepthLevels[plane][exX][exY + 1], 1);
				underwaterDepthLevels[plane][exX + 1][exY + 1] = Math.max(underwaterDepthLevels[plane][exX + 1][exY + 1], 1);
			}

			addTileVertexKeys(vertexIsWater, heights, plane, exX, exY, sceneX, sceneY);
			patched++;
		}

		return patched;
	}

	private static void addTileVertexKeys(
		Map<Integer, Boolean> vertexIsWater,
		int[][][] heights,
		int plane,
		int exX,
		int exY,
		int sceneX,
		int sceneY)
	{
		if (heights == null || plane < 0 || plane >= heights.length
			|| exX + 1 >= heights[plane].length
			|| exY + 1 >= heights[plane][exX].length
			|| exY + 1 >= heights[plane][exX + 1].length)
		{
			return;
		}

		int swX = sceneX * LOCAL_TILE_SIZE;
		int swY = sceneY * LOCAL_TILE_SIZE;
		vertexIsWater.put(fastVertexHash(swX, swY, heights[plane][exX][exY]), true);
		vertexIsWater.put(fastVertexHash((sceneX + 1) * LOCAL_TILE_SIZE, swY, heights[plane][exX + 1][exY]), true);
		vertexIsWater.put(fastVertexHash(swX, (sceneY + 1) * LOCAL_TILE_SIZE, heights[plane][exX][exY + 1]), true);
		vertexIsWater.put(
			fastVertexHash((sceneX + 1) * LOCAL_TILE_SIZE, (sceneY + 1) * LOCAL_TILE_SIZE, heights[plane][exX + 1][exY + 1]),
			true
		);
	}

	private static int fastVertexHash(int x, int y, int z)
	{
		int hash = 0;
		hash = 31 * hash + x;
		hash = 31 * hash + ',';
		hash = 31 * hash + y;
		hash = 31 * hash + ',';
		hash = 31 * hash + z;
		hash = 31 * hash + ',';
		return hash;
	}

	private static boolean inBounds(boolean[][][] array, int plane, int x, int y)
	{
		return plane >= 0
			&& plane < array.length
			&& x >= 0
			&& y >= 0
			&& x < array[plane].length
			&& y < array[plane][x].length;
	}
}
