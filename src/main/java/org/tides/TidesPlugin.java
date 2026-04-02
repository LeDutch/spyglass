package org.tides;

import com.google.inject.Provides;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.awt.event.KeyEvent;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ClientTick;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.PreMapLoad;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import org.tides.buoyancy.TidesBuoyancyController;
import org.tides.config.TidesConfig;
import org.tides.config.TidesPreset;
import org.tides.hd.HdRendererBridge;
import org.tides.water.TidesSceneSnapshot;
import org.tides.water.TidesWaterOverlay;
import org.tides.water.TidesWaterTile;
import org.tides.water.TidesWaterType;
import org.tides.water.fft.TidesFftProfiles;

@Slf4j
@PluginDescriptor(
	name = "Tides",
	description = "Renderer-ready water scene detection and wave sampling",
	tags = {"water", "waves", "renderer", "117hd"}
)
public class TidesPlugin extends Plugin
{
	public static final int LOCAL_TILE_SIZE = 128;

	@Inject
	private Client client;

	@Inject
	private TidesConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ConfigManager configManager;

	@Inject
	private TidesWaterOverlay waterOverlay;

	@Inject
	private HdRendererBridge hdRendererBridge;

	@Inject
	private KeyManager keyManager;

	@Inject
	private TidesBuoyancyController buoyancyController;

	private Scene lastScene;
	private TidesSceneSnapshot sceneSnapshot = TidesSceneSnapshot.empty();
	private boolean applyingPreset;
	private final KeyListener selectionKeyListener = new KeyListener()
	{
		@Override
		public void keyPressed(KeyEvent e)
		{
			if (config.selectBuoyantObjectHotkey().matches(e))
			{
				buoyancyController.requestSelectionToggle();
				e.consume();
			}
		}

		@Override
		public void keyReleased(KeyEvent e)
		{
		}

		@Override
		public void keyTyped(KeyEvent e)
		{
		}
	};

	@Override
	protected void startUp()
	{
		log.info("Tides started");
		applyPresetIfNeeded(config.preset());
		overlayManager.add(waterOverlay);
		keyManager.registerKeyListener(selectionKeyListener);
	}

	@Override
	protected void shutDown()
	{
		log.info("Tides stopped");
		keyManager.unregisterKeyListener(selectionKeyListener);
		hdRendererBridge.shutDown();
		overlayManager.remove(waterOverlay);
		buoyancyController.clearSelection();
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
		buoyancyController.processPendingSelection();
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

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (!"tides".equals(event.getGroup()) || applyingPreset)
		{
			return;
		}

		if ("preset".equals(event.getKey()))
		{
			applyPresetIfNeeded(config.preset());
		}
	}

	public TidesConfig getConfig()
	{
		return config;
	}

	public Client getClient()
	{
		return client;
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

	public TidesBuoyancyController.RenderedObjectCandidate getSelectedRenderedObject()
	{
		return buoyancyController.getSelectedCandidate();
	}

	public TidesBuoyancyController.SelectedOverlayFootprint getSelectedOverlayFootprint()
	{
		return buoyancyController.getSelectedOverlayFootprint();
	}

	public void onHdFrameStart()
	{
		buoyancyController.beginFrame();
	}

	public void onHdFrameEnd()
	{
		buoyancyController.endFrame();
	}

	public void recordRenderedObject(TileObject tileObject, int orientation, int x, int y, int z)
	{
		buoyancyController.recordCandidate(tileObject, orientation, x, y, z);
	}

	public TidesBuoyancyController.BuoyancyTransform computeBuoyancyTransform(TileObject tileObject, int orientation, int x, int z)
	{
		return buoyancyController.computeTransform(this, tileObject, orientation, x, z);
	}

	public TidesBuoyancyController.BuoyancyTransform computeRenderableBuoyancyTransform(long hash, int orientation, int x, int z)
	{
		return buoyancyController.computeTransformForHash(this, hash, orientation, x, z);
	}

	public void drawBuoyantModel(Model model, TidesBuoyancyController.BuoyancyTransform transform, Runnable drawAction)
	{
		buoyancyController.drawBuoyantModel(model, transform, drawAction);
	}

	public void debugSelectedBuoyancyProximity(String source, net.runelite.api.Renderable renderable, long hash, int x, int z)
	{
		buoyancyController.debugProximityToSelectedWorldView(source, renderable, hash, x, z);
	}

	public LocalPoint worldToLocal(WorldPoint worldPoint)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}
		return LocalPoint.fromWorld(worldView, worldPoint);
	}

	@Provides
	TidesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TidesConfig.class);
	}

	private void applyPresetIfNeeded(TidesPreset preset)
	{
		if (preset == TidesPreset.CUSTOM)
		{
			return;
		}

		applyingPreset = true;
		try
		{
			TidesFftProfiles.applyPresetToConfig(preset, configManager);
		}
		finally
		{
			applyingPreset = false;
		}
	}

	private void clearSceneState()
	{
		lastScene = null;
		sceneSnapshot = TidesSceneSnapshot.empty();
	}

	private void updateSceneSnapshot(WorldView worldView, Scene scene)
	{
		sceneSnapshot = scanScene(worldView, scene);
		lastScene = scene;
	}

	private TidesSceneSnapshot scanScene(WorldView worldView, Scene scene)
	{
		Tile[][][] tiles = scene.getTiles();
		int plane = worldView.getPlane();
		if (tiles == null || worldView.getId() < 0 || plane < 0 || plane >= tiles.length || worldView.getSizeX() <= 0 || worldView.getSizeY() <= 0)
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

	public static long tileKey(WorldPoint worldPoint)
	{
		long x = worldPoint.getX() & 0x3FFFFFFL;
		long y = worldPoint.getY() & 0x3FFFFFFL;
		long plane = worldPoint.getPlane() & 0x3L;
		return x | (y << 26) | (plane << 52);
	}

	public void onHdPreSceneDraw(Scene scene)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || scene == null || scene == lastScene && sceneSnapshot.matches(worldView))
		{
			return;
		}

		updateSceneSnapshot(worldView, scene);
	}

	public static final class WaterTileSeed
	{
		private final WorldPoint worldPoint;
		private final TidesWaterType waterType;
		private final int surfaceHeight;
		private final boolean paintBacked;
		private final boolean modelBacked;

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
	}
}
