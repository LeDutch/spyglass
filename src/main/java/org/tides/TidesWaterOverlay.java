package org.tides;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class TidesWaterOverlay extends Overlay
{
	private static final Color SHORELINE_COLOR = new Color(255, 255, 255, 210);

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
		if (!plugin.shouldDrawDebugOverlay())
		{
			return null;
		}

		String status = "Tides bridge: " + plugin.getHdBridgeStatus();
		String shaderStatus = "Shaders: " + plugin.getHdShaderStatus();
		String sceneStatus = "Scene: " + plugin.getHdSceneInjectionStatus();
		graphics.setColor(new Color(0, 0, 0, 180));
		graphics.drawString(status, 11, 21);
		graphics.drawString(shaderStatus, 11, 37);
		graphics.drawString(sceneStatus, 11, 53);
		graphics.setColor(plugin.isHdBridgeHooked() ? new Color(140, 255, 180) : new Color(255, 220, 120));
		graphics.drawString(status, 10, 20);
		graphics.drawString(shaderStatus, 10, 36);
		graphics.drawString(sceneStatus, 10, 52);

		long nowNanos = System.nanoTime();
		FontMetrics fontMetrics = graphics.getFontMetrics();

		for (TidesWaterTile tile : plugin.getWaterTiles())
		{
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

			Color baseColor = tile.getWaterType().getDebugColor();
			int waveHeight = plugin.sampleWaveHeight(tile, TidesPlugin.LOCAL_TILE_SIZE / 2, TidesPlugin.LOCAL_TILE_SIZE / 2, nowNanos);
			int fillAlpha = 35 + Math.min(50, Math.abs(waveHeight) * 2);
			graphics.setColor(withAlpha(baseColor, fillAlpha));
			graphics.fillPolygon(polygon);

			graphics.setStroke(new BasicStroke(tile.isShoreline() ? 2f : 1f));
			graphics.setColor(tile.isShoreline() ? SHORELINE_COLOR : withAlpha(baseColor, 200));
			graphics.drawPolygon(polygon);

			net.runelite.api.Point textPoint = Perspective.getCanvasTextLocation(client, graphics, localPoint, tile.getWaterType().name(), 0);
			if (textPoint != null)
			{
				String text = tile.getWaterType().name() + " " + waveHeight;
				int x = textPoint.getX() - (fontMetrics.stringWidth(text) / 2);
				int y = textPoint.getY();
				graphics.setColor(new Color(0, 0, 0, 180));
				graphics.drawString(text, x + 1, y + 1);
				graphics.setColor(Color.WHITE);
				graphics.drawString(text, x, y);
			}
		}

		return null;
	}

	private static Color withAlpha(Color color, int alpha)
	{
		return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.max(0, Math.min(255, alpha)));
	}
}
