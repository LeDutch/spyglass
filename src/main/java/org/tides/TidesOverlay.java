package org.tides;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class TidesOverlay extends Overlay
{
	private static final int LOCAL_TILE_SIZE = Perspective.LOCAL_TILE_SIZE;
	private static final int WATER_TEXTURE_ID = 1;
	private static final Color DEBUG_TILE_OUTLINE = new Color(255, 210, 80, 220);

	private final Client client;
	private final TidesPlugin plugin;

	@Inject
	public TidesOverlay(Client client, TidesPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!plugin.isEnabled() || client.getGameState().getState() < 30)
		{
			return null;
		}

		TidesConfig config = plugin.getConfig();
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return null;
		}

		List<WorldPoint> waterTiles = detectWaterTiles(worldView);
		List<CellPolygon> cells = buildMesh(worldView, waterTiles, config);
		if (cells.isEmpty())
		{
			renderDebugOverlay(graphics, config);
			return null;
		}

		cells.sort(Comparator.comparingInt(CellPolygon::depth));
		graphics.setStroke(new BasicStroke(1f));

		for (CellPolygon cell : cells)
		{
			graphics.setColor(cell.fillColor());
			graphics.fillPolygon(cell.polygon());
			if (config.wireframe())
			{
				graphics.setColor(new Color(255, 255, 255, 80));
				graphics.drawPolygon(cell.polygon());
			}
		}

		renderDebugOverlay(graphics, config);
		return null;
	}

	public TileDebugInfo inspectHoveredTile()
	{
		Tile tile = findHoveredTile();
		if (tile == null)
		{
			return null;
		}

		Set<Integer> textureIds = collectTextureIds(tile);
		Integer paintTextureId = null;
		SceneTilePaint paint = tile.getSceneTilePaint();
		if (paint != null && paint.getTexture() >= 0)
		{
			paintTextureId = paint.getTexture();
		}

		return new TileDebugInfo(tile, tile.getWorldLocation(), paintTextureId, textureIds);
	}

	private List<WorldPoint> detectWaterTiles(WorldView worldView)
	{
		List<WorldPoint> waterTiles = new ArrayList<>();
		Scene scene = worldView.getScene();
		Tile[][][] tiles = scene.getTiles();
		if (tiles == null || worldView.getPlane() < 0 || worldView.getPlane() >= tiles.length)
		{
			return waterTiles;
		}

		Tile[][] planeTiles = tiles[worldView.getPlane()];
		for (Tile[] row : planeTiles)
		{
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}

				Set<Integer> tileTextures = collectTextureIds(tile);
				if (tileTextures.contains(WATER_TEXTURE_ID))
				{
					waterTiles.add(tile.getWorldLocation());
				}
			}
		}

		return waterTiles;
	}

	private List<CellPolygon> buildMesh(WorldView worldView, List<WorldPoint> tiles, TidesConfig config)
	{
		int subdivisions = config.subdivisions();
		int step = LOCAL_TILE_SIZE / subdivisions;
		List<CellPolygon> cells = new ArrayList<>(Math.max(tiles.size(), 1) * subdivisions * subdivisions);

		long now = System.nanoTime();
		double time = now / 1_000_000_000.0;
		double phaseSpeed = config.speed() / 100.0;
		double amplitude = config.amplitude();
		double wavelength = Math.max(16.0, config.wavelength());

		for (WorldPoint worldTile : tiles)
		{
			if (worldTile.getPlane() != worldView.getPlane())
			{
				continue;
			}

			int tileLocalBaseX = (worldTile.getX() - worldView.getBaseX()) * LOCAL_TILE_SIZE;
			int tileLocalBaseY = (worldTile.getY() - worldView.getBaseY()) * LOCAL_TILE_SIZE;

			for (int subX = 0; subX < subdivisions; subX++)
			{
				for (int subY = 0; subY < subdivisions; subY++)
				{
					int localX0 = tileLocalBaseX + (subX * step);
					int localY0 = tileLocalBaseY + (subY * step);
					int localX1 = localX0 + step;
					int localY1 = localY0 + step;

					CellVertex a = projectVertex(worldView, localX0, localY0, amplitude, wavelength, phaseSpeed, time);
					CellVertex b = projectVertex(worldView, localX1, localY0, amplitude, wavelength, phaseSpeed, time);
					CellVertex c = projectVertex(worldView, localX1, localY1, amplitude, wavelength, phaseSpeed, time);
					CellVertex d = projectVertex(worldView, localX0, localY1, amplitude, wavelength, phaseSpeed, time);

					if (a == null || b == null || c == null || d == null)
					{
						continue;
					}

					Polygon polygon = new Polygon(
						new int[] {a.canvasX, b.canvasX, c.canvasX, d.canvasX},
						new int[] {a.canvasY, b.canvasY, c.canvasY, d.canvasY},
						4
					);

					Color fill = shadeCell(a.height, b.height, c.height, d.height, config.opacity());
					int depth = a.localY + b.localY + c.localY + d.localY;
					cells.add(new CellPolygon(polygon, fill, depth));
				}
			}
		}

		return cells;
	}

	private CellVertex projectVertex(WorldView worldView, int localX, int localY, double amplitude, double wavelength, double phaseSpeed, double time)
	{
		int worldViewId = worldView.getId();
		LocalPoint localPoint = new LocalPoint(localX, localY, worldViewId);
		int displacement = waveHeight(localX, localY, amplitude, wavelength, phaseSpeed, time);
		Point projected = Perspective.localToCanvas(client, localPoint, worldView.getPlane(), displacement);
		if (projected == null)
		{
			return null;
		}

		return new CellVertex(projected.getX(), projected.getY(), localY, displacement);
	}

	private int waveHeight(int localX, int localY, double amplitude, double wavelength, double phaseSpeed, double time)
	{
		double primary = Math.sin((localX / wavelength) + (time * phaseSpeed));
		double secondary = Math.cos((localY / (wavelength * 0.75)) - (time * phaseSpeed * 1.35));
		double diagonal = Math.sin(((localX + localY) / (wavelength * 1.4)) + (time * phaseSpeed * 0.8));
		double combined = (primary * 0.55) + (secondary * 0.3) + (diagonal * 0.15);
		return (int) Math.round(combined * amplitude);
	}

	private Color shadeCell(int a, int b, int c, int d, int opacityPercent)
	{
		float average = (a + b + c + d) / 4.0f;
		int brightness = clamp(120 + Math.round(average * 1.4f), 70, 200);
		int green = clamp(brightness + 20, 90, 220);
		int blue = clamp(brightness + 45, 120, 255);
		int alpha = clamp((int) Math.round(opacityPercent * 2.55), 0, 255);
		return new Color(30, green, blue, alpha);
	}

	private int clamp(int value, int min, int max)
	{
		return Math.max(min, Math.min(max, value));
	}

	private void renderDebugOverlay(Graphics2D graphics, TidesConfig config)
	{
		if (!config.debugOverlay())
		{
			return;
		}

		TileDebugInfo debugInfo = inspectHoveredTile();
		if (debugInfo == null)
		{
			return;
		}

		Polygon polygon = Perspective.getCanvasTilePoly(client, debugInfo.tile().getLocalLocation());
		if (polygon != null)
		{
			graphics.setColor(DEBUG_TILE_OUTLINE);
			graphics.drawPolygon(polygon);
		}

		List<String> lines = new ArrayList<>();
		lines.add("Tides Debug");
		lines.add("Tile: " + debugInfo.worldPoint().getX() + "," + debugInfo.worldPoint().getY() + "," + debugInfo.worldPoint().getPlane());
		lines.add("Paint texture: " + (debugInfo.paintTextureId() == null ? "-" : debugInfo.paintTextureId()));
		lines.add("All textures: " + debugInfo.textureIds());
		lines.add("Water texture ID: " + WATER_TEXTURE_ID);
		lines.add("Is water: " + debugInfo.textureIds().contains(WATER_TEXTURE_ID));
		drawDebugPanel(graphics, lines);
	}

	private void drawDebugPanel(Graphics2D graphics, List<String> lines)
	{
		int x = 12;
		int y = 24;
		int lineHeight = 16;
		int width = 0;
		for (String line : lines)
		{
			width = Math.max(width, graphics.getFontMetrics().stringWidth(line));
		}

		int height = (lines.size() * lineHeight) + 8;
		graphics.setColor(new Color(15, 20, 30, 170));
		graphics.fillRoundRect(x - 6, y - 14, width + 12, height, 8, 8);

		graphics.setColor(Color.WHITE);
		for (int i = 0; i < lines.size(); i++)
		{
			graphics.drawString(lines.get(i), x, y + (i * lineHeight));
		}
	}

	private Tile findHoveredTile()
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null || worldView.getScene() == null)
		{
			return null;
		}

		Tile selected = worldView.getSelectedSceneTile();
		if (selected != null)
		{
			return selected;
		}

		Point mouse = client.getMouseCanvasPosition();
		Scene scene = worldView.getScene();
		Tile[][][] tiles = scene.getTiles();
		if (tiles == null || worldView.getPlane() < 0 || worldView.getPlane() >= tiles.length)
		{
			return null;
		}

		Tile[][] planeTiles = tiles[worldView.getPlane()];
		for (Tile[] row : planeTiles)
		{
			for (Tile tile : row)
			{
				if (tile == null)
				{
					continue;
				}

				Polygon polygon = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());
				if (polygon != null && polygon.contains(mouse.getX(), mouse.getY()))
				{
					return tile;
				}
			}
		}

		return null;
	}

	private Set<Integer> collectTextureIds(Tile tile)
	{
		Set<Integer> textureIds = new LinkedHashSet<>();

		SceneTilePaint paint = tile.getSceneTilePaint();
		if (paint != null && paint.getTexture() >= 0)
		{
			textureIds.add(paint.getTexture());
		}

		SceneTileModel model = tile.getSceneTileModel();
		if (model != null && model.getTriangleTextureId() != null)
		{
			for (int textureId : model.getTriangleTextureId())
			{
				if (textureId >= 0)
				{
					textureIds.add(textureId);
				}
			}
		}

		return textureIds;
	}

	private static final class CellVertex
	{
		private final int canvasX;
		private final int canvasY;
		private final int localY;
		private final int height;

		private CellVertex(int canvasX, int canvasY, int localY, int height)
		{
			this.canvasX = canvasX;
			this.canvasY = canvasY;
			this.localY = localY;
			this.height = height;
		}
	}

	private static final class CellPolygon
	{
		private final Polygon polygon;
		private final Color fillColor;
		private final int depth;

		private CellPolygon(Polygon polygon, Color fillColor, int depth)
		{
			this.polygon = polygon;
			this.fillColor = fillColor;
			this.depth = depth;
		}

		private Polygon polygon()
		{
			return polygon;
		}

		private Color fillColor()
		{
			return fillColor;
		}

		private int depth()
		{
			return depth;
		}
	}
}
