package org.tides.water.fft;

import java.util.Arrays;
import org.tides.config.TidesPreset;

public final class TidesFftProfile
{
	private static final float LENGTH_SCALE_UNIT = 64.0f;

	private final TidesPreset preset;
	private final int resolution;
	private final float lambda;
	private final int[] lengthScales;
	private final TidesSpectrumParameters[] spectrums;

	public TidesFftProfile(TidesPreset preset, int resolution, float lambda, int[] lengthScales, TidesSpectrumParameters[] spectrums)
	{
		this.preset = preset;
		this.resolution = resolution;
		this.lambda = lambda;
		this.lengthScales = Arrays.copyOf(lengthScales, lengthScales.length);
		this.spectrums = Arrays.copyOf(spectrums, spectrums.length);
	}

	public TidesPreset preset()
	{
		return preset;
	}

	public int resolution()
	{
		return resolution;
	}

	public float lambda()
	{
		return lambda;
	}

	public int bandLengthScale(int band)
	{
		return lengthScales[band];
	}

	public float bandDomain(int band)
	{
		return Math.max(256.0f, bandLengthScale(band) * LENGTH_SCALE_UNIT);
	}

	public TidesSpectrumParameters spectrum(int index)
	{
		return spectrums[index];
	}

	public float bandAverageScale(int band)
	{
		TidesSpectrumParameters a = spectrum(band * 2);
		TidesSpectrumParameters b = spectrum((band * 2) + 1);
		return (a.scale() + b.scale()) * 0.5f;
	}

	public float bandAverageWindSpeed(int band)
	{
		TidesSpectrumParameters a = spectrum(band * 2);
		TidesSpectrumParameters b = spectrum((band * 2) + 1);
		return (a.windSpeed() + b.windSpeed()) * 0.5f;
	}

	public float weightedAverageWindSpeed()
	{
		float weightedSum = 0.0f;
		float weight = 0.0f;
		for (TidesSpectrumParameters spectrum : spectrums)
		{
			float w = Math.max(0.001f, spectrum.scale());
			weightedSum += spectrum.windSpeed() * w;
			weight += w;
		}
		return weight <= 0.0f ? 1.0f : weightedSum / weight;
	}

	public float[] averagedWindDirection()
	{
		float x = 0.0f;
		float y = 0.0f;
		for (TidesSpectrumParameters spectrum : spectrums)
		{
			float radians = (float) Math.toRadians(spectrum.windDirectionDegrees());
			float weight = Math.max(0.001f, spectrum.scale() * Math.max(0.5f, spectrum.windSpeed()));
			x += Math.cos(radians) * weight;
			y += Math.sin(radians) * weight;
		}

		float length = (float) Math.sqrt((x * x) + (y * y));
		if (length < 1e-4f)
		{
			return new float[] {0.94f, 0.34f};
		}
		return new float[] {x / length, y / length};
	}

	public float swellFactor()
	{
		float scale = bandAverageScale(0) + (bandAverageScale(1) * 0.7f);
		return Math.max(0.18f, Math.min(1.35f, scale * 1.7f));
	}

	public float swellHeight()
	{
		float scale = bandAverageScale(0) + (bandAverageScale(1) * 0.6f);
		return Math.max(18.0f, 28.0f + (scale * 36.0f));
	}

	public float swellLength0()
	{
		return Math.max(3072.0f, bandDomain(0) * 1.35f);
	}

	public float swellLength1()
	{
		return Math.max(4608.0f, Math.max(bandDomain(0), bandDomain(1)) * 1.95f);
	}

	public float motionScale()
	{
		return Math.max(2.4f, 1.2f + (weightedAverageWindSpeed() * 0.32f));
	}

	public float swellSpeed0()
	{
		float broadWind = (bandAverageWindSpeed(0) * 0.75f) + (bandAverageWindSpeed(1) * 0.35f);
		return Math.max(2.4f, 0.8f + (broadWind * 0.36f));
	}

	public float swellSpeed1()
	{
		float broadWind = (bandAverageWindSpeed(0) * 0.55f) + (bandAverageWindSpeed(1) * 0.20f);
		return Math.max(1.35f, 0.45f + (broadWind * 0.22f));
	}

	public String signature()
	{
		StringBuilder signature = new StringBuilder()
			.append(preset).append('|')
			.append(resolution).append('|')
			.append(lambda).append('|');
		for (int lengthScale : lengthScales)
		{
			signature.append(lengthScale).append(',');
		}
		for (TidesSpectrumParameters spectrum : spectrums)
		{
			signature.append(spectrum.scale()).append(',')
				.append(spectrum.windSpeed()).append(',')
				.append(spectrum.windDirectionDegrees()).append(',')
				.append(spectrum.fetch()).append(',')
				.append(spectrum.spreadBlend()).append(',')
				.append(spectrum.swell()).append(',')
				.append(spectrum.peakEnhancement()).append(',')
				.append(spectrum.shortWavesFade()).append(';');
		}
		return signature.toString();
	}
}
