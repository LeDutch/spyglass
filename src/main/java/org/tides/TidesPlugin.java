package org.tides;

import com.google.inject.Provides;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@Slf4j
@PluginDescriptor(
	name = "Tides"
)
public class TidesPlugin extends Plugin
{
	@Getter
	@Inject
	private TidesConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private TidesOverlay overlay;

	@Inject
	private KeyManager keyManager;

	@Getter
	private boolean enabled = true;

	private TidesKeyListener keyListener;

	@Override
	protected void startUp()
	{
		log.info("Tides started");
		overlayManager.add(overlay);
		keyListener = new TidesKeyListener(this);
		keyManager.registerKeyListener(keyListener);
	}

	@Override
	protected void shutDown()
	{
		log.info("Tides stopped");
		overlayManager.remove(overlay);
		if (keyListener != null)
		{
			keyManager.unregisterKeyListener(keyListener);
			keyListener = null;
		}
		enabled = false;
	}

	public void toggleEnabled()
	{
		enabled = !enabled;
	}

	@Provides
	TidesConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(TidesConfig.class);
	}
}
