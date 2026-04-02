package org.tides.hd;

import net.runelite.client.plugins.Plugin;

final class Hd117Handle
{
	final Plugin plugin;
	final Object renderer;
	final ClassLoader classLoader;
	final String rendererName;

	Hd117Handle(Plugin plugin, Object renderer, String rendererName)
	{
		this.plugin = plugin;
		this.renderer = renderer;
		this.classLoader = plugin.getClass().getClassLoader();
		this.rendererName = rendererName;
	}
}
