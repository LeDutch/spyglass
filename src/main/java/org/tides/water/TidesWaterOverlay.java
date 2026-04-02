package org.tides.water;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import org.tides.TidesPlugin;
import org.tides.buoyancy.TidesBuoyancyController;

public class TidesWaterOverlay extends Overlay
{
	private static final Color SHORELINE_FILL = new Color(255, 208, 64, 88);
	private static final Color SHORELINE_OUTLINE = new Color(255, 240, 160, 255);
	private static final Color DEEP_OCEAN_FILL = new Color(32, 128, 255, 82);
	private static final Color DEEP_OCEAN_OUTLINE = new Color(160, 220, 255, 255);
	private static final Color LABEL_SHADOW = new Color(0, 0, 0, 190);
	private static final Color SELECTED_OBJECT_OUTLINE = new Color(255, 96, 96, 255);
	private static final Color SELECTED_OBJECT_FILL = new Color(255, 64, 64, 38);

	private final Client client;
	private final TidesPlugin plugin;

	@Inject
	public TidesWaterOverlay(Client client, TidesPlugin plugin)
	{
		this.client = client;
		this.plugin = plugin;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.ABOVE_SCENE);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		TidesBuoyancyController.RenderedObjectCandidate selectedObject = plugin.getSelectedRenderedObject();
		TidesBuoyancyController.SelectedOverlayFootprint selectedFootprint = plugin.getSelectedOverlayFootprint();
		if (!plugin.shouldDrawDebugOverlay() && selectedObject == null && selectedFootprint == null)
		{
			return null;
		}

		if (plugin.shouldDrawDebugOverlay())
		{
			for (TidesWaterTile tile : plugin.getWaterTiles())
			{
				if (!tile.isShoreline() && !tile.isDeepOcean())
				{
					continue;
				}

				WorldPoint worldPoint = tile.getWorldPoint();
				LocalPoint localPoint = plugin.worldToLocal(worldPoint);
				if (localPoint == null)
				{
					continue;
				}

				Polygon polygon = Perspective.getCanvasTilePoly(client, localPoint);
				if (polygon == null)
				{
					continue;
				}

				Color fillColor = tile.isShoreline() ? SHORELINE_FILL : DEEP_OCEAN_FILL;
				Color outlineColor = tile.isShoreline() ? SHORELINE_OUTLINE : DEEP_OCEAN_OUTLINE;
				graphics.setColor(fillColor);
				graphics.fillPolygon(polygon);

				graphics.setStroke(new BasicStroke(tile.isShoreline() ? 2.5f : 2.0f));
				graphics.setColor(outlineColor);
				graphics.drawPolygon(polygon);

				drawTileLabel(graphics, polygon, tile.isShoreline() ? "S" : "D", outlineColor);
			}
		}

		if (selectedObject != null)
		{
			Shape clickbox = selectedObject.getClickbox();
			if (clickbox != null)
			{
				graphics.setColor(SELECTED_OBJECT_FILL);
				graphics.fill(clickbox);
				graphics.setStroke(new BasicStroke(2.0f));
				graphics.setColor(SELECTED_OBJECT_OUTLINE);
				graphics.draw(clickbox);
			}
		}

		if (selectedFootprint != null)
		{
			drawSelectedFootprint(graphics, selectedFootprint);
		}

		return null;
	}

	private void drawSelectedFootprint(Graphics2D graphics, TidesBuoyancyController.SelectedOverlayFootprint footprint)
	{
		WorldView worldView = client.getTopLevelWorldView();
		if (worldView == null)
		{
			return;
		}

		int centerSceneX = floorDiv(footprint.getLocalX(), TidesPlugin.LOCAL_TILE_SIZE);
		int centerSceneY = floorDiv(footprint.getLocalZ(), TidesPlugin.LOCAL_TILE_SIZE);
		int startSceneX = centerSceneX - (footprint.getSizeX() / 2);
		int startSceneY = centerSceneY - (footprint.getSizeY() / 2);
		Polygon labelPolygon = null;

		for (int dx = 0; dx < footprint.getSizeX(); dx++)
		{
			for (int dy = 0; dy < footprint.getSizeY(); dy++)
			{
				int sceneX = startSceneX + dx;
				int sceneY = startSceneY + dy;
				if (sceneX < 0 || sceneY < 0 || sceneX >= worldView.getSizeX() || sceneY >= worldView.getSizeY())
				{
					continue;
				}

				WorldPoint worldPoint = new WorldPoint(worldView.getBaseX() + sceneX, worldView.getBaseY() + sceneY, worldView.getPlane());
				LocalPoint localPoint = plugin.worldToLocal(worldPoint);
				if (localPoint == null)
				{
					continue;
				}

				Polygon polygon = Perspective.getCanvasTilePoly(client, localPoint);
				if (polygon == null)
				{
					continue;
				}

				if (labelPolygon == null)
				{
					labelPolygon = polygon;
				}

				graphics.setColor(SELECTED_OBJECT_FILL);
				graphics.fillPolygon(polygon);
				graphics.setStroke(new BasicStroke(2.0f));
				graphics.setColor(SELECTED_OBJECT_OUTLINE);
				graphics.drawPolygon(polygon);
			}
		}

		if (labelPolygon != null)
		{
			drawTileLabel(graphics, labelPolygon, footprint.isProxyLocked() ? "BOAT" : "SEL", SELECTED_OBJECT_OUTLINE);
		}
	}

	private static int floorDiv(int value, int divisor)
	{
		return (int) Math.floor(value / (double) divisor);
	}

	private static void drawTileLabel(Graphics2D graphics, Polygon polygon, String label, Color color)
	{
		Rectangle bounds = polygon.getBounds();
		FontMetrics metrics = graphics.getFontMetrics();
		int x = bounds.x + ((bounds.width - metrics.stringWidth(label)) / 2);
		int y = bounds.y + ((bounds.height + metrics.getAscent()) / 2) - 2;

		graphics.setColor(LABEL_SHADOW);
		graphics.drawString(label, x + 1, y + 1);
		graphics.setColor(color);
		graphics.drawString(label, x, y);
	}
}
