package org.tides;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PreMapLoad;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Tides",
	description = "Renderer-ready water scene detection and wave sampling",
	tags = {"water", "waves", "renderer", "117hd"}
)
public class TidesPlugin extends Plugin
{
	static final int LOCAL_TILE_SIZE = 128;

	@Inject
	private Client client;

	@Inject
	private TidesConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TidesWaterOverlay waterOverlay;

	@Inject
	private HdRendererBridge hdRendererBridge;

	private final TidesWaveSampler waveSampler = new TidesWaveSampler();
	private Scene lastScene;
	private TidesSceneSnapshot sceneSnapshot = TidesSceneSnapshot.empty();

	@Override
	protected void startUp()
	{
		seedBridgeDefaults();
		log.info("Tides started");
		overlayManager.add(waterOverlay);
		hdRendererBridge.refresh(this);
	}

	@Override
	protected void shutDown()
	{
		log.info("Tides stopped");
		hdRendererBridge.shutDown();
		overlayManager.remove(waterOverlay);
		clearSceneState();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState state = event.getGameState();
		if (state == GameState.LOADING || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			clearSceneState();
		}
	}

	@Subscribe
	public void onPreMapLoad(PreMapLoad event)
	{
		WorldView worldView = event.getWorldView();
		Scene scene = event.getScene();
		if (worldView == null || scene == null)
		{
			clearSceneState();
			return;
		}

		updateSceneSnapshot(worldView, scene);
	}

	@Subscribe
	public void onClientTick(ClientTick event)
	{
		hdRendererBridge.refresh(this);

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			clearSceneState();
			return;
		}

		Scene scene = worldView.getScene();
		if (scene == null)
		{
			clearSceneState();
			return;
		}

		if (scene != lastScene || !sceneSnapshot.matches(worldView))
		{
			updateSceneSnapshot(worldView, scene);
		}
	}

	public TidesConfig getConfig()
	{
		return config;
	}

	public TidesSceneSnapshot getSceneSnapshot()
	{
		return sceneSnapshot;
	}

	public Collection<TidesWaterTile> getWaterTiles()
	{
		return sceneSnapshot.getWaterTiles();
	}

	public boolean shouldDrawDebugOverlay()
	{
		return config.debugOverlay();
	}

	public String getHdBridgeStatus()
	{
		return hdRendererBridge.getStatus();
	}

	public String getHdShaderStatus()
	{
		return hdRendererBridge.getShaderStatus();
	}

	public String getHdSceneInjectionStatus()
	{
		return hdRendererBridge.getSceneInjectionStatus();
	}

	public boolean isHdBridgeHooked()
	{
		return hdRendererBridge.isHooked();
	}

	public int sampleWaveHeight(TidesWaterTile tile, int localX, int localZ, long nowNanos)
	{
		return waveSampler.sampleHeight(tile, localX, localZ, nowNanos, config);
	}

	public LocalPoint worldToLocal(WorldPoint worldPoint)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || worldPoint.getPlane() != worldView.getPlane())
		{
			return null;
		}

		int sceneX = worldPoint.getX() - worldView.getBaseX();
		int sceneY = worldPoint.getY() - worldView.getBaseY();
		if (sceneX < 0 || sceneY < 0 || sceneX >= worldView.getSizeX() || sceneY >= worldView.getSizeY())
		{
			return null;
		}

		return new LocalPoint(
			(sceneX * LOCAL_TILE_SIZE) + Perspective.LOCAL_HALF_TILE_SIZE,
			(sceneY * LOCAL_TILE_SIZE) + Perspective.LOCAL_HALF_TILE_SIZE,
			worldView.getId()
		);
	}

	@Provides
	TidesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TidesConfig.class);
	}

	private void clearSceneState()
	{
		lastScene = null;
		sceneSnapshot = TidesSceneSnapshot.empty();
	}

	private void seedBridgeDefaults()
	{
		seedBooleanConfig("bridge117Hd", true);
		seedBooleanConfig("hotPatch117HdShaders", true);
		seedBooleanConfig("inject117HdWaterState", true);
	}

	private void seedBooleanConfig(String key, boolean value)
	{
		if (configManager.getConfiguration("tides", key) == null)
		{
			configManager.setConfiguration("tides", key, value);
		}
	}

	private void updateSceneSnapshot(WorldView worldView, Scene scene)
	{
		sceneSnapshot = scanScene(worldView, scene);
		lastScene = scene;
		log.debug(
			"Water snapshot updated: worldViewId={} plane={} tiles={}",
			sceneSnapshot.getWorldViewId(),
			sceneSnapshot.getPlane(),
			sceneSnapshot.size()
		);
	}

	private TidesSceneSnapshot scanScene(WorldView worldView, Scene scene)
	{
		Tile[][][] tiles = scene.getTiles();
		int plane = worldView.getPlane();
		if (tiles == null || plane < 0 || plane >= tiles.length)
		{
			return TidesSceneSnapshot.empty();
		}

		List<WaterTileSeed> seeds = new ArrayList<>();
		Tile[][] planeTiles = tiles[plane];
		for (Tile[] row : planeTiles)
		{
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}

				TidesWaterType waterType = detectWaterType(tile);
				if (waterType == TidesWaterType.NONE)
				{
					continue;
				}

				WorldPoint worldPoint = tileWorldPoint(worldView, tile);
				if (worldPoint == null)
				{
					continue;
				}

				seeds.add(new WaterTileSeed(
					worldPoint,
					waterType,
					resolveSurfaceHeight(worldView, tile),
					tile.getSceneTilePaint() != null,
					tile.getSceneTileModel() != null
				));
			}
		}

		return TidesSceneSnapshot.fromSeeds(worldView, seeds);
	}

	private TidesWaterType detectWaterType(Tile tile)
	{
		SceneTilePaint paint = tile.getSceneTilePaint();
		if (paint != null)
		{
			TidesWaterType paintType = TidesWaterType.fromTexture(paint.getTexture());
			if (paintType != TidesWaterType.NONE)
			{
				return paintType;
			}
		}

		SceneTileModel model = tile.getSceneTileModel();
		if (model == null || model.getTriangleTextureId() == null)
		{
			return TidesWaterType.NONE;
		}

		for (int textureId : model.getTriangleTextureId())
		{
			TidesWaterType modelType = TidesWaterType.fromTexture(textureId);
			if (modelType != TidesWaterType.NONE)
			{
				return modelType;
			}
		}

		return TidesWaterType.NONE;
	}

	private int resolveSurfaceHeight(WorldView worldView, Tile tile)
	{
		Point scenePoint = tile.getSceneLocation();
		if (scenePoint == null)
		{
			return 0;
		}

		int[][][] heights = worldView.getScene().getTileHeights();
		int renderLevel = tile.getRenderLevel();
		int sceneX = scenePoint.getX();
		int sceneY = scenePoint.getY();
		if (heights == null
			|| renderLevel < 0
			|| renderLevel >= heights.length
			|| sceneX < 0
			|| sceneY < 0
			|| sceneX + 1 >= heights[renderLevel].length
			|| sceneY + 1 >= heights[renderLevel][sceneX].length
			|| sceneY + 1 >= heights[renderLevel][sceneX + 1].length)
		{
			return 0;
		}

		int sw = heights[renderLevel][sceneX][sceneY];
		int se = heights[renderLevel][sceneX + 1][sceneY];
		int ne = heights[renderLevel][sceneX + 1][sceneY + 1];
		int nw = heights[renderLevel][sceneX][sceneY + 1];
		return (sw + se + ne + nw) / 4;
	}

	private WorldPoint tileWorldPoint(WorldView worldView, Tile tile)
	{
		Point scenePoint = tile.getSceneLocation();
		if (scenePoint == null)
		{
			return null;
		}

		return new WorldPoint(
			worldView.getBaseX() + scenePoint.getX(),
			worldView.getBaseY() + scenePoint.getY(),
			tile.getRenderLevel()
		);
	}

	static long tileKey(WorldPoint worldPoint)
	{
		long x = worldPoint.getX() & 0x3FFFFFFL;
		long y = worldPoint.getY() & 0x3FFFFFFL;
		long plane = worldPoint.getPlane() & 0x3L;
		return x | (y << 26) | (plane << 52);
	}

	void onHdPreSceneDraw(Scene scene)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || scene == null || scene == lastScene && sceneSnapshot.matches(worldView))
		{
			return;
		}

		updateSceneSnapshot(worldView, scene);
	}

	static final class WaterTileSeed
	{
		final WorldPoint worldPoint;
		final TidesWaterType waterType;
		final int surfaceHeight;
		final boolean paintBacked;
		final boolean modelBacked;

		private WaterTileSeed(
			WorldPoint worldPoint,
			TidesWaterType waterType,
			int surfaceHeight,
			boolean paintBacked,
			boolean modelBacked
		)
		{
			this.worldPoint = worldPoint;
			this.waterType = waterType;
			this.surfaceHeight = surfaceHeight;
			this.paintBacked = paintBacked;
			this.modelBacked = modelBacked;
		}
	}
}
