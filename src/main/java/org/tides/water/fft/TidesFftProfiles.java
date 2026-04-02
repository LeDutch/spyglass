package org.tides.water.fft;

import net.runelite.client.config.ConfigManager;
import org.tides.config.TidesConfig;
import org.tides.config.TidesPreset;
import org.tides.config.TidesResolution;

public final class TidesFftProfiles
{
	private static final String GROUP = "tides";

	private TidesFftProfiles()
	{
	}

	public static TidesFftProfile fromConfig(TidesConfig config)
	{
		if (config.preset() == TidesPreset.DEEP_OCEAN)
		{
			return deepOcean();
		}
		return custom(config);
	}

	public static void applyPresetToConfig(TidesPreset preset, ConfigManager configManager)
	{
		if (preset == TidesPreset.CUSTOM)
		{
			return;
		}

		TidesFftProfile profile = preset == TidesPreset.DEEP_OCEAN ? deepOcean() : null;
		if (profile == null)
		{
			return;
		}

		configManager.setConfiguration(GROUP, "resolution", TidesResolution.FFT_128.name());
		configManager.setConfiguration(GROUP, "lambda", profile.lambda());
		configManager.setConfiguration(GROUP, "lengthScale1", profile.bandLengthScale(0));
		configManager.setConfiguration(GROUP, "lengthScale2", profile.bandLengthScale(1));
		configManager.setConfiguration(GROUP, "lengthScale3", profile.bandLengthScale(2));
		configManager.setConfiguration(GROUP, "lengthScale4", profile.bandLengthScale(3));

		for (int i = 0; i < 8; i++)
		{
			TidesSpectrumParameters spectrum = profile.spectrum(i);
			int spectrumNumber = i + 1;
			configManager.setConfiguration(GROUP, "spectrum" + spectrumNumber + "Scale", spectrum.scale());
			configManager.setConfiguration(GROUP, "spectrum" + spectrumNumber + "WindSpeed", spectrum.windSpeed());
			configManager.setConfiguration(GROUP, "spectrum" + spectrumNumber + "WindDirection", Math.round(spectrum.windDirectionDegrees()));
			configManager.setConfiguration(GROUP, "spectrum" + spectrumNumber + "Fetch", Math.round(spectrum.fetch()));
			configManager.setConfiguration(GROUP, "spectrum" + spectrumNumber + "SpreadBlend", spectrum.spreadBlend());
			configManager.setConfiguration(GROUP, "spectrum" + spectrumNumber + "Swell", spectrum.swell());
			configManager.setConfiguration(GROUP, "spectrum" + spectrumNumber + "PeakEnhancement", spectrum.peakEnhancement());
			configManager.setConfiguration(GROUP, "spectrum" + spectrumNumber + "ShortWavesFade", spectrum.shortWavesFade());
		}
	}

	private static TidesFftProfile custom(TidesConfig config)
	{
		return new TidesFftProfile(
			config.preset(),
			config.resolution().size(),
			(float) config.lambda(),
			new int[] {
				config.lengthScale1(),
				config.lengthScale2(),
				config.lengthScale3(),
				config.lengthScale4()
			},
			new TidesSpectrumParameters[] {
				spectrum(config.spectrum1Scale(), config.spectrum1WindSpeed(), config.spectrum1WindDirection(), config.spectrum1Fetch(), config.spectrum1SpreadBlend(), config.spectrum1Swell(), config.spectrum1PeakEnhancement(), config.spectrum1ShortWavesFade()),
				spectrum(config.spectrum2Scale(), config.spectrum2WindSpeed(), config.spectrum2WindDirection(), config.spectrum2Fetch(), config.spectrum2SpreadBlend(), config.spectrum2Swell(), config.spectrum2PeakEnhancement(), config.spectrum2ShortWavesFade()),
				spectrum(config.spectrum3Scale(), config.spectrum3WindSpeed(), config.spectrum3WindDirection(), config.spectrum3Fetch(), config.spectrum3SpreadBlend(), config.spectrum3Swell(), config.spectrum3PeakEnhancement(), config.spectrum3ShortWavesFade()),
				spectrum(config.spectrum4Scale(), config.spectrum4WindSpeed(), config.spectrum4WindDirection(), config.spectrum4Fetch(), config.spectrum4SpreadBlend(), config.spectrum4Swell(), config.spectrum4PeakEnhancement(), config.spectrum4ShortWavesFade()),
				spectrum(config.spectrum5Scale(), config.spectrum5WindSpeed(), config.spectrum5WindDirection(), config.spectrum5Fetch(), config.spectrum5SpreadBlend(), config.spectrum5Swell(), config.spectrum5PeakEnhancement(), config.spectrum5ShortWavesFade()),
				spectrum(config.spectrum6Scale(), config.spectrum6WindSpeed(), config.spectrum6WindDirection(), config.spectrum6Fetch(), config.spectrum6SpreadBlend(), config.spectrum6Swell(), config.spectrum6PeakEnhancement(), config.spectrum6ShortWavesFade()),
				spectrum(config.spectrum7Scale(), config.spectrum7WindSpeed(), config.spectrum7WindDirection(), config.spectrum7Fetch(), config.spectrum7SpreadBlend(), config.spectrum7Swell(), config.spectrum7PeakEnhancement(), config.spectrum7ShortWavesFade()),
				spectrum(config.spectrum8Scale(), config.spectrum8WindSpeed(), config.spectrum8WindDirection(), config.spectrum8Fetch(), config.spectrum8SpreadBlend(), config.spectrum8Swell(), config.spectrum8PeakEnhancement(), config.spectrum8ShortWavesFade())
			}
		);
	}

	private static TidesFftProfile deepOcean()
	{
		return new TidesFftProfile(
			TidesPreset.DEEP_OCEAN,
			TidesResolution.FFT_128.size(),
			0.35f,
			new int[] {160, 224, 112, 56},
			new TidesSpectrumParameters[] {
				spectrum(0.040, 1.5, 22.0, 100000.0, 0.642, 1.0, 1.0, 0.025),
				spectrum(0.028, 1.5, 59.0, 1000.0, 0.0, 1.0, 1.0, 0.010),
				spectrum(0.090, 8.0, 97.0, 100000000.0, 0.14, 1.0, 1.0, 0.50),
				spectrum(0.090, 8.0, 67.0, 1000000.0, 0.47, 1.0, 1.0, 0.50),
				spectrum(0.050, 3.0, 105.0, 1000000.0, 0.20, 1.0, 1.0, 0.50),
				spectrum(0.032, 1.0, 19.0, 10000.0, 0.298, 0.695, 1.0, 0.50),
				spectrum(0.160, 1.0, 209.0, 200000.0, 0.56, 1.0, 1.0, 0.0001),
				spectrum(0.045, 1.0, 0.0, 1000.0, 0.0, 0.0, 1.0, 0.0001)
			}
		);
	}

	private static TidesSpectrumParameters spectrum(
		double scale,
		double windSpeed,
		double windDirection,
		double fetch,
		double spreadBlend,
		double swell,
		double peakEnhancement,
		double shortWavesFade)
	{
		return new TidesSpectrumParameters(
			(float) scale,
			(float) windSpeed,
			(float) windDirection,
			(float) fetch,
			(float) spreadBlend,
			(float) swell,
			(float) peakEnhancement,
			(float) shortWavesFade
		);
	}
}
