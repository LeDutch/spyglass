package org.tides.hd;

import java.lang.reflect.Field;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.hooks.DrawCallbacks;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import org.tides.TidesPlugin;

@Slf4j
@Singleton
public class HdRendererBridge
{
	private static final String HD_PLUGIN_CLASS = "rs117.hd.HdPlugin";
	private static final String HD_RENDERER_PACKAGE = "rs117.hd.renderer.";

	private final Client client;
	private final ClientThread clientThread;
	private final PluginManager pluginManager;
	private final Hd117ShaderPatcher shaderPatcher;
	private final Hd117SceneStateInjector sceneStateInjector;
	private final Hd117FftWater fftWater;

	private Plugin hdPlugin;
	private Hd117Handle handle;
	private DrawCallbacks delegate;
	private HdRendererProxy proxy;
	private String rendererName = "none";
	private String status = "117 HD not found";

	@Inject
	public HdRendererBridge(
		Client client,
		ClientThread clientThread,
		PluginManager pluginManager,
		Hd117ShaderPatcher shaderPatcher,
		Hd117SceneStateInjector sceneStateInjector,
		Hd117FftWater fftWater)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.pluginManager = pluginManager;
		this.shaderPatcher = shaderPatcher;
		this.sceneStateInjector = sceneStateInjector;
		this.fftWater = fftWater;
	}

	public void refresh(TidesPlugin plugin)
	{
		hdPlugin = findHdPlugin();
		DrawCallbacks current = client.getDrawCallbacks();

		if (hdPlugin == null)
		{
			uninstallIfInstalled(current);
			restorePatchedState();
			handle = null;
			rendererName = "none";
			status = "117 HD not loaded";
			return;
		}

		handle = reflectHandle(hdPlugin);
		rendererName = handle == null ? "renderer pending" : handle.rendererName;
		if (handle == null)
		{
			uninstallIfInstalled(current);
			restorePatchedState();
			status = "117 HD found, renderer pending";
			return;
		}

		if (current == proxy)
		{
			refreshPatchedState(plugin, "proxy");
			return;
		}

		if (!isHdRenderer(current))
		{
			uninstallIfInstalled(current);
			restorePatchedState();
			status = "117 HD found, renderer unavailable";
			return;
		}

		delegate = current;
		proxy = new HdRendererProxy(plugin, this, delegate);
		client.setDrawCallbacks(proxy);
		refreshPatchedState(plugin, "hook");
		log.info("Installed tides bridge on {}", rendererName);
	}

	public void shutDown()
	{
		Hd117Handle handleToRestore = handle != null ? handle : hdPlugin == null ? null : reflectHandle(hdPlugin);
		DrawCallbacks delegateToRestore = delegate;
		HdRendererProxy proxyToRemove = proxy;
		String rendererNameToRestore = rendererName;

		clientThread.invoke(() ->
		{
			uninstallProxy(proxyToRemove, delegateToRestore, rendererNameToRestore);
			restorePatchedState(handleToRestore);
		});

		proxy = null;
		delegate = null;
		hdPlugin = null;
		handle = null;
		rendererName = "none";
		status = "inactive";
	}

	public boolean isHooked()
	{
		return client.getDrawCallbacks() == proxy;
	}

	public String getStatus()
	{
		return status;
	}

	public String getRendererName()
	{
		return rendererName;
	}

	public String getShaderStatus()
	{
		return shaderPatcher.getStatus();
	}

	public String getSceneInjectionStatus()
	{
		return sceneStateInjector.getStatus();
	}

	public String getFftStatus()
	{
		return fftWater.getStatus();
	}

	void noteSceneCallback(String callback)
	{
		status = "Hooked " + rendererName + " via " + callback;
	}

	void onRendererSceneChanged(TidesPlugin plugin, String callback)
	{
		refreshPatchedState(plugin, callback);
	}

	void onRendererFrame(TidesPlugin plugin, String callback)
	{
		refreshPatchedState(plugin, callback);
	}

	private void uninstallIfInstalled(DrawCallbacks current)
	{
		if (proxy != null && current == proxy)
		{
			client.setDrawCallbacks(delegate);
			log.info("Removed tides bridge from {}", rendererName);
		}

		proxy = null;
		delegate = null;
	}

	private void uninstallProxy(HdRendererProxy proxyToRemove, DrawCallbacks delegateToRestore, String rendererNameToRestore)
	{
		if (proxyToRemove != null && client.getDrawCallbacks() == proxyToRemove)
		{
			client.setDrawCallbacks(delegateToRestore);
			log.info("Removed tides bridge from {}", rendererNameToRestore);
		}
	}

	private void refreshPatchedState(TidesPlugin plugin, String source)
	{
		if (handle == null)
		{
			status = "117 HD renderer unavailable";
			return;
		}

		shaderPatcher.refresh(handle, plugin.getConfig());
		fftWater.refresh(handle, plugin);
		sceneStateInjector.refresh(handle, plugin);
		status = "Hooked " + rendererName
			+ " via " + source
			+ " | " + shaderPatcher.getStatus()
			+ " | " + sceneStateInjector.getStatus();
	}

	private void restorePatchedState()
	{
		restorePatchedState(handle);
	}

	private void restorePatchedState(Hd117Handle handleToRestore)
	{
		fftWater.reset();
		if (handleToRestore != null)
		{
			shaderPatcher.restore(handleToRestore);
		}
		sceneStateInjector.reset();
	}

	private Plugin findHdPlugin()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (HD_PLUGIN_CLASS.equals(plugin.getClass().getName()))
			{
				return plugin;
			}
		}

		return null;
	}

	private static boolean isHdRenderer(DrawCallbacks drawCallbacks)
	{
		return drawCallbacks != null && drawCallbacks.getClass().getName().startsWith(HD_RENDERER_PACKAGE);
	}

	private static Hd117Handle reflectHandle(Plugin hdPlugin)
	{
		try
		{
			Field rendererField = hdPlugin.getClass().getField("renderer");
			Object renderer = rendererField.get(hdPlugin);
			if (renderer == null)
			{
				return null;
			}
			return new Hd117Handle(hdPlugin, renderer, renderer.getClass().getSimpleName());
		}
		catch (ReflectiveOperationException ex)
		{
			log.debug("Unable to inspect 117 HD renderer", ex);
			return null;
		}
	}
}
