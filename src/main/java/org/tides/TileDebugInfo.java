package org.tides;

import java.util.Set;
import net.runelite.api.Tile;
import net.runelite.api.coords.WorldPoint;

public final class TileDebugInfo
{
	private final Tile tile;
	private final WorldPoint worldPoint;
	private final Integer paintTextureId;
	private final Set<Integer> textureIds;

	public TileDebugInfo(Tile tile, WorldPoint worldPoint, Integer paintTextureId, Set<Integer> textureIds)
	{
		this.tile = tile;
		this.worldPoint = worldPoint;
		this.paintTextureId = paintTextureId;
		this.textureIds = textureIds;
	}

	public Tile tile()
	{
		return tile;
	}

	public WorldPoint worldPoint()
	{
		return worldPoint;
	}

	public Integer paintTextureId()
	{
		return paintTextureId;
	}

	public Set<Integer> textureIds()
	{
		return textureIds;
	}
}
