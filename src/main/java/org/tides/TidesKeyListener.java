package org.tides;

import java.awt.event.KeyEvent;
import net.runelite.client.config.Keybind;
import net.runelite.client.input.KeyListener;

public class TidesKeyListener implements KeyListener
{
	private final TidesPlugin plugin;

	public TidesKeyListener(TidesPlugin plugin)
	{
		this.plugin = plugin;
	}

	@Override
	public void keyPressed(KeyEvent e)
	{
		Keybind toggleKeybind = plugin.getConfig().toggleKey();
		if (toggleKeybind != null && toggleKeybind.matches(e))
		{
			plugin.toggleEnabled();
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

	@Override
	public boolean isEnabledOnLoginScreen()
	{
		return false;
	}

	@Override
	public void focusLost()
	{
	}
}
