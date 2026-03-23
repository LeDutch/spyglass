package org.tides;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("tides")
public interface TidesConfig extends Config
{
	@ConfigItem(
		keyName = "toggleKey",
		name = "Toggle key",
		description = "Keybind to toggle the water mesh"
	)
	default Keybind toggleKey()
	{
		return Keybind.NOT_SET;
	}

	@ConfigItem(
		keyName = "debugOverlay",
		name = "Debug overlay",
		description = "Show hovered tile texture debug information"
	)
	default boolean debugOverlay()
	{
		return true;
	}

	@Range(min = 1, max = 32)
	@ConfigItem(
		keyName = "subdivisions",
		name = "Subdivisions",
		description = "Sub-tile subdivisions per tile"
	)
	default int subdivisions()
	{
		return 4;
	}

	@Range(min = 0, max = 96)
	@ConfigItem(
		keyName = "amplitude",
		name = "Amplitude",
		description = "Wave height in local units"
	)
	default int amplitude()
	{
		return 18;
	}

	@Range(min = 16, max = 512)
	@ConfigItem(
		keyName = "wavelength",
		name = "Wavelength",
		description = "Distance between peaks in local units"
	)
	default int wavelength()
	{
		return 160;
	}

	@Range(min = 1, max = 400)
	@ConfigItem(
		keyName = "speed",
		name = "Speed",
		description = "Wave speed"
	)
	default int speed()
	{
		return 55;
	}

	@Range(min = 0, max = 100)
	@ConfigItem(
		keyName = "opacity",
		name = "Opacity",
		description = "Mesh opacity"
	)
	default int opacity()
	{
		return 60;
	}

	@ConfigItem(
		keyName = "wireframe",
		name = "Wireframe",
		description = "Draw cell edges to inspect the generated mesh"
	)
	default boolean wireframe()
	{
		return false;
	}
}
