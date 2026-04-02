package org.tides.water.fft;

public final class TidesSpectrumParameters
{
	private final float scale;
	private final float windSpeed;
	private final float windDirectionDegrees;
	private final float fetch;
	private final float spreadBlend;
	private final float swell;
	private final float peakEnhancement;
	private final float shortWavesFade;

	public TidesSpectrumParameters(
		float scale,
		float windSpeed,
		float windDirectionDegrees,
		float fetch,
		float spreadBlend,
		float swell,
		float peakEnhancement,
		float shortWavesFade)
	{
		this.scale = scale;
		this.windSpeed = windSpeed;
		this.windDirectionDegrees = windDirectionDegrees;
		this.fetch = fetch;
		this.spreadBlend = spreadBlend;
		this.swell = swell;
		this.peakEnhancement = peakEnhancement;
		this.shortWavesFade = shortWavesFade;
	}

	public float scale()
	{
		return scale;
	}

	public float windSpeed()
	{
		return windSpeed;
	}

	public float windDirectionDegrees()
	{
		return windDirectionDegrees;
	}

	public float fetch()
	{
		return fetch;
	}

	public float spreadBlend()
	{
		return spreadBlend;
	}

	public float swell()
	{
		return swell;
	}

	public float peakEnhancement()
	{
		return peakEnhancement;
	}

	public float shortWavesFade()
	{
		return shortWavesFade;
	}
}
