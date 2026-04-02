package org.tides.config;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Keybind;
import net.runelite.client.config.Range;

@ConfigGroup("tides")
public interface TidesConfig extends Config
{
	String SECTION_GLOBAL = "global";
	String SECTION_LAYER_1 = "layer1";
	String SECTION_LAYER_2 = "layer2";
	String SECTION_LAYER_3 = "layer3";
	String SECTION_LAYER_4 = "layer4";

	@ConfigSection(
		name = "Global FFT",
		description = "Preset and global FFT settings",
		position = 0
	)
	String globalSection = SECTION_GLOBAL;

	@ConfigSection(
		name = "Layer 1",
		description = "Broad swell layer",
		position = 1
	)
	String layer1Section = SECTION_LAYER_1;

	@ConfigSection(
		name = "Layer 2",
		description = "Secondary swell layer",
		position = 2
	)
	String layer2Section = SECTION_LAYER_2;

	@ConfigSection(
		name = "Layer 3",
		description = "Medium detail layer",
		position = 3
	)
	String layer3Section = SECTION_LAYER_3;

	@ConfigSection(
		name = "Layer 4",
		description = "Fine detail layer",
		position = 4
	)
	String layer4Section = SECTION_LAYER_4;

	@ConfigItem(
		keyName = "preset",
		name = "Preset",
		description = "Built-in FFT profiles derived from Garrett Gunnell's repo",
		position = 0,
		section = SECTION_GLOBAL
	)
	default TidesPreset preset()
	{
		return TidesPreset.DEEP_OCEAN;
	}

	@ConfigItem(
		keyName = "resolution",
		name = "Resolution",
		description = "FFT texture resolution",
		position = 1,
		section = SECTION_GLOBAL
	)
	default TidesResolution resolution()
	{
		return TidesResolution.FFT_128;
	}

	@Range(min = 0, max = 3)
	@ConfigItem(
		keyName = "lambda",
		name = "Lambda",
		description = "Global horizontal choppiness multiplier",
		position = 2,
		section = SECTION_GLOBAL
	)
	default double lambda()
	{
		return 0.35;
	}

	@ConfigItem(
		keyName = "foam",
		name = "Foam",
		description = "Render FFT crest foam on sharper waves",
		position = 3,
		section = SECTION_GLOBAL
	)
	default boolean foam()
	{
		return true;
	}

	@ConfigItem(
		keyName = "shorelineLock",
		name = "Shoreline lock",
		description = "Keep shoreline-connected vertices pinned closer to the original water plane",
		position = 4,
		section = SECTION_GLOBAL
	)
	default boolean shorelineLock()
	{
		return true;
	}

	@ConfigItem(
		keyName = "debugOverlay",
		name = "Debug overlay",
		description = "Draw detected water tiles and shoreline edges",
		position = 5,
		section = SECTION_GLOBAL
	)
	default boolean debugOverlay()
	{
		return false;
	}

	@ConfigItem(
		keyName = "selectBuoyantObjectHotkey",
		name = "Select buoyant object",
		description = "Debug hotkey to select or clear the hovered rendered object for buoyancy",
		position = 6,
		section = SECTION_GLOBAL
	)
	default Keybind selectBuoyantObjectHotkey()
	{
		return new Keybind(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK);
	}

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "lengthScale1", name = "Length scale", description = "Domain size for layer 1", position = 0, section = SECTION_LAYER_1)
	default int lengthScale1() { return 94; }
	@Range(min = 0, max = 500)
	@ConfigItem(keyName = "spectrum1Scale", name = "Spectrum 1 scale", description = "Primary spectrum energy for layer 1", position = 1, section = SECTION_LAYER_1)
	default double spectrum1Scale() { return 0.10; }
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "spectrum1WindSpeed", name = "Spectrum 1 wind", description = "Primary spectrum wind speed for layer 1", position = 2, section = SECTION_LAYER_1)
	default double spectrum1WindSpeed() { return 2.0; }
	@Range(min = 0, max = 360)
	@ConfigItem(keyName = "spectrum1WindDirection", name = "Spectrum 1 direction", description = "Primary spectrum wind direction for layer 1", position = 3, section = SECTION_LAYER_1)
	default int spectrum1WindDirection() { return 22; }
	@Range(min = 1, max = 100000000)
	@ConfigItem(keyName = "spectrum1Fetch", name = "Spectrum 1 fetch", description = "Primary spectrum fetch for layer 1", position = 4, section = SECTION_LAYER_1)
	default int spectrum1Fetch() { return 100000; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum1SpreadBlend", name = "Spectrum 1 spread", description = "Primary spectrum directional spread blend for layer 1", position = 5, section = SECTION_LAYER_1)
	default double spectrum1SpreadBlend() { return 0.642; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum1Swell", name = "Spectrum 1 swell", description = "Primary spectrum swell amount for layer 1", position = 6, section = SECTION_LAYER_1)
	default double spectrum1Swell() { return 1.0; }
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "spectrum1PeakEnhancement", name = "Spectrum 1 peak", description = "Primary spectrum peak enhancement for layer 1", position = 7, section = SECTION_LAYER_1)
	default double spectrum1PeakEnhancement() { return 1.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum1ShortWavesFade", name = "Spectrum 1 fade", description = "Primary spectrum short-wave fade for layer 1", position = 8, section = SECTION_LAYER_1)
	default double spectrum1ShortWavesFade() { return 0.025; }
	@Range(min = 0, max = 500)
	@ConfigItem(keyName = "spectrum2Scale", name = "Spectrum 2 scale", description = "Secondary spectrum energy for layer 1", position = 9, section = SECTION_LAYER_1)
	default double spectrum2Scale() { return 0.07; }
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "spectrum2WindSpeed", name = "Spectrum 2 wind", description = "Secondary spectrum wind speed for layer 1", position = 10, section = SECTION_LAYER_1)
	default double spectrum2WindSpeed() { return 2.0; }
	@Range(min = 0, max = 360)
	@ConfigItem(keyName = "spectrum2WindDirection", name = "Spectrum 2 direction", description = "Secondary spectrum wind direction for layer 1", position = 11, section = SECTION_LAYER_1)
	default int spectrum2WindDirection() { return 59; }
	@Range(min = 1, max = 100000000)
	@ConfigItem(keyName = "spectrum2Fetch", name = "Spectrum 2 fetch", description = "Secondary spectrum fetch for layer 1", position = 12, section = SECTION_LAYER_1)
	default int spectrum2Fetch() { return 1000; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum2SpreadBlend", name = "Spectrum 2 spread", description = "Secondary spectrum directional spread blend for layer 1", position = 13, section = SECTION_LAYER_1)
	default double spectrum2SpreadBlend() { return 0.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum2Swell", name = "Spectrum 2 swell", description = "Secondary spectrum swell amount for layer 1", position = 14, section = SECTION_LAYER_1)
	default double spectrum2Swell() { return 1.0; }
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "spectrum2PeakEnhancement", name = "Spectrum 2 peak", description = "Secondary spectrum peak enhancement for layer 1", position = 15, section = SECTION_LAYER_1)
	default double spectrum2PeakEnhancement() { return 1.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum2ShortWavesFade", name = "Spectrum 2 fade", description = "Secondary spectrum short-wave fade for layer 1", position = 16, section = SECTION_LAYER_1)
	default double spectrum2ShortWavesFade() { return 0.010; }

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "lengthScale2", name = "Length scale", description = "Domain size for layer 2", position = 0, section = SECTION_LAYER_2)
	default int lengthScale2() { return 128; }
	@Range(min = 0, max = 500)
	@ConfigItem(keyName = "spectrum3Scale", name = "Spectrum 3 scale", description = "Primary spectrum energy for layer 2", position = 1, section = SECTION_LAYER_2)
	default double spectrum3Scale() { return 0.25; }
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "spectrum3WindSpeed", name = "Spectrum 3 wind", description = "Primary spectrum wind speed for layer 2", position = 2, section = SECTION_LAYER_2)
	default double spectrum3WindSpeed() { return 20.0; }
	@Range(min = 0, max = 360)
	@ConfigItem(keyName = "spectrum3WindDirection", name = "Spectrum 3 direction", description = "Primary spectrum wind direction for layer 2", position = 3, section = SECTION_LAYER_2)
	default int spectrum3WindDirection() { return 97; }
	@Range(min = 1, max = 100000000)
	@ConfigItem(keyName = "spectrum3Fetch", name = "Spectrum 3 fetch", description = "Primary spectrum fetch for layer 2", position = 4, section = SECTION_LAYER_2)
	default int spectrum3Fetch() { return 100000000; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum3SpreadBlend", name = "Spectrum 3 spread", description = "Primary spectrum directional spread blend for layer 2", position = 5, section = SECTION_LAYER_2)
	default double spectrum3SpreadBlend() { return 0.14; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum3Swell", name = "Spectrum 3 swell", description = "Primary spectrum swell amount for layer 2", position = 6, section = SECTION_LAYER_2)
	default double spectrum3Swell() { return 1.0; }
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "spectrum3PeakEnhancement", name = "Spectrum 3 peak", description = "Primary spectrum peak enhancement for layer 2", position = 7, section = SECTION_LAYER_2)
	default double spectrum3PeakEnhancement() { return 1.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum3ShortWavesFade", name = "Spectrum 3 fade", description = "Primary spectrum short-wave fade for layer 2", position = 8, section = SECTION_LAYER_2)
	default double spectrum3ShortWavesFade() { return 0.50; }
	@Range(min = 0, max = 500)
	@ConfigItem(keyName = "spectrum4Scale", name = "Spectrum 4 scale", description = "Secondary spectrum energy for layer 2", position = 9, section = SECTION_LAYER_2)
	default double spectrum4Scale() { return 0.25; }
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "spectrum4WindSpeed", name = "Spectrum 4 wind", description = "Secondary spectrum wind speed for layer 2", position = 10, section = SECTION_LAYER_2)
	default double spectrum4WindSpeed() { return 20.0; }
	@Range(min = 0, max = 360)
	@ConfigItem(keyName = "spectrum4WindDirection", name = "Spectrum 4 direction", description = "Secondary spectrum wind direction for layer 2", position = 11, section = SECTION_LAYER_2)
	default int spectrum4WindDirection() { return 67; }
	@Range(min = 1, max = 100000000)
	@ConfigItem(keyName = "spectrum4Fetch", name = "Spectrum 4 fetch", description = "Secondary spectrum fetch for layer 2", position = 12, section = SECTION_LAYER_2)
	default int spectrum4Fetch() { return 1000000; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum4SpreadBlend", name = "Spectrum 4 spread", description = "Secondary spectrum directional spread blend for layer 2", position = 13, section = SECTION_LAYER_2)
	default double spectrum4SpreadBlend() { return 0.47; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum4Swell", name = "Spectrum 4 swell", description = "Secondary spectrum swell amount for layer 2", position = 14, section = SECTION_LAYER_2)
	default double spectrum4Swell() { return 1.0; }
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "spectrum4PeakEnhancement", name = "Spectrum 4 peak", description = "Secondary spectrum peak enhancement for layer 2", position = 15, section = SECTION_LAYER_2)
	default double spectrum4PeakEnhancement() { return 1.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum4ShortWavesFade", name = "Spectrum 4 fade", description = "Secondary spectrum short-wave fade for layer 2", position = 16, section = SECTION_LAYER_2)
	default double spectrum4ShortWavesFade() { return 0.50; }

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "lengthScale3", name = "Length scale", description = "Domain size for layer 3", position = 0, section = SECTION_LAYER_3)
	default int lengthScale3() { return 64; }
	@Range(min = 0, max = 500)
	@ConfigItem(keyName = "spectrum5Scale", name = "Spectrum 5 scale", description = "Primary spectrum energy for layer 3", position = 1, section = SECTION_LAYER_3)
	default double spectrum5Scale() { return 0.15; }
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "spectrum5WindSpeed", name = "Spectrum 5 wind", description = "Primary spectrum wind speed for layer 3", position = 2, section = SECTION_LAYER_3)
	default double spectrum5WindSpeed() { return 5.0; }
	@Range(min = 0, max = 360)
	@ConfigItem(keyName = "spectrum5WindDirection", name = "Spectrum 5 direction", description = "Primary spectrum wind direction for layer 3", position = 3, section = SECTION_LAYER_3)
	default int spectrum5WindDirection() { return 105; }
	@Range(min = 1, max = 100000000)
	@ConfigItem(keyName = "spectrum5Fetch", name = "Spectrum 5 fetch", description = "Primary spectrum fetch for layer 3", position = 4, section = SECTION_LAYER_3)
	default int spectrum5Fetch() { return 1000000; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum5SpreadBlend", name = "Spectrum 5 spread", description = "Primary spectrum directional spread blend for layer 3", position = 5, section = SECTION_LAYER_3)
	default double spectrum5SpreadBlend() { return 0.20; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum5Swell", name = "Spectrum 5 swell", description = "Primary spectrum swell amount for layer 3", position = 6, section = SECTION_LAYER_3)
	default double spectrum5Swell() { return 1.0; }
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "spectrum5PeakEnhancement", name = "Spectrum 5 peak", description = "Primary spectrum peak enhancement for layer 3", position = 7, section = SECTION_LAYER_3)
	default double spectrum5PeakEnhancement() { return 1.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum5ShortWavesFade", name = "Spectrum 5 fade", description = "Primary spectrum short-wave fade for layer 3", position = 8, section = SECTION_LAYER_3)
	default double spectrum5ShortWavesFade() { return 0.50; }
	@Range(min = 0, max = 500)
	@ConfigItem(keyName = "spectrum6Scale", name = "Spectrum 6 scale", description = "Secondary spectrum energy for layer 3", position = 9, section = SECTION_LAYER_3)
	default double spectrum6Scale() { return 0.10; }
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "spectrum6WindSpeed", name = "Spectrum 6 wind", description = "Secondary spectrum wind speed for layer 3", position = 10, section = SECTION_LAYER_3)
	default double spectrum6WindSpeed() { return 1.0; }
	@Range(min = 0, max = 360)
	@ConfigItem(keyName = "spectrum6WindDirection", name = "Spectrum 6 direction", description = "Secondary spectrum wind direction for layer 3", position = 11, section = SECTION_LAYER_3)
	default int spectrum6WindDirection() { return 19; }
	@Range(min = 1, max = 100000000)
	@ConfigItem(keyName = "spectrum6Fetch", name = "Spectrum 6 fetch", description = "Secondary spectrum fetch for layer 3", position = 12, section = SECTION_LAYER_3)
	default int spectrum6Fetch() { return 10000; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum6SpreadBlend", name = "Spectrum 6 spread", description = "Secondary spectrum directional spread blend for layer 3", position = 13, section = SECTION_LAYER_3)
	default double spectrum6SpreadBlend() { return 0.298; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum6Swell", name = "Spectrum 6 swell", description = "Secondary spectrum swell amount for layer 3", position = 14, section = SECTION_LAYER_3)
	default double spectrum6Swell() { return 0.695; }
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "spectrum6PeakEnhancement", name = "Spectrum 6 peak", description = "Secondary spectrum peak enhancement for layer 3", position = 15, section = SECTION_LAYER_3)
	default double spectrum6PeakEnhancement() { return 1.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum6ShortWavesFade", name = "Spectrum 6 fade", description = "Secondary spectrum short-wave fade for layer 3", position = 16, section = SECTION_LAYER_3)
	default double spectrum6ShortWavesFade() { return 0.50; }

	@Range(min = 1, max = 256)
	@ConfigItem(keyName = "lengthScale4", name = "Length scale", description = "Domain size for layer 4", position = 0, section = SECTION_LAYER_4)
	default int lengthScale4() { return 32; }
	@Range(min = 0, max = 500)
	@ConfigItem(keyName = "spectrum7Scale", name = "Spectrum 7 scale", description = "Primary spectrum energy for layer 4", position = 1, section = SECTION_LAYER_4)
	default double spectrum7Scale() { return 1.0; }
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "spectrum7WindSpeed", name = "Spectrum 7 wind", description = "Primary spectrum wind speed for layer 4", position = 2, section = SECTION_LAYER_4)
	default double spectrum7WindSpeed() { return 1.0; }
	@Range(min = 0, max = 360)
	@ConfigItem(keyName = "spectrum7WindDirection", name = "Spectrum 7 direction", description = "Primary spectrum wind direction for layer 4", position = 3, section = SECTION_LAYER_4)
	default int spectrum7WindDirection() { return 209; }
	@Range(min = 1, max = 100000000)
	@ConfigItem(keyName = "spectrum7Fetch", name = "Spectrum 7 fetch", description = "Primary spectrum fetch for layer 4", position = 4, section = SECTION_LAYER_4)
	default int spectrum7Fetch() { return 200000; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum7SpreadBlend", name = "Spectrum 7 spread", description = "Primary spectrum directional spread blend for layer 4", position = 5, section = SECTION_LAYER_4)
	default double spectrum7SpreadBlend() { return 0.56; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum7Swell", name = "Spectrum 7 swell", description = "Primary spectrum swell amount for layer 4", position = 6, section = SECTION_LAYER_4)
	default double spectrum7Swell() { return 1.0; }
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "spectrum7PeakEnhancement", name = "Spectrum 7 peak", description = "Primary spectrum peak enhancement for layer 4", position = 7, section = SECTION_LAYER_4)
	default double spectrum7PeakEnhancement() { return 1.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum7ShortWavesFade", name = "Spectrum 7 fade", description = "Primary spectrum short-wave fade for layer 4", position = 8, section = SECTION_LAYER_4)
	default double spectrum7ShortWavesFade() { return 0.0001; }
	@Range(min = 0, max = 500)
	@ConfigItem(keyName = "spectrum8Scale", name = "Spectrum 8 scale", description = "Secondary spectrum energy for layer 4", position = 9, section = SECTION_LAYER_4)
	default double spectrum8Scale() { return 0.23; }
	@Range(min = 0, max = 100)
	@ConfigItem(keyName = "spectrum8WindSpeed", name = "Spectrum 8 wind", description = "Secondary spectrum wind speed for layer 4", position = 10, section = SECTION_LAYER_4)
	default double spectrum8WindSpeed() { return 1.0; }
	@Range(min = 0, max = 360)
	@ConfigItem(keyName = "spectrum8WindDirection", name = "Spectrum 8 direction", description = "Secondary spectrum wind direction for layer 4", position = 11, section = SECTION_LAYER_4)
	default int spectrum8WindDirection() { return 0; }
	@Range(min = 1, max = 100000000)
	@ConfigItem(keyName = "spectrum8Fetch", name = "Spectrum 8 fetch", description = "Secondary spectrum fetch for layer 4", position = 12, section = SECTION_LAYER_4)
	default int spectrum8Fetch() { return 1000; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum8SpreadBlend", name = "Spectrum 8 spread", description = "Secondary spectrum directional spread blend for layer 4", position = 13, section = SECTION_LAYER_4)
	default double spectrum8SpreadBlend() { return 0.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum8Swell", name = "Spectrum 8 swell", description = "Secondary spectrum swell amount for layer 4", position = 14, section = SECTION_LAYER_4)
	default double spectrum8Swell() { return 0.0; }
	@Range(min = 1, max = 20)
	@ConfigItem(keyName = "spectrum8PeakEnhancement", name = "Spectrum 8 peak", description = "Secondary spectrum peak enhancement for layer 4", position = 15, section = SECTION_LAYER_4)
	default double spectrum8PeakEnhancement() { return 1.0; }
	@Range(min = 0, max = 1000)
	@ConfigItem(keyName = "spectrum8ShortWavesFade", name = "Spectrum 8 fade", description = "Secondary spectrum short-wave fade for layer 4", position = 16, section = SECTION_LAYER_4)
	default double spectrum8ShortWavesFade() { return 0.0001; }
}
