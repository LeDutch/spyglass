package org.tides;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("tides")
public interface TidesConfig extends Config
{
	@ConfigItem(
		keyName = "bridge117Hd",
		name = "Bridge 117HD",
		description = "Install the tides renderer bridge when 117HD is active"
	)
	default boolean bridge117Hd()
	{
		return true;
	}

	@ConfigItem(
		keyName = "hotPatch117HdShaders",
		name = "Hot-patch 117HD shaders",
		description = "Reflect into 117HD and redirect its shader/resource path to patched tides copies"
	)
	default boolean hotPatch117HdShaders()
	{
		return true;
	}

	@ConfigItem(
		keyName = "inject117HdWaterState",
		name = "Inject 117HD water state",
		description = "Reflect into the active 117HD renderer and mark tides water tiles in the live scene context"
	)
	default boolean inject117HdWaterState()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugOverlay",
		name = "Debug overlay",
		description = "Draw detected water tiles and shoreline edges"
	)
	default boolean debugOverlay()
	{
		return false;
	}

	@Range(min = 0, max = 96)
	@ConfigItem(
		keyName = "amplitude",
		name = "Amplitude",
		description = "Wave height in local units for renderer integration"
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
		description = "Wave phase speed"
	)
	default int speed()
	{
		return 55;
	}

	@ConfigItem(
		keyName = "shorelineFade",
		name = "Shoreline fade",
		description = "Dampen wave height near non-water neighbors"
	)
	default boolean shorelineFade()
	{
		return true;
	}
}
