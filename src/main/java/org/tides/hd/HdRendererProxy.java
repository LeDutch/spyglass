package org.tides.hd;

import java.util.Set;
import net.runelite.api.GameObject;
import net.runelite.api.Model;
import net.runelite.api.Projection;
import net.runelite.api.Renderable;
import net.runelite.api.Scene;
import net.runelite.api.SceneTileModel;
import net.runelite.api.SceneTilePaint;
import net.runelite.api.Texture;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.hooks.DrawCallbacks;
import org.tides.TidesPlugin;
import org.tides.buoyancy.TidesBuoyancyController;

final class HdRendererProxy implements DrawCallbacks
{
	private final TidesPlugin plugin;
	private final HdRendererBridge bridge;
	private final DrawCallbacks delegate;

	HdRendererProxy(TidesPlugin plugin, HdRendererBridge bridge, DrawCallbacks delegate)
	{
		this.plugin = plugin;
		this.bridge = bridge;
		this.delegate = delegate;
	}

	@Override
	public void draw(Projection projection, Scene scene, Renderable renderable, int orientation, int x, int y, int z, long hash)
	{
		plugin.debugSelectedBuoyancyProximity("draw", renderable, hash, x, z);
		TidesBuoyancyController.BuoyancyTransform transform = plugin.computeRenderableBuoyancyTransform(hash, orientation, x, z);
		if (transform == null)
		{
			delegate.draw(projection, scene, renderable, orientation, x, y, z, hash);
			return;
		}

		Model model = renderable instanceof Model ? (Model) renderable : renderable.getModel();
		if (model == null)
		{
			delegate.draw(projection, scene, renderable, orientation, x, y + transform.getHeaveOffset(), z, hash);
			return;
		}

		plugin.drawBuoyantModel(model, transform, () ->
			delegate.draw(projection, scene, renderable, orientation, x, y + transform.getHeaveOffset(), z, hash));
	}

	@Override
	public void drawScenePaint(Scene scene, SceneTilePaint paint, int plane, int tileX, int tileZ)
	{
		delegate.drawScenePaint(scene, paint, plane, tileX, tileZ);
	}

	@Override
	public void drawSceneTileModel(Scene scene, SceneTileModel model, int tileX, int tileZ)
	{
		delegate.drawSceneTileModel(scene, model, tileX, tileZ);
	}

	@Override
	public void draw(int overlayColor)
	{
		delegate.draw(overlayColor);
	}

	@Override
	public void drawScene(double cameraX, double cameraY, double cameraZ, double cameraPitch, double cameraYaw, int plane)
	{
		delegate.drawScene(cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, plane);
	}

	@Override
	public void postDrawScene()
	{
		delegate.postDrawScene();
	}

	@Override
	public void animate(Texture texture, int diff)
	{
		delegate.animate(texture, diff);
	}

	@Override
	public void loadScene(Scene scene)
	{
		bridge.noteSceneCallback("loadScene");
		delegate.loadScene(scene);
		bridge.onRendererSceneChanged(plugin, "loadScene");
	}

	@Override
	public void swapScene(Scene scene)
	{
		bridge.noteSceneCallback("swapScene");
		delegate.swapScene(scene);
		bridge.onRendererSceneChanged(plugin, "swapScene");
	}

	@Override
	public boolean tileInFrustum(Scene scene, float pitchSin, float pitchCos, float yawSin, float yawCos, int cameraX, int cameraY, int cameraZ, int plane, int msx, int msy)
	{
		return delegate.tileInFrustum(scene, pitchSin, pitchCos, yawSin, yawCos, cameraX, cameraY, cameraZ, plane, msx, msy);
	}

	@Override
	public boolean zoneInFrustum(int zoneX, int zoneZ, int maxY, int minY)
	{
		return delegate.zoneInFrustum(zoneX, zoneZ, maxY, minY);
	}

	@Override
	public void loadScene(WorldView worldView, Scene scene)
	{
		bridge.noteSceneCallback("loadScene(WorldView)");
		delegate.loadScene(worldView, scene);
		bridge.onRendererSceneChanged(plugin, "loadScene(WorldView)");
	}

	@Override
	public void despawnWorldView(WorldView worldView)
	{
		delegate.despawnWorldView(worldView);
	}

	@Override
	public void preSceneDraw(
		Scene scene,
		float cameraX, float cameraY, float cameraZ, float cameraPitch, float cameraYaw,
		int minLevel, int level, int maxLevel, Set<Integer> hideRoofIds)
	{
		plugin.onHdFrameStart();
		plugin.onHdPreSceneDraw(scene);
		bridge.onRendererFrame(plugin, "preSceneDraw");
		delegate.preSceneDraw(scene, cameraX, cameraY, cameraZ, cameraPitch, cameraYaw, minLevel, level, maxLevel, hideRoofIds);
	}

	@Override
	public void postSceneDraw(Scene scene)
	{
		delegate.postSceneDraw(scene);
		plugin.onHdFrameEnd();
	}

	@Override
	public void drawPass(Projection entityProjection, Scene scene, int pass)
	{
		delegate.drawPass(entityProjection, scene, pass);
	}

	@Override
	public void drawZoneOpaque(Projection entityProjection, Scene scene, int zx, int zz)
	{
		delegate.drawZoneOpaque(entityProjection, scene, zx, zz);
	}

	@Override
	public void drawZoneAlpha(Projection entityProjection, Scene scene, int level, int zx, int zz)
	{
		delegate.drawZoneAlpha(entityProjection, scene, level, zx, zz);
	}

	@Override
	public void drawDynamic(Projection worldProjection, Scene scene, TileObject tileObject, Renderable r, Model m, int orient, int x, int y, int z)
	{
		plugin.debugSelectedBuoyancyProximity("drawDynamic", r, tileObject == null ? -1L : tileObject.getHash(), x, z);
		plugin.recordRenderedObject(tileObject, orient, x, y, z);
		TidesBuoyancyController.BuoyancyTransform transform = plugin.computeBuoyancyTransform(tileObject, orient, x, z);
		if (transform == null)
		{
			delegate.drawDynamic(worldProjection, scene, tileObject, r, m, orient, x, y, z);
			return;
		}

		plugin.drawBuoyantModel(m, transform, () ->
			delegate.drawDynamic(worldProjection, scene, tileObject, r, m, orient, x, y + transform.getHeaveOffset(), z));
	}

	@Override
	public void drawDynamic(int renderThreadId, Projection worldProjection, Scene scene, TileObject tileObject, Renderable r, Model m, int orient, int x, int y, int z)
	{
		plugin.debugSelectedBuoyancyProximity("drawDynamicAsync", r, tileObject == null ? -1L : tileObject.getHash(), x, z);
		plugin.recordRenderedObject(tileObject, orient, x, y, z);
		TidesBuoyancyController.BuoyancyTransform transform = plugin.computeBuoyancyTransform(tileObject, orient, x, z);
		if (transform == null)
		{
			delegate.drawDynamic(renderThreadId, worldProjection, scene, tileObject, r, m, orient, x, y, z);
			return;
		}

		plugin.drawBuoyantModel(m, transform, () ->
			delegate.drawDynamic(renderThreadId, worldProjection, scene, tileObject, r, m, orient, x, y + transform.getHeaveOffset(), z));
	}

	@Override
	public void drawTemp(Projection worldProjection, Scene scene, GameObject gameObject, Model m, int orient, int x, int y, int z)
	{
		plugin.debugSelectedBuoyancyProximity("drawTemp", m, gameObject == null ? -1L : gameObject.getHash(), x, z);
		plugin.recordRenderedObject(gameObject, orient, x, y, z);
		TidesBuoyancyController.BuoyancyTransform transform = plugin.computeBuoyancyTransform(gameObject, orient, x, z);
		if (transform == null)
		{
			delegate.drawTemp(worldProjection, scene, gameObject, m, orient, x, y, z);
			return;
		}

		plugin.drawBuoyantModel(m, transform, () ->
			delegate.drawTemp(worldProjection, scene, gameObject, m, orient, x, y + transform.getHeaveOffset(), z));
	}

	@Override
	public void invalidateZone(Scene scene, int zx, int zz)
	{
		delegate.invalidateZone(scene, zx, zz);
	}
}
