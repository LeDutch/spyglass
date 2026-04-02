package org.tides.buoyancy;

import java.awt.Shape;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.MenuEntry;
import net.runelite.api.Model;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldEntityConfig;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.config.ConfigManager;
import org.tides.TidesPlugin;
import org.tides.water.TidesSceneSnapshot;
import org.tides.water.TidesWaterTile;
import org.tides.water.TidesWaterType;
import org.tides.water.fft.TidesFftProfile;
import org.tides.water.fft.TidesFftProfiles;
import org.tides.water.fft.TidesSpectrumParameters;

@Slf4j
@Singleton
public class TidesBuoyancyController
{
	private static final long NO_SELECTION = Long.MIN_VALUE;
	private static final float SAMPLE_INSET = 0.82f;
	private static final float MAX_PITCH_ROLL_RADIANS = (float) Math.toRadians(18.0);
	private static final float[] BAND_TIME_SCALES = {2.2f, 2.5f};
	private static final int HASH_TYPE_SHIFT = 16;
	private static final int HASH_TYPE_MASK = 0x7;
	private static final int HASH_TYPE_WORLD_ENTITY = 4;
	private static final int DEBUG_PROXIMITY_MARGIN_TILES = 3;
	private static final String CONFIG_GROUP = "tides";
	private static final String CONFIG_PROXY_OBJECT_ID = "persistedBoatProxyObjectId";
	private static final String CONFIG_PROXY_DELTA_X = "persistedBoatProxyDeltaX";
	private static final String CONFIG_PROXY_DELTA_Z = "persistedBoatProxyDeltaZ";

	private final Client client;
	private final ConfigManager configManager;

	private final Object mutex = new Object();
	private final List<RenderedObjectCandidate> frameCandidates = new ArrayList<>();

	private volatile List<RenderedObjectCandidate> latestCandidates = Collections.emptyList();
	private volatile RenderedObjectCandidate selectedCandidate;
	private volatile long selectedKey = NO_SELECTION;
	private volatile long selectedHash = 0L;
	private volatile int selectedSizeX = 1;
	private volatile int selectedSizeY = 1;
	private volatile WorldViewSelection selectedWorldViewSelection;
	private volatile boolean selectionToggleRequested;
	private volatile int debugRenderHitLogsRemaining;
	private volatile int debugProximityLogsRemaining;
	private volatile long selectedWorldViewProxyHash;
	private volatile int selectedWorldViewProxyId;
	private volatile int selectedWorldViewProxyLocalX = Integer.MIN_VALUE;
	private volatile int selectedWorldViewProxyLocalZ = Integer.MIN_VALUE;
	private volatile int persistedProxyObjectId;
	private volatile int persistedProxyDeltaX;
	private volatile int persistedProxyDeltaZ;

	private int frameSelectionRenderHits;
	private int frameWorldEntityRenderHits;
	private int noRenderHitFrames;
	private long candidateProxyHash;
	private int candidateProxyId;
	private int candidateProxyHits;

	@Inject
	public TidesBuoyancyController(Client client, ConfigManager configManager)
	{
		this.client = client;
		this.configManager = configManager;
		this.persistedProxyObjectId = configManager.getConfiguration(CONFIG_GROUP, CONFIG_PROXY_OBJECT_ID, Integer.class) == null
			? -1
			: configManager.getConfiguration(CONFIG_GROUP, CONFIG_PROXY_OBJECT_ID, Integer.class);
		this.persistedProxyDeltaX = configManager.getConfiguration(CONFIG_GROUP, CONFIG_PROXY_DELTA_X, Integer.class) == null
			? Integer.MIN_VALUE
			: configManager.getConfiguration(CONFIG_GROUP, CONFIG_PROXY_DELTA_X, Integer.class);
		this.persistedProxyDeltaZ = configManager.getConfiguration(CONFIG_GROUP, CONFIG_PROXY_DELTA_Z, Integer.class) == null
			? Integer.MIN_VALUE
			: configManager.getConfiguration(CONFIG_GROUP, CONFIG_PROXY_DELTA_Z, Integer.class);
	}

	public void beginFrame()
	{
		synchronized (mutex)
		{
			frameCandidates.clear();
		}

		frameSelectionRenderHits = 0;
		frameWorldEntityRenderHits = 0;
	}

	public void endFrame()
	{
		List<RenderedObjectCandidate> candidates;
		synchronized (mutex)
		{
			candidates = new ArrayList<>(frameCandidates);
			frameCandidates.clear();
		}

		latestCandidates = Collections.unmodifiableList(candidates);
		if (selectedKey == NO_SELECTION && selectedWorldViewSelection == null)
		{
			selectedCandidate = null;
			return;
		}

		if (selectedKey == NO_SELECTION)
		{
			selectedCandidate = selectedWorldViewProxyHash == 0L ? null : findCandidateByHash(selectedWorldViewProxyHash, candidates);
		}
		else
		{
			selectedCandidate = findCandidate(selectedKey, candidates);
		}
		if (selectedWorldViewSelection != null)
		{
			if (frameSelectionRenderHits == 0)
			{
				noRenderHitFrames++;
				if (noRenderHitFrames == 1 || noRenderHitFrames % 120 == 0)
				{
					log.info(
						"Buoyancy debug: selected worldview id={} had no render hits this frame",
						selectedWorldViewSelection.worldViewId
					);
				}
			}
			else
			{
				noRenderHitFrames = 0;
			}
		}
	}

	public void recordCandidate(TileObject tileObject, int orientation, int x, int y, int z)
	{
		if (tileObject == null)
		{
			return;
		}

		Shape clickbox = tileObject.getClickbox();
		if (clickbox == null)
		{
			return;
		}

		int sizeX = resolveSizeX(tileObject);
		int sizeY = resolveSizeY(tileObject);
		RenderedObjectCandidate candidate = new RenderedObjectCandidate(
			buildSelectionKey(tileObject, x, z),
			tileObject.getId(),
			tileObject.getHash(),
			tileObject.getPlane(),
			orientation,
			x,
			y,
			z,
			sizeX,
			sizeY,
			clickbox
		);

		synchronized (mutex)
		{
			frameCandidates.add(candidate);
		}
	}

	public void requestSelectionToggle()
	{
		selectionToggleRequested = true;
	}

	public void clearSelection()
	{
		selectedKey = NO_SELECTION;
		selectedHash = 0L;
		selectedSizeX = 1;
		selectedSizeY = 1;
		selectedCandidate = null;
		selectedWorldViewSelection = null;
		debugRenderHitLogsRemaining = 0;
		debugProximityLogsRemaining = 0;
		selectedWorldViewProxyHash = 0L;
		selectedWorldViewProxyId = -1;
		selectedWorldViewProxyLocalX = Integer.MIN_VALUE;
		selectedWorldViewProxyLocalZ = Integer.MIN_VALUE;
		candidateProxyHash = 0L;
		candidateProxyId = -1;
		candidateProxyHits = 0;
		frameSelectionRenderHits = 0;
		frameWorldEntityRenderHits = 0;
		noRenderHitFrames = 0;
	}

	public void processPendingSelection()
	{
		if (!selectionToggleRequested)
		{
			return;
		}

		selectionToggleRequested = false;
		Point mouse = client.getMouseCanvasPosition();
		if (mouse == null || mouse.getX() < 0 || mouse.getY() < 0)
		{
			return;
		}

		dumpHoveredDebug(mouse);

		RenderedObjectCandidate hovered = findHoveredCandidate(mouse);
		if (hovered == null)
		{
			WorldViewSelection worldViewSelection = findMenuWorldViewSelection();
			if (worldViewSelection != null)
			{
				selectWorldViewSelection(worldViewSelection);
				return;
			}

			WorldViewSelection ownedWorldViewSelection = findOwnedWorldViewSelection();
			if (ownedWorldViewSelection != null)
			{
				selectWorldViewSelection(ownedWorldViewSelection);
				return;
			}

			TileObject hoveredTileObject = findHoveredTileObject();
			if (hoveredTileObject == null)
			{
				clearSelection();
				log.info("Cleared buoyant object selection");
				return;
			}

			selectTileObject(hoveredTileObject);
			return;
		}

		if (selectedKey == hovered.getSelectionKey())
		{
			clearSelection();
			log.info("Cleared buoyant object selection");
			return;
		}

		selectedWorldViewSelection = null;
		selectedKey = hovered.getSelectionKey();
		selectedHash = hovered.getHash();
		selectedSizeX = hovered.getSizeX();
		selectedSizeY = hovered.getSizeY();
		selectedCandidate = hovered;
		log.info(
			"Selected buoyant object id={} hash={} size={}x{} plane={} local=({}, {})",
			hovered.getId(),
			Long.toUnsignedString(hovered.getHash()),
			hovered.getSizeX(),
			hovered.getSizeY(),
			hovered.getPlane(),
			hovered.getX(),
			hovered.getZ()
		);
	}

	public RenderedObjectCandidate getSelectedCandidate()
	{
		return selectedCandidate;
	}

	public SelectedOverlayFootprint getSelectedOverlayFootprint()
	{
		WorldViewSelection selection = selectedWorldViewSelection;
		if (selection == null)
		{
			return null;
		}

		int localX = selectedWorldViewProxyLocalX;
		int localZ = selectedWorldViewProxyLocalZ;
		if (localX != Integer.MIN_VALUE && localZ != Integer.MIN_VALUE)
		{
			return new SelectedOverlayFootprint(localX, localZ, 1, 1, true);
		}

		LocalPoint anchor = selection.anchorLocalPoint;
		if (anchor == null)
		{
			return null;
		}

		return new SelectedOverlayFootprint(anchor.getX(), anchor.getY(), selection.sizeX, selection.sizeY, false);
	}

	public BuoyancyTransform computeTransform(
		TidesPlugin plugin,
		TileObject tileObject,
		int orientation,
		int x,
		int z)
	{
		WorldViewSelection worldViewSelection = selectedWorldViewSelection;
		if (worldViewSelection != null)
		{
			BuoyancyTransform transform = computeSelectedWorldViewEntityTransform(plugin, worldViewSelection, tileObject.getHash(), orientation, x, z);
			if (transform != null)
			{
				noteSelectedWorldViewRenderHit(tileObject.getHash(), "tileObject", x, z);
			}
			return transform;
		}

		if (tileObject.getHash() != selectedHash)
		{
			return null;
		}

		return computeTransform(plugin, orientation, x, z, tileObject.getPlane(), resolveSizeX(tileObject), resolveSizeY(tileObject));
	}

	public BuoyancyTransform computeTransformForHash(TidesPlugin plugin, long hash, int orientation, int x, int z)
	{
		WorldViewSelection worldViewSelection = selectedWorldViewSelection;
		if (worldViewSelection != null)
		{
			BuoyancyTransform transform = computeSelectedWorldViewEntityTransform(plugin, worldViewSelection, hash, orientation, x, z);
			if (transform != null)
			{
				int hashType = (int) ((hash >> HASH_TYPE_SHIFT) & HASH_TYPE_MASK);
				String source = hashType == HASH_TYPE_WORLD_ENTITY ? "renderable(worldEntity)" : "renderable";
				noteSelectedWorldViewRenderHit(hash, source, x, z);
			}
			return transform;
		}

		if (hash == -1L || hash != selectedHash)
		{
			return null;
		}

		int plane = (int) ((hash >> TileObject.HASH_PLANE_SHIFT) & 3L);
		return computeTransform(plugin, orientation, x, z, plane, selectedSizeX, selectedSizeY);
	}

	private BuoyancyTransform computeSelectedWorldViewEntityTransform(
		TidesPlugin plugin,
		WorldViewSelection selection,
		long hash,
		int orientation,
		int x,
		int z)
	{
		if (hash == -1L || selectedWorldViewProxyHash == 0L || hash != selectedWorldViewProxyHash)
		{
			return null;
		}

		return computeTransform(
			plugin,
			orientation,
			x,
			z,
			-1,
			selection.sizeX,
			selection.sizeY
		);
	}

	private BuoyancyTransform computeTransform(
		TidesPlugin plugin,
		int orientation,
		int x,
		int z,
		int plane,
		int worldWidthTiles,
		int worldLengthTiles)
	{
		TidesSceneSnapshot snapshot = plugin.getSceneSnapshot();
		if (snapshot.isEmpty())
		{
			return null;
		}

		plane = plane >= 0 ? plane : snapshot.getPlane();
		TidesFftProfile profile = TidesFftProfiles.fromConfig(plugin.getConfig());

		float footprintWidth = Math.max(1.0f, Math.min(worldWidthTiles, worldLengthTiles)) * TidesPlugin.LOCAL_TILE_SIZE * 0.5f * SAMPLE_INSET;
		float footprintLength = Math.max(1.0f, Math.max(worldWidthTiles, worldLengthTiles)) * TidesPlugin.LOCAL_TILE_SIZE * 0.5f * SAMPLE_INSET;

		float yaw = orientationToRadians(orientation);
		float forwardX = (float) Math.sin(yaw);
		float forwardZ = (float) Math.cos(yaw);
		float rightX = forwardZ;
		float rightZ = -forwardX;

		float center = sampleWaveHeight(plugin, snapshot, profile, x, z, plane);
		float front = sampleWaveHeight(plugin, snapshot, profile, x + (forwardX * footprintLength), z + (forwardZ * footprintLength), plane);
		float back = sampleWaveHeight(plugin, snapshot, profile, x - (forwardX * footprintLength), z - (forwardZ * footprintLength), plane);
		float right = sampleWaveHeight(plugin, snapshot, profile, x + (rightX * footprintWidth), z + (rightZ * footprintWidth), plane);
		float left = sampleWaveHeight(plugin, snapshot, profile, x - (rightX * footprintWidth), z - (rightZ * footprintWidth), plane);
		if (Float.isNaN(center) || Float.isNaN(front) || Float.isNaN(back) || Float.isNaN(right) || Float.isNaN(left))
		{
			return null;
		}

		float frontRight = sampleWaveHeight(
			plugin,
			snapshot,
			profile,
			x + (forwardX * footprintLength) + (rightX * footprintWidth),
			z + (forwardZ * footprintLength) + (rightZ * footprintWidth),
			plane
		);
		float frontLeft = sampleWaveHeight(
			plugin,
			snapshot,
			profile,
			x + (forwardX * footprintLength) - (rightX * footprintWidth),
			z + (forwardZ * footprintLength) - (rightZ * footprintWidth),
			plane
		);
		float backRight = sampleWaveHeight(
			plugin,
			snapshot,
			profile,
			x - (forwardX * footprintLength) + (rightX * footprintWidth),
			z - (forwardZ * footprintLength) + (rightZ * footprintWidth),
			plane
		);
		float backLeft = sampleWaveHeight(
			plugin,
			snapshot,
			profile,
			x - (forwardX * footprintLength) - (rightX * footprintWidth),
			z - (forwardZ * footprintLength) - (rightZ * footprintWidth),
			plane
		);
		if (Float.isNaN(frontRight) || Float.isNaN(frontLeft) || Float.isNaN(backRight) || Float.isNaN(backLeft))
		{
			return null;
		}

		float heave = (center + front + back + right + left + frontRight + frontLeft + backRight + backLeft) / 9.0f;
		float pitch = clamp((float) Math.atan2(front - back, Math.max(footprintLength * 2.0f, 1.0f)), -MAX_PITCH_ROLL_RADIANS, MAX_PITCH_ROLL_RADIANS);
		float roll = clamp((float) Math.atan2(right - left, Math.max(footprintWidth * 2.0f, 1.0f)), -MAX_PITCH_ROLL_RADIANS, MAX_PITCH_ROLL_RADIANS);
		return new BuoyancyTransform(Math.round(heave), pitch, -roll);
	}

	public void drawBuoyantModel(Model model, BuoyancyTransform transform, Runnable drawAction)
	{
		if (model == null || transform == null || (transform.heaveOffset == 0 && transform.pitchRadians == 0.0f && transform.rollRadians == 0.0f))
		{
			drawAction.run();
			return;
		}

		float[] verticesX = model.getVerticesX();
		float[] verticesY = model.getVerticesY();
		float[] verticesZ = model.getVerticesZ();
		int vertexCount = model.getVerticesCount();
		if (verticesX == null || verticesY == null || verticesZ == null || vertexCount <= 0)
		{
			drawAction.run();
			return;
		}

		float[] originalX = new float[vertexCount];
		float[] originalY = new float[vertexCount];
		float[] originalZ = new float[vertexCount];
		System.arraycopy(verticesX, 0, originalX, 0, vertexCount);
		System.arraycopy(verticesY, 0, originalY, 0, vertexCount);
		System.arraycopy(verticesZ, 0, originalZ, 0, vertexCount);

		float minX = Float.POSITIVE_INFINITY;
		float maxX = Float.NEGATIVE_INFINITY;
		float minY = Float.POSITIVE_INFINITY;
		float maxY = Float.NEGATIVE_INFINITY;
		float minZ = Float.POSITIVE_INFINITY;
		float maxZ = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < vertexCount; i++)
		{
			minX = Math.min(minX, originalX[i]);
			maxX = Math.max(maxX, originalX[i]);
			minY = Math.min(minY, originalY[i]);
			maxY = Math.max(maxY, originalY[i]);
			minZ = Math.min(minZ, originalZ[i]);
			maxZ = Math.max(maxZ, originalZ[i]);
		}

		float pivotX = (minX + maxX) * 0.5f;
		float pivotY = minY + ((maxY - minY) * 0.35f);
		float pivotZ = (minZ + maxZ) * 0.5f;
		float sinPitch = (float) Math.sin(transform.pitchRadians);
		float cosPitch = (float) Math.cos(transform.pitchRadians);
		float sinRoll = (float) Math.sin(transform.rollRadians);
		float cosRoll = (float) Math.cos(transform.rollRadians);

		for (int i = 0; i < vertexCount; i++)
		{
			float dx = originalX[i] - pivotX;
			float dy = originalY[i] - pivotY;
			float dz = originalZ[i] - pivotZ;

			float pitchY = (dy * cosPitch) - (dz * sinPitch);
			float pitchZ = (dy * sinPitch) + (dz * cosPitch);

			float rollX = (dx * cosRoll) - (pitchY * sinRoll);
			float rollY = (dx * sinRoll) + (pitchY * cosRoll);

			verticesX[i] = pivotX + rollX;
			verticesY[i] = pivotY + rollY;
			verticesZ[i] = pivotZ + pitchZ;
		}

		try
		{
			drawAction.run();
		}
		finally
		{
			System.arraycopy(originalX, 0, verticesX, 0, vertexCount);
			System.arraycopy(originalY, 0, verticesY, 0, vertexCount);
			System.arraycopy(originalZ, 0, verticesZ, 0, vertexCount);
		}
	}

	private RenderedObjectCandidate findHoveredCandidate(Point mouse)
	{
		List<RenderedObjectCandidate> candidates = latestCandidates;
		for (int i = candidates.size() - 1; i >= 0; i--)
		{
			RenderedObjectCandidate candidate = candidates.get(i);
			if (candidate.clickbox.contains(mouse.getX(), mouse.getY()))
			{
				return candidate;
			}
		}

		return null;
	}

	private static RenderedObjectCandidate findCandidate(long selectionKey, List<RenderedObjectCandidate> candidates)
	{
		for (int i = candidates.size() - 1; i >= 0; i--)
		{
			RenderedObjectCandidate candidate = candidates.get(i);
			if (candidate.selectionKey == selectionKey)
			{
				return candidate;
			}
		}
		return null;
	}

	private static RenderedObjectCandidate findCandidateByHash(long hash, List<RenderedObjectCandidate> candidates)
	{
		for (int i = candidates.size() - 1; i >= 0; i--)
		{
			RenderedObjectCandidate candidate = candidates.get(i);
			if (candidate.hash == hash)
			{
				return candidate;
			}
		}
		return null;
	}

	private static long buildSelectionKey(TileObject tileObject, int x, int z)
	{
		long key = tileObject.getHash();
		key = (key * 31L) + tileObject.getId();
		key = (key * 31L) + tileObject.getPlane();
		return key;
	}

	private void selectTileObject(TileObject tileObject)
	{
		long key = buildSelectionKey(tileObject, 0, 0);
		if (selectedKey == key)
		{
			clearSelection();
			log.info("Cleared buoyant object selection");
			return;
		}

		selectedWorldViewSelection = null;
		selectedKey = key;
		selectedHash = tileObject.getHash();
		selectedSizeX = resolveSizeX(tileObject);
		selectedSizeY = resolveSizeY(tileObject);
		selectedCandidate = findCandidate(key, latestCandidates);
		log.info(
			"Selected buoyant tile object id={} hash={} plane={}",
			tileObject.getId(),
			Long.toUnsignedString(tileObject.getHash()),
			tileObject.getPlane()
		);
	}

	private void selectWorldViewSelection(WorldViewSelection selection)
	{
		WorldViewSelection current = selectedWorldViewSelection;
		if (current != null && current.worldViewId == selection.worldViewId)
		{
			selectedSizeX = selection.sizeX;
			selectedSizeY = selection.sizeY;
			selectedWorldViewSelection = selection;
			resetWorldViewDebugState();
			log.info(
				"Refreshed buoyant worldview id={} objectCount={} size={}x{} mainLocal=({}, {})",
				selection.worldViewId,
				selection.hashes.size(),
				selection.sizeX,
				selection.sizeY,
				selection.anchorLocalPoint == null ? -1 : selection.anchorLocalPoint.getX(),
				selection.anchorLocalPoint == null ? -1 : selection.anchorLocalPoint.getY()
			);
			return;
		}

		selectedCandidate = null;
		selectedKey = NO_SELECTION;
		selectedHash = 0L;
		selectedSizeX = selection.sizeX;
		selectedSizeY = selection.sizeY;
		selectedWorldViewSelection = selection;
		resetWorldViewDebugState();
		log.info(
			"Selected buoyant worldview id={} originHash={} objectCount={} size={}x{} mainLocal=({}, {})",
			selection.worldViewId,
			Long.toUnsignedString(selection.originHash),
			selection.hashes.size(),
			selection.sizeX,
			selection.sizeY,
			selection.anchorLocalPoint == null ? -1 : selection.anchorLocalPoint.getX(),
			selection.anchorLocalPoint == null ? -1 : selection.anchorLocalPoint.getY()
		);
	}

	private void resetWorldViewDebugState()
	{
		debugRenderHitLogsRemaining = 12;
		debugProximityLogsRemaining = 24;
		selectedWorldViewProxyHash = 0L;
		selectedWorldViewProxyId = -1;
		selectedWorldViewProxyLocalX = Integer.MIN_VALUE;
		selectedWorldViewProxyLocalZ = Integer.MIN_VALUE;
		candidateProxyHash = 0L;
		candidateProxyId = -1;
		candidateProxyHits = 0;
		frameSelectionRenderHits = 0;
		frameWorldEntityRenderHits = 0;
		noRenderHitFrames = 0;
	}

	private void noteSelectedWorldViewRenderHit(long hash, String source, int x, int z)
	{
		frameSelectionRenderHits++;
		int hashType = (int) ((hash >> HASH_TYPE_SHIFT) & HASH_TYPE_MASK);
		if (hashType == HASH_TYPE_WORLD_ENTITY)
		{
			frameWorldEntityRenderHits++;
		}
		if (hash == selectedWorldViewProxyHash)
		{
			selectedWorldViewProxyLocalX = x;
			selectedWorldViewProxyLocalZ = z;
		}

		if (debugRenderHitLogsRemaining <= 0 || selectedWorldViewSelection == null)
		{
			return;
		}

		debugRenderHitLogsRemaining--;
		log.info(
			"Buoyancy debug: selected worldview render hit source={} hash={} type={} local=({}, {}) frameHits={} worldEntityHits={}",
			source,
			Long.toUnsignedString(hash),
			hashType,
			x,
			z,
			frameSelectionRenderHits,
			frameWorldEntityRenderHits
		);
	}

	public void debugProximityToSelectedWorldView(String source, Renderable renderable, long hash, int x, int z)
	{
		WorldViewSelection selection = selectedWorldViewSelection;
		if (selection == null || debugProximityLogsRemaining <= 0 || selection.anchorLocalPoint == null)
		{
			return;
		}

		int margin = DEBUG_PROXIMITY_MARGIN_TILES * TidesPlugin.LOCAL_TILE_SIZE;
		int halfWidth = ((selection.sizeX * TidesPlugin.LOCAL_TILE_SIZE) / 2) + margin;
		int halfLength = ((selection.sizeY * TidesPlugin.LOCAL_TILE_SIZE) / 2) + margin;
		int dx = x - selection.anchorLocalPoint.getX();
		int dz = z - selection.anchorLocalPoint.getY();
		if (Math.abs(dx) > halfWidth || Math.abs(dz) > halfLength)
		{
			return;
		}

		maybeCaptureSelectedWorldViewProxy(source, hash, x, z);

		debugProximityLogsRemaining--;
		int hashType = hash == -1L ? -1 : (int) ((hash >> HASH_TYPE_SHIFT) & HASH_TYPE_MASK);
		log.info(
			"Buoyancy debug: nearby render source={} class={} hash={} type={} local=({}, {}) delta=({}, {}) anchor=({}, {}) size={}x{}",
			source,
			renderable == null ? "<null>" : renderable.getClass().getSimpleName(),
			hash == -1L ? "-1" : Long.toUnsignedString(hash),
			hashType,
			x,
			z,
			dx,
			dz,
			selection.anchorLocalPoint.getX(),
			selection.anchorLocalPoint.getY(),
			selection.sizeX,
			selection.sizeY
		);
	}

	private void maybeCaptureSelectedWorldViewProxy(String source, long hash, int x, int z)
	{
		if (selectedWorldViewSelection == null || hash == -1L)
		{
			return;
		}

		int hashType = (int) ((hash >> HASH_TYPE_SHIFT) & HASH_TYPE_MASK);
		if (!"drawDynamicAsync".equals(source) || hashType != 2)
		{
			return;
		}

		if (selectedWorldViewProxyHash == hash)
		{
			return;
		}

		int objectId = (int) ((hash >> 20) & 0xffffffffL);
		int dx = x - selectedWorldViewSelection.anchorLocalPoint.getX();
		int dz = z - selectedWorldViewSelection.anchorLocalPoint.getY();
		if (objectId == persistedProxyObjectId
			&& persistedProxyDeltaX != Integer.MIN_VALUE
			&& persistedProxyDeltaZ != Integer.MIN_VALUE
			&& Math.abs(dx - persistedProxyDeltaX) <= TidesPlugin.LOCAL_TILE_SIZE * 2
			&& Math.abs(dz - persistedProxyDeltaZ) <= TidesPlugin.LOCAL_TILE_SIZE * 2)
		{
			lockSelectedWorldViewProxy(hash, objectId, x, z);
			return;
		}

		if (candidateProxyHash != hash)
		{
			candidateProxyHash = hash;
			candidateProxyId = objectId;
			candidateProxyHits = 1;
			return;
		}

		candidateProxyHits++;
		if (candidateProxyHits < 6)
		{
			return;
		}

		lockSelectedWorldViewProxy(hash, objectId, x, z);
	}

	private void lockSelectedWorldViewProxy(long hash, int objectId, int x, int z)
	{
		selectedWorldViewProxyHash = hash;
		selectedWorldViewProxyId = objectId;
		selectedWorldViewProxyLocalX = x;
		selectedWorldViewProxyLocalZ = z;
		debugRenderHitLogsRemaining = Math.max(debugRenderHitLogsRemaining, 6);
		if (selectedWorldViewSelection != null && selectedWorldViewSelection.anchorLocalPoint != null)
		{
			persistedProxyObjectId = objectId;
			persistedProxyDeltaX = x - selectedWorldViewSelection.anchorLocalPoint.getX();
			persistedProxyDeltaZ = z - selectedWorldViewSelection.anchorLocalPoint.getY();
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_PROXY_OBJECT_ID, objectId);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_PROXY_DELTA_X, persistedProxyDeltaX);
			configManager.setConfiguration(CONFIG_GROUP, CONFIG_PROXY_DELTA_Z, persistedProxyDeltaZ);
		}
		log.info(
			"Buoyancy debug: locked boat proxy hash={} id={} local=({}, {}) for worldview id={}",
			Long.toUnsignedString(hash),
			objectId,
			x,
			z,
			selectedWorldViewSelection == null ? -1 : selectedWorldViewSelection.worldViewId
		);
	}

	private TileObject findHoveredTileObject()
	{
		Tile selectedTile = client.getSelectedSceneTile();
		if (selectedTile == null)
		{
			return null;
		}

		TileObject direct = firstSelectableObject(selectedTile);
		if (direct != null)
		{
			return direct;
		}

		Tile bridge = selectedTile.getBridge();
		return bridge == null ? null : firstSelectableObject(bridge);
	}

	private static TileObject firstSelectableObject(Tile tile)
	{
		if (tile == null)
		{
			return null;
		}

		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects != null)
		{
			for (GameObject gameObject : gameObjects)
			{
				if (gameObject != null)
				{
					return gameObject;
				}
			}
		}

		if (tile.getDecorativeObject() != null)
		{
			return tile.getDecorativeObject();
		}
		if (tile.getWallObject() != null)
		{
			return tile.getWallObject();
		}
		if (tile.getGroundObject() != null)
		{
			return tile.getGroundObject();
		}
		return null;
	}

	private void dumpHoveredDebug(Point mouse)
	{
		Tile selectedTile = client.getSelectedSceneTile();
		if (selectedTile == null)
		{
			log.info("Buoyancy debug: no hovered scene tile, mouse=({}, {})", mouse.getX(), mouse.getY());
			return;
		}

		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			log.info("Buoyancy debug: hovered tile present but world view is unavailable");
			return;
		}

		net.runelite.api.Point sceneLocation = selectedTile.getSceneLocation();
		if (sceneLocation == null)
		{
			log.info("Buoyancy debug: hovered tile has no scene location");
			return;
		}

		int sceneX = sceneLocation.getX();
		int sceneY = sceneLocation.getY();
		int plane = selectedTile.getRenderLevel();
		int worldX = worldView.getBaseX() + sceneX;
		int worldY = worldView.getBaseY() + sceneY;

		log.info(
			"Buoyancy debug: hovered tile world=({}, {}, {}) scene=({}, {}) mouse=({}, {})",
			worldX,
			worldY,
			plane,
			sceneX,
			sceneY,
			mouse.getX(),
			mouse.getY()
		);
		logTileObjects("hovered", selectedTile);

		Tile bridge = selectedTile.getBridge();
		if (bridge != null)
		{
			logTileObjects("hovered-bridge", bridge);
		}

		Scene scene = worldView.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		if (tiles != null && plane >= 0 && plane < tiles.length)
		{
			for (int dy = -1; dy <= 1; dy++)
			{
				for (int dx = -1; dx <= 1; dx++)
				{
					if (dx == 0 && dy == 0)
					{
						continue;
					}

					int nx = sceneX + dx;
					int ny = sceneY + dy;
					if (nx < 0 || ny < 0 || nx >= tiles[plane].length || ny >= tiles[plane][nx].length)
					{
						continue;
					}

					Tile nearby = tiles[plane][nx][ny];
					if (nearby == null)
					{
						continue;
					}

					if (hasObjects(nearby))
					{
						logTileObjects("nearby(" + dx + "," + dy + ")", nearby);
					}
				}
			}
		}

		List<RenderedObjectCandidate> candidates = latestCandidates;
		int hoveredCandidates = 0;
		for (RenderedObjectCandidate candidate : candidates)
		{
			if (candidate.clickbox.contains(mouse.getX(), mouse.getY()))
			{
				hoveredCandidates++;
				log.info(
					"Buoyancy debug: render candidate id={} hash={} plane={} local=({}, {}, {}) size={}x{} orient={}",
					candidate.id,
					Long.toUnsignedString(candidate.hash),
					candidate.plane,
					candidate.x,
					candidate.y,
					candidate.z,
					candidate.sizeX,
					candidate.sizeY,
					candidate.orientation
				);
			}
		}

		if (hoveredCandidates == 0)
		{
			log.info("Buoyancy debug: no rendered candidates under mouse this frame");
		}

		dumpMenuEntries();
	}

	private static boolean hasObjects(Tile tile)
	{
		if (tile == null)
		{
			return false;
		}

		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects != null)
		{
			for (GameObject gameObject : gameObjects)
			{
				if (gameObject != null)
				{
					return true;
				}
			}
		}

		return tile.getDecorativeObject() != null
			|| tile.getWallObject() != null
			|| tile.getGroundObject() != null;
	}

	private static void logTileObjects(String label, Tile tile)
	{
		if (tile == null)
		{
			return;
		}

		net.runelite.api.Point sceneLocation = tile.getSceneLocation();
		int sx = sceneLocation == null ? -1 : sceneLocation.getX();
		int sy = sceneLocation == null ? -1 : sceneLocation.getY();
		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects != null)
		{
			for (GameObject gameObject : gameObjects)
			{
				if (gameObject != null)
				{
					log.info(
						"Buoyancy debug: {} gameObject id={} hash={} plane={} scene=({}, {}) size={}x{} orient={}",
						label,
						gameObject.getId(),
						Long.toUnsignedString(gameObject.getHash()),
						gameObject.getPlane(),
						sx,
						sy,
						gameObject.sizeX(),
						gameObject.sizeY(),
						gameObject.getOrientation()
					);
				}
			}
		}

		logTileObject(label, "decorative", tile.getDecorativeObject(), sx, sy);
		logTileObject(label, "wall", tile.getWallObject(), sx, sy);
		logTileObject(label, "ground", tile.getGroundObject(), sx, sy);
	}

	private static void logTileObject(String label, String kind, TileObject object, int sceneX, int sceneY)
	{
		if (object == null)
		{
			return;
		}

		log.info(
			"Buoyancy debug: {} {} id={} hash={} plane={} scene=({}, {}) type={}",
			label,
			kind,
			object.getId(),
			Long.toUnsignedString(object.getHash()),
			object.getPlane(),
			sceneX,
			sceneY,
			object.getClass().getSimpleName()
		);
	}

	private void dumpMenuEntries()
	{
		MenuEntry[] entries = client.getMenuEntries();
		if (entries == null || entries.length == 0)
		{
			log.info("Buoyancy debug: no menu entries");
			return;
		}

		List<MenuObjectRef> menuObjectRefs = collectMenuObjectRefs(entries, true);
		Set<Integer> objectIds = new LinkedHashSet<>();
		for (MenuObjectRef ref : menuObjectRefs)
		{
			objectIds.add(ref.identifier);
		}

		if (!menuObjectRefs.isEmpty())
		{
			logWorldViewMatchesForMenuRefs(menuObjectRefs);
		}

		if (!objectIds.isEmpty())
		{
			logSceneMatchesForMenuObjectIds(objectIds);
		}
	}

	private WorldViewSelection findMenuWorldViewSelection()
	{
		MenuEntry[] entries = client.getMenuEntries();
		if (entries == null || entries.length == 0)
		{
			return null;
		}

		List<MenuObjectRef> menuObjectRefs = collectMenuObjectRefs(entries, false);
		if (menuObjectRefs.isEmpty())
		{
			return null;
		}

		WorldViewSelection fallback = null;
		for (WorldView worldView : collectWorldViews())
		{
			WorldViewSelection selection = buildWorldViewSelection(worldView, menuObjectRefs);
			if (selection == null)
			{
				continue;
			}

			if (!worldView.isTopLevel())
			{
				return selection;
			}

			if (fallback == null)
			{
				fallback = selection;
			}
		}

		return fallback;
	}

	private WorldViewSelection findOwnedWorldViewSelection()
	{
		WorldView topLevel = client.getTopLevelWorldView();
		if (topLevel == null)
		{
			return null;
		}

		WorldEntity bestEntity = null;
		int bestDistance = Integer.MAX_VALUE;
		WorldPoint playerPoint = client.getLocalPlayer() == null ? null : client.getLocalPlayer().getWorldLocation();
		for (WorldEntity worldEntity : topLevel.worldEntities())
		{
			if (worldEntity == null || worldEntity.getWorldView() == null || worldEntity.getWorldView().isTopLevel())
			{
				continue;
			}

			if (worldEntity.getOwnerType() != WorldEntity.OWNER_TYPE_SELF_PLAYER)
			{
				continue;
			}

			int distance = 0;
			if (playerPoint != null && worldEntity.getLocalLocation() != null)
			{
				WorldPoint entityPoint = WorldPoint.fromLocalInstance(client, worldEntity.getLocalLocation());
				if (entityPoint != null)
				{
					distance = playerPoint.distanceTo2D(entityPoint);
				}
			}

			if (bestEntity == null || distance < bestDistance)
			{
				bestEntity = worldEntity;
				bestDistance = distance;
			}
		}

		if (bestEntity == null)
		{
			return null;
		}

		WorldViewSelection selection = buildWorldViewSelection(bestEntity);
		if (selection != null)
		{
			log.info(
				"Buoyancy debug: falling back to self-owned worldview id={} ownerType={} distance={}",
				selection.worldViewId,
				bestEntity.getOwnerType(),
				bestDistance
			);
		}
		return selection;
	}

	private List<MenuObjectRef> collectMenuObjectRefs(MenuEntry[] entries, boolean logEntries)
	{
		List<MenuObjectRef> menuObjectRefs = new ArrayList<>();
		int logged = 0;
		for (int i = entries.length - 1; i >= 0 && logged < 8; i--)
		{
			MenuEntry entry = entries[i];
			if (entry == null)
			{
				continue;
			}

			logged++;
			if (logEntries)
			{
				log.info(
					"Buoyancy debug: menu option='{}' target='{}' identifier={} param0={} param1={} type={} action={}",
					entry.getOption(),
					entry.getTarget(),
					entry.getIdentifier(),
					entry.getParam0(),
					entry.getParam1(),
					entry.getType(),
					invokeObject(entry, "getMenuAction")
				);
			}

			String type = String.valueOf(entry.getType());
			if (entry.getIdentifier() > 0 && isObjectMenuType(type))
			{
				menuObjectRefs.add(new MenuObjectRef(entry.getIdentifier(), entry.getParam0(), entry.getParam1(), type, entry.getTarget()));
			}
		}

		return menuObjectRefs;
	}

	private WorldViewSelection buildWorldViewSelection(WorldView worldView, List<MenuObjectRef> menuObjectRefs)
	{
		Scene scene = worldView.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		if (tiles == null || tiles.length == 0)
		{
			return null;
		}

		int plane = Math.max(0, Math.min(worldView.getPlane(), tiles.length - 1));
		Set<Long> seedHashes = new LinkedHashSet<>();
		int minSceneX = Integer.MAX_VALUE;
		int minSceneY = Integer.MAX_VALUE;
		int maxSceneX = Integer.MIN_VALUE;
		int maxSceneY = Integer.MIN_VALUE;

		for (MenuObjectRef ref : menuObjectRefs)
		{
			if (ref.param0 < 0 || ref.param1 < 0 || ref.param0 >= worldView.getSizeX() || ref.param1 >= worldView.getSizeY())
			{
				continue;
			}

			Tile tile = tiles[plane][ref.param0][ref.param1];
			if (!hasObjects(tile))
			{
				continue;
			}

			List<TileObject> tileObjects = collectTileObjects(tile);
			for (TileObject tileObject : tileObjects)
			{
				seedHashes.add(tileObject.getHash());
			}

			minSceneX = Math.min(minSceneX, ref.param0);
			minSceneY = Math.min(minSceneY, ref.param1);
			maxSceneX = Math.max(maxSceneX, ref.param0);
			maxSceneY = Math.max(maxSceneY, ref.param1);
		}

		if (seedHashes.isEmpty())
		{
			return null;
		}

		Set<Long> assemblyHashes = collectWorldViewObjectHashes(worldView);
		if (assemblyHashes.isEmpty())
		{
			return null;
		}

		WorldEntity owner = findOwnerForWorldView(worldView);
		LocalPoint anchor = owner == null ? null : owner.getLocalLocation();
		int orientation = owner == null ? 0 : owner.getOrientation();
		int sizeX = Math.max(1, maxSceneX - minSceneX + 1);
		int sizeY = Math.max(1, maxSceneY - minSceneY + 1);
		if (!worldView.isTopLevel())
		{
			sizeX = Math.max(sizeX, worldView.getSizeX());
			sizeY = Math.max(sizeY, worldView.getSizeY());
		}

		return new WorldViewSelection(
			worldView.getId(),
			anchor,
			orientation,
			sizeX,
			sizeY,
			seedHashes.iterator().next(),
			assemblyHashes
		);
	}

	private WorldViewSelection buildWorldViewSelection(WorldEntity owner)
	{
		WorldView worldView = owner.getWorldView();
		if (worldView == null || worldView.isTopLevel())
		{
			return null;
		}

		Set<Long> assemblyHashes = collectWorldViewObjectHashes(worldView);
		if (assemblyHashes.isEmpty())
		{
			return null;
		}

		int sizeX = Math.max(1, worldView.getSizeX());
		int sizeY = Math.max(1, worldView.getSizeY());
		WorldEntityConfig config = owner.getConfig();
		if (config != null)
		{
			sizeX = Math.max(sizeX, localUnitsToTiles(config.getBoundsWidth()));
			sizeY = Math.max(sizeY, localUnitsToTiles(config.getBoundsHeight()));
		}

		return new WorldViewSelection(
			worldView.getId(),
			owner.getLocalLocation(),
			owner.getOrientation(),
			sizeX,
			sizeY,
			assemblyHashes.iterator().next(),
			assemblyHashes
		);
	}

	private void logWorldViewMatchesForMenuRefs(List<MenuObjectRef> menuObjectRefs)
	{
		for (WorldView worldView : collectWorldViews())
		{
			log.info(
				"Buoyancy debug: worldview id={} base=({}, {}) plane={} size={}x{}",
				worldView.getId(),
				worldView.getBaseX(),
				worldView.getBaseY(),
				worldView.getPlane(),
				worldView.getSizeX(),
				worldView.getSizeY()
			);

			Scene scene = worldView.getScene();
			Tile[][][] tiles = scene == null ? null : scene.getTiles();
			if (tiles == null)
			{
				continue;
			}

			int plane = Math.max(0, Math.min(worldView.getPlane(), tiles.length - 1));
			for (MenuObjectRef ref : menuObjectRefs)
			{
				if (ref.param0 < 0 || ref.param1 < 0 || ref.param0 >= worldView.getSizeX() || ref.param1 >= worldView.getSizeY())
				{
					continue;
				}

				Tile tile = tiles[plane][ref.param0][ref.param1];
				if (!hasObjects(tile))
				{
					continue;
				}

				log.info(
					"Buoyancy debug: menu ref target='{}' id={} type={} maps to worldview={} world=({}, {}, {}) scene=({}, {})",
					ref.target,
					ref.identifier,
					ref.type,
					worldView.getId(),
					worldView.getBaseX() + ref.param0,
					worldView.getBaseY() + ref.param1,
					plane,
					ref.param0,
					ref.param1
				);
				logTileObjects("menu-ref", tile);
			}
		}
	}

	private void logSceneMatchesForMenuObjectIds(Set<Integer> objectIds)
	{
		int matches = 0;
		for (WorldView worldView : collectWorldViews())
		{
			Scene scene = worldView.getScene();
			Tile[][][] tiles = scene == null ? null : scene.getTiles();
			if (tiles == null)
			{
				continue;
			}

			for (int plane = 0; plane < tiles.length; plane++)
			{
				Tile[][] planeTiles = tiles[plane];
				if (planeTiles == null)
				{
					continue;
				}

				for (int x = 0; x < planeTiles.length; x++)
				{
					Tile[] row = planeTiles[x];
					if (row == null)
					{
						continue;
					}

					for (int y = 0; y < row.length; y++)
					{
						Tile tile = row[y];
						if (tile == null)
						{
							continue;
						}

						matches += logSceneMatch("scene-match", tile.getGameObjects(), objectIds, x, y, plane, worldView);
						matches += logSceneMatch("scene-match decorative", tile.getDecorativeObject(), objectIds, x, y, plane, worldView);
						matches += logSceneMatch("scene-match wall", tile.getWallObject(), objectIds, x, y, plane, worldView);
						matches += logSceneMatch("scene-match ground", tile.getGroundObject(), objectIds, x, y, plane, worldView);
					}
				}
			}
		}

		if (matches == 0)
		{
			log.info("Buoyancy debug: no scene objects matched hovered menu object ids {}", objectIds);
		}
	}

	private static String invokeString(Object target, String method)
	{
		Object value = invokeObject(target, method);
		return value == null ? "<null>" : String.valueOf(value);
	}

	private static int invokeInt(Object target, String method)
	{
		Object value = invokeObject(target, method);
		return value instanceof Number ? ((Number) value).intValue() : Integer.MIN_VALUE;
	}

	private static Object invokeObject(Object target, String method)
	{
		try
		{
			return target.getClass().getMethod(method).invoke(target);
		}
		catch (ReflectiveOperationException ex)
		{
			return "<missing:" + method + ">";
		}
	}

	private static boolean isObjectMenuType(String type)
	{
		return type != null && (type.contains("OBJECT") || type.startsWith("GAME_OBJECT"));
	}

	private static List<TileObject> collectTileObjects(Tile tile)
	{
		if (tile == null)
		{
			return Collections.emptyList();
		}

		List<TileObject> objects = new ArrayList<>();
		GameObject[] gameObjects = tile.getGameObjects();
		if (gameObjects != null)
		{
			for (GameObject gameObject : gameObjects)
			{
				if (gameObject != null)
				{
					objects.add(gameObject);
				}
			}
		}

		if (tile.getDecorativeObject() != null)
		{
			objects.add(tile.getDecorativeObject());
		}
		if (tile.getWallObject() != null)
		{
			objects.add(tile.getWallObject());
		}
		if (tile.getGroundObject() != null)
		{
			objects.add(tile.getGroundObject());
		}
		return objects;
	}

	private static int logSceneMatch(
		String label,
		GameObject[] objects,
		Set<Integer> objectIds,
		int sceneX,
		int sceneY,
		int plane,
		WorldView worldView)
	{
		if (objects == null)
		{
			return 0;
		}

		int matches = 0;
		for (GameObject object : objects)
		{
			if (object != null && objectIds.contains(object.getId()))
			{
				log.info(
					"Buoyancy debug: {} id={} hash={} plane={} scene=({}, {}) world=({}, {}) size={}x{} orient={}",
					label,
					object.getId(),
					Long.toUnsignedString(object.getHash()),
					plane,
					sceneX,
					sceneY,
					worldView.getBaseX() + sceneX,
					worldView.getBaseY() + sceneY,
					object.sizeX(),
					object.sizeY(),
					object.getOrientation()
				);
				matches++;
			}
		}
		return matches;
	}

	private static int logSceneMatch(
		String label,
		TileObject object,
		Set<Integer> objectIds,
		int sceneX,
		int sceneY,
		int plane,
		WorldView worldView)
	{
		if (object == null || !objectIds.contains(object.getId()))
		{
			return 0;
		}

		log.info(
			"Buoyancy debug: {} id={} hash={} plane={} scene=({}, {}) world=({}, {}) type={}",
			label,
			object.getId(),
			Long.toUnsignedString(object.getHash()),
			plane,
			sceneX,
			sceneY,
			worldView.getBaseX() + sceneX,
			worldView.getBaseY() + sceneY,
			object.getClass().getSimpleName()
		);
		return 1;
	}

	private List<WorldView> collectWorldViews()
	{
		WorldView topLevel = client.getTopLevelWorldView();
		if (topLevel == null)
		{
			return Collections.emptyList();
		}

		List<WorldView> worldViews = new ArrayList<>();
		worldViews.add(topLevel);
		for (WorldEntity worldEntity : topLevel.worldEntities())
		{
			if (worldEntity != null && worldEntity.getWorldView() != null)
			{
				worldViews.add(worldEntity.getWorldView());
			}
		}
		return worldViews;
	}

	private Set<Long> collectWorldViewObjectHashes(WorldView worldView)
	{
		Scene scene = worldView.getScene();
		Tile[][][] tiles = scene == null ? null : scene.getTiles();
		if (tiles == null)
		{
			return Collections.emptySet();
		}

		Set<Long> hashes = new LinkedHashSet<>();
		for (Tile[][] planeTiles : tiles)
		{
			if (planeTiles == null)
			{
				continue;
			}

			for (Tile[] row : planeTiles)
			{
				if (row == null)
				{
					continue;
				}

				for (Tile tile : row)
				{
					for (TileObject tileObject : collectTileObjects(tile))
					{
						hashes.add(tileObject.getHash());
					}
				}
			}
		}

		return hashes;
	}

	private WorldEntity findOwnerForWorldView(WorldView worldView)
	{
		WorldView topLevel = client.getTopLevelWorldView();
		if (topLevel == null)
		{
			return null;
		}

		for (WorldEntity worldEntity : topLevel.worldEntities())
		{
			if (worldEntity != null && worldEntity.getWorldView() != null && worldEntity.getWorldView().getId() == worldView.getId())
			{
				return worldEntity;
			}
		}

		return null;
	}

	private static int resolveSizeX(TileObject tileObject)
	{
		if (tileObject instanceof GameObject)
		{
			GameObject object = (GameObject) tileObject;
			return Math.max(1, object.sizeX());
		}
		return 1;
	}

	private static int resolveSizeY(TileObject tileObject)
	{
		if (tileObject instanceof GameObject)
		{
			GameObject object = (GameObject) tileObject;
			return Math.max(1, object.sizeY());
		}
		return 1;
	}

	private static int localUnitsToTiles(int localUnits)
	{
		return Math.max(1, (int) Math.ceil(localUnits / (double) TidesPlugin.LOCAL_TILE_SIZE));
	}

	private static float sampleWaveHeight(TidesPlugin plugin, TidesSceneSnapshot snapshot, TidesFftProfile profile, float localX, float localZ, int plane)
	{
		TidesWaterTile tile = sampleWaterTile(snapshot, localX, localZ, plane);
		if (tile == null)
		{
			return Float.NaN;
		}

		float amplitudeScale = tile.getWaveAmplitudeScale();
		float deepWater = deepWaterFactor(amplitudeScale);
		float time = (float) (System.nanoTime() / 1_000_000_000.0);
		float[] baseDir = profile.averagedWindDirection();

		float[] dir0 = chaoticDirection(baseDir[0], baseDir[1], 0.0f, 0.11f, time);
		float[] dir1 = chaoticDirection(-baseDir[1], baseDir[0], 1.9f, 0.08f, time);
		float[] rotatedBase = rotate(baseDir[0], baseDir[1], 0.85f);
		float[] dir2 = chaoticDirection(rotatedBase[0], rotatedBase[1], 3.4f, 0.06f, time);

		float swellHeight = profile.swellHeight();
		float swellLength0 = profile.swellLength0();
		float swellLength1 = profile.swellLength1();
		float swellLength2 = Math.max(profile.swellLength0() * 0.82f, 2304.0f);
		float swellSpeed0 = profile.swellSpeed0();
		float swellSpeed1 = profile.swellSpeed1();
		float swellSpeed2 = Math.max(profile.swellSpeed0() * 0.58f, 0.32f);

		float phase0 = ((localX * dir0[0]) + (localZ * dir0[1])) / swellLength0 + (time * swellSpeed0);
		float phase1 = ((localX * dir1[0]) + (localZ * dir1[1])) / swellLength1 - (time * swellSpeed1);
		float phase2 = ((localX * dir2[0]) + (localZ * dir2[1])) / swellLength2 + (time * swellSpeed2);

		float swell = ((float) Math.sin(phase0) * swellHeight)
			+ ((float) Math.sin(phase1) * swellHeight * 0.58f)
			+ ((float) Math.sin(phase2) * swellHeight * 0.34f);

		float band0 = sampleBandHeight(profile, 0, localX, localZ, time, deepWater);
		float band1 = sampleBandHeight(profile, 1, localX, localZ, time, deepWater);
		return (swell * deepWater + band0 + band1) * amplitudeScale;
	}

	private static float sampleBandHeight(TidesFftProfile profile, int band, float localX, float localZ, float time, float deepWater)
	{
		float[] dir = bandWindDirection(profile, band);
		float motionScale = profile.motionScale() * BAND_TIME_SCALES[band];
		float domain = profile.bandDomain(band) * mix(1.0f, band == 0 ? 2.35f : 2.05f, deepWater);
		float secondaryDomain = domain * 1.37f;
		float averageScale = profile.bandAverageScale(band);
		float amplitude = averageScale * (band == 0 ? 22.0f : 12.0f);
		float phaseA = ((localX * dir[0]) + (localZ * dir[1])) / domain + (time * motionScale);
		float phaseB = ((localX * -dir[1]) + (localZ * dir[0])) / secondaryDomain - (time * motionScale * 0.63f);
		return (((float) Math.sin(phaseA) * 0.78f) + ((float) Math.cos(phaseB) * 0.22f)) * amplitude;
	}

	private static TidesWaterTile sampleWaterTile(TidesSceneSnapshot snapshot, float localX, float localZ, int plane)
	{
		int sceneX = floorDiv(localX, TidesPlugin.LOCAL_TILE_SIZE);
		int sceneY = floorDiv(localZ, TidesPlugin.LOCAL_TILE_SIZE);
		return snapshot.getTile(snapshot.getBaseX() + sceneX, snapshot.getBaseY() + sceneY, plane);
	}

	private static int floorDiv(float value, int divisor)
	{
		return (int) Math.floor(value / divisor);
	}

	private static float[] bandWindDirection(TidesFftProfile profile, int band)
	{
		TidesSpectrumParameters a = profile.spectrum(band * 2);
		TidesSpectrumParameters b = profile.spectrum((band * 2) + 1);
		float ax = (float) Math.cos(Math.toRadians(a.windDirectionDegrees())) * Math.max(0.01f, a.scale());
		float az = (float) Math.sin(Math.toRadians(a.windDirectionDegrees())) * Math.max(0.01f, a.scale());
		float bx = (float) Math.cos(Math.toRadians(b.windDirectionDegrees())) * Math.max(0.01f, b.scale());
		float bz = (float) Math.sin(Math.toRadians(b.windDirectionDegrees())) * Math.max(0.01f, b.scale());
		return normalize(ax + bx, az + bz);
	}

	private static float[] chaoticDirection(float x, float z, float seed, float timeScale, float time)
	{
		float angle = (float) (Math.sin(time * timeScale + seed) * 0.42 + Math.sin(time * (timeScale * 0.53f) + seed * 1.73f) * 0.24);
		float[] rotated = rotate(x, z, angle);
		return normalize(rotated[0], rotated[1]);
	}

	private static float[] rotate(float x, float z, float angle)
	{
		float sin = (float) Math.sin(angle);
		float cos = (float) Math.cos(angle);
		return new float[] {(x * cos) - (z * sin), (x * sin) + (z * cos)};
	}

	private static float[] normalize(float x, float z)
	{
		float length = (float) Math.sqrt((x * x) + (z * z));
		if (length < 1e-4f)
		{
			return new float[] {1.0f, 0.0f};
		}
		return new float[] {x / length, z / length};
	}

	private static float deepWaterFactor(float amplitudeScale)
	{
		float t = clamp((amplitudeScale - 1.0f) / 1.25f, 0.0f, 1.0f);
		return t * t * (3.0f - (2.0f * t));
	}

	private static float mix(float a, float b, float t)
	{
		return a + ((b - a) * t);
	}

	private static float clamp(float value, float min, float max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private static float orientationToRadians(int orientation)
	{
		return (float) (orientation * (Math.PI / 1024.0));
	}

	@Getter
	public static final class RenderedObjectCandidate
	{
		private final long selectionKey;
		private final int id;
		private final long hash;
		private final int plane;
		private final int orientation;
		private final int x;
		private final int y;
		private final int z;
		private final int sizeX;
		private final int sizeY;
		private final Shape clickbox;

		private RenderedObjectCandidate(
			long selectionKey,
			int id,
			long hash,
			int plane,
			int orientation,
			int x,
			int y,
			int z,
			int sizeX,
			int sizeY,
			Shape clickbox)
		{
			this.selectionKey = selectionKey;
			this.id = id;
			this.hash = hash;
			this.plane = plane;
			this.orientation = orientation;
			this.x = x;
			this.y = y;
			this.z = z;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.clickbox = clickbox;
		}
	}

	public static final class BuoyancyTransform
	{
		private final int heaveOffset;
		private final float pitchRadians;
		private final float rollRadians;

		private BuoyancyTransform(int heaveOffset, float pitchRadians, float rollRadians)
		{
			this.heaveOffset = heaveOffset;
			this.pitchRadians = pitchRadians;
			this.rollRadians = rollRadians;
		}

		public int getHeaveOffset()
		{
			return heaveOffset;
		}
	}

	@Getter
	public static final class SelectedOverlayFootprint
	{
		private final int localX;
		private final int localZ;
		private final int sizeX;
		private final int sizeY;
		private final boolean proxyLocked;

		private SelectedOverlayFootprint(int localX, int localZ, int sizeX, int sizeY, boolean proxyLocked)
		{
			this.localX = localX;
			this.localZ = localZ;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.proxyLocked = proxyLocked;
		}
	}

	private static final class MenuObjectRef
	{
		private final int identifier;
		private final int param0;
		private final int param1;
		private final String type;
		private final String target;

		private MenuObjectRef(int identifier, int param0, int param1, String type, String target)
		{
			this.identifier = identifier;
			this.param0 = param0;
			this.param1 = param1;
			this.type = type;
			this.target = target;
		}
	}

	private static final class WorldViewSelection
	{
		private final int worldViewId;
		private final LocalPoint anchorLocalPoint;
		private final int orientation;
		private final int sizeX;
		private final int sizeY;
		private final long originHash;
		private final Set<Long> hashes;

		private WorldViewSelection(int worldViewId, LocalPoint anchorLocalPoint, int orientation, int sizeX, int sizeY, long originHash, Set<Long> hashes)
		{
			this.worldViewId = worldViewId;
			this.anchorLocalPoint = anchorLocalPoint;
			this.orientation = orientation;
			this.sizeX = sizeX;
			this.sizeY = sizeY;
			this.originHash = originHash;
			this.hashes = hashes;
		}
	}
}
