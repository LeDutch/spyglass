package org.tides.hd;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import org.tides.TidesPlugin;
import org.tides.config.TidesConfig;
import org.tides.water.TidesSceneSnapshot;
import org.tides.water.TidesWaterTile;
import org.tides.water.fft.TidesFftProfile;
import org.tides.water.fft.TidesFftProfiles;
import org.tides.water.fft.TidesSpectrumParameters;

import static org.lwjgl.opengl.GL11C.GL_FALSE;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_LINEAR_MIPMAP_LINEAR;
import static org.lwjgl.opengl.GL11C.GL_NEAREST;
import static org.lwjgl.opengl.GL11C.GL_RED;
import static org.lwjgl.opengl.GL11C.GL_REPEAT;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL11C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL11C.glBindTexture;
import static org.lwjgl.opengl.GL11C.glDeleteTextures;
import static org.lwjgl.opengl.GL11C.glGenTextures;
import static org.lwjgl.opengl.GL11C.glTexImage2D;
import static org.lwjgl.opengl.GL13C.GL_TEXTURE0;
import static org.lwjgl.opengl.GL13C.glActiveTexture;
import static org.lwjgl.opengl.GL15C.GL_DYNAMIC_DRAW;
import static org.lwjgl.opengl.GL15C.glBindBuffer;
import static org.lwjgl.opengl.GL15C.glBufferData;
import static org.lwjgl.opengl.GL15C.glDeleteBuffers;
import static org.lwjgl.opengl.GL15C.glGenBuffers;
import static org.lwjgl.opengl.GL20C.GL_COMPILE_STATUS;
import static org.lwjgl.opengl.GL20C.GL_LINK_STATUS;
import static org.lwjgl.opengl.GL20C.glAttachShader;
import static org.lwjgl.opengl.GL20C.glCompileShader;
import static org.lwjgl.opengl.GL20C.glCreateProgram;
import static org.lwjgl.opengl.GL20C.glCreateShader;
import static org.lwjgl.opengl.GL20C.glDeleteProgram;
import static org.lwjgl.opengl.GL20C.glDeleteShader;
import static org.lwjgl.opengl.GL20C.glGetProgramInfoLog;
import static org.lwjgl.opengl.GL20C.glGetProgrami;
import static org.lwjgl.opengl.GL20C.glGetShaderInfoLog;
import static org.lwjgl.opengl.GL20C.glGetShaderi;
import static org.lwjgl.opengl.GL20C.glGetUniformLocation;
import static org.lwjgl.opengl.GL20C.glLinkProgram;
import static org.lwjgl.opengl.GL20C.glShaderSource;
import static org.lwjgl.opengl.GL20C.glUniform1f;
import static org.lwjgl.opengl.GL20C.glUniform1i;
import static org.lwjgl.opengl.GL20C.glUniform2f;
import static org.lwjgl.opengl.GL20C.glUseProgram;
import static org.lwjgl.opengl.GL30C.GL_R16F;
import static org.lwjgl.opengl.GL30C.GL_RG;
import static org.lwjgl.opengl.GL30C.GL_RG16F;
import static org.lwjgl.opengl.GL30C.GL_RG32F;
import static org.lwjgl.opengl.GL30C.GL_RGBA16F;
import static org.lwjgl.opengl.GL30C.GL_RGBA32F;
import static org.lwjgl.opengl.GL30C.glGenerateMipmap;
import static org.lwjgl.opengl.GL42C.GL_ALL_BARRIER_BITS;
import static org.lwjgl.opengl.GL42C.GL_READ_ONLY;
import static org.lwjgl.opengl.GL42C.GL_READ_WRITE;
import static org.lwjgl.opengl.GL42C.GL_SHADER_IMAGE_ACCESS_BARRIER_BIT;
import static org.lwjgl.opengl.GL42C.GL_WRITE_ONLY;
import static org.lwjgl.opengl.GL42C.glBindImageTexture;
import static org.lwjgl.opengl.GL42C.glMemoryBarrier;
import static org.lwjgl.opengl.GL43C.GL_COMPUTE_SHADER;
import static org.lwjgl.opengl.GL43C.GL_CURRENT_PROGRAM;
import static org.lwjgl.opengl.GL43C.GL_SHADER_STORAGE_BUFFER;
import static org.lwjgl.opengl.GL43C.GL_TEXTURE_2D;
import static org.lwjgl.opengl.GL43C.glBindBufferBase;
import static org.lwjgl.opengl.GL43C.glDispatchCompute;
import static org.lwjgl.opengl.GL43C.glGetInteger;
import static org.lwjgl.opengl.GL43C.glTexStorage2D;

@Slf4j
@Singleton
public class Hd117FftWater
{
	private static final int MAX_FFT_SIZE = 256;
	private static final int BAND_COUNT = 4;
	private static final int SPECTRA_PER_BAND = 2;
	private static final int SPECTRUM_COUNT = BAND_COUNT * SPECTRA_PER_BAND;
	private static final int SPECTRUM_FLOAT_STRIDE = 8;
	private static final int SPECTRUM_BUFFER_BINDING = 3;
	private static final int[] FFT_DISPLACEMENT_TEXTURE_UNITS = {
		GL_TEXTURE0 + 14,
		GL_TEXTURE0 + 16,
		GL_TEXTURE0 + 18,
		GL_TEXTURE0 + 20
	};
	private static final int[] FFT_SLOPE_TEXTURE_UNITS = {
		GL_TEXTURE0 + 15,
		GL_TEXTURE0 + 17,
		GL_TEXTURE0 + 19,
		GL_TEXTURE0 + 21
	};
	private static final int TEXTURE_UNIT_WATER_META = GL_TEXTURE0 + 22;
	private static final int[] BAND_SEEDS = {1337, 1729, 2081, 3253};
	private static final float[] BAND_TIME_SCALES = {2.2f, 2.5f, 2.9f, 3.3f};
	private static final float[] BAND_CHOPPY_LAMBDA = {0.72f, 0.50f, 0.18f, 0.06f};
	private static final float[] BAND_HEIGHT_VISUAL_SCALES = {1.0f, 0.58f, 0.30f, 0.10f};
	private static final float HEIGHT_SCALE_UNIT = 320.0f;

	private int initProgram;
	private int packProgram;
	private int updateProgram;
	private int fftProgram;
	private int finalizeProgram;

	private final int[] initialSpectrumTextures = new int[BAND_COUNT];
	private final int[] spectrumTextures = new int[BAND_COUNT];
	private final int[] pingTextures = new int[BAND_COUNT];
	private final int[] displacementTextures = new int[BAND_COUNT];
	private final int[] slopeTextures = new int[BAND_COUNT];

	private int spectrumSettingsBuffer;
	private int waterMetaTexture;

	private int resolution;
	private int log2Resolution;
	private int waterMetaWidth;
	private int waterMetaHeight;
	private int waterMetaOriginX;
	private int waterMetaOriginY;
	private int lastWindBucket = Integer.MIN_VALUE;
	private TidesSceneSnapshot lastMetaSnapshot = TidesSceneSnapshot.empty();
	private String signature;
	private String status = "fft idle";

	public String getStatus()
	{
		return status;
	}

	public void refresh(Hd117Handle handle, TidesPlugin plugin)
	{
		TidesConfig config = plugin.getConfig();
		TidesFftProfile profile = TidesFftProfiles.fromConfig(config);
		if (!hasCurrentGlContext())
		{
			status = "fft waiting for GL context";
			return;
		}

		try
		{
			ensureResources(profile);
			updateWaterMetaTexture(handle, plugin);
			updateSpectrum(profile);
			bindSceneUniforms(handle, profile);
			status = "fft active four-band " + resolution + "x" + resolution + " preset=" + profile.preset();
		}
		catch (Exception ex)
		{
			status = "fft failed";
			log.error("Unable to update FFT water pipeline", ex);
			disableSceneUniforms(handle);
			destroy();
		}
	}

	public void reset()
	{
		destroy();
		status = "fft idle";
	}

	private void ensureResources(TidesFftProfile profile) throws IOException
	{
		int requestedResolution = profile.resolution();
		String nextSignature = profile.signature();
		if (nextSignature.equals(signature) && initProgram != 0)
		{
			return;
		}

		destroy();
		resolution = requestedResolution;
		log2Resolution = Integer.numberOfTrailingZeros(resolution);
		initProgram = compileComputeProgram(loadShader("fft_init.comp"));
		packProgram = compileComputeProgram(loadShader("fft_pack.comp"));
		updateProgram = compileComputeProgram(loadShader("fft_update.comp"));
		fftProgram = compileComputeProgram(loadShader("fft_ifft.comp"));
		finalizeProgram = compileComputeProgram(loadShader("fft_finalize.comp"));

		for (int band = 0; band < BAND_COUNT; band++)
		{
			initialSpectrumTextures[band] = createTexture(GL_RGBA32F, GL_LINEAR, false);
			spectrumTextures[band] = createTexture(GL_RG32F, GL_NEAREST, false);
			pingTextures[band] = createTexture(GL_RG32F, GL_NEAREST, false);
			displacementTextures[band] = createTexture(GL_RGBA16F, GL_LINEAR_MIPMAP_LINEAR, true);
			slopeTextures[band] = createTexture(GL_RG16F, GL_LINEAR_MIPMAP_LINEAR, true);
		}

		spectrumSettingsBuffer = glGenBuffers();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, spectrumSettingsBuffer);
		glBufferData(GL_SHADER_STORAGE_BUFFER, (long) SPECTRUM_COUNT * SPECTRUM_FLOAT_STRIDE * Float.BYTES, GL_DYNAMIC_DRAW);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);

		waterMetaTexture = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, waterMetaTexture);
		org.lwjgl.opengl.GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
		org.lwjgl.opengl.GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		org.lwjgl.opengl.GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		org.lwjgl.opengl.GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);

		lastWindBucket = Integer.MIN_VALUE;
		signature = nextSignature;
	}

	private void initializeSpectrum(TidesFftProfile profile, int band)
	{
		glUseProgram(initProgram);
		updateSpectrumSettingsBuffer(profile);
		setInitSpectrumUniforms(initProgram, profile, band);
		glUniform1i(glGetUniformLocation(initProgram, "uSeed"), BAND_SEEDS[band]);
		glBindBufferBase(GL_SHADER_STORAGE_BUFFER, SPECTRUM_BUFFER_BINDING, spectrumSettingsBuffer);
		glBindImageTexture(0, initialSpectrumTextures[band], 0, false, 0, GL_READ_WRITE, GL_RGBA32F);
		dispatchGrid(resolution, resolution, 8, 8);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

		glUseProgram(packProgram);
		glUniform1i(glGetUniformLocation(packProgram, "uResolution"), resolution);
		glBindImageTexture(0, initialSpectrumTextures[band], 0, false, 0, GL_READ_WRITE, GL_RGBA32F);
		dispatchGrid(resolution, resolution, 8, 8);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
	}

	private void updateSpectrum(TidesFftProfile profile)
	{
		double baseTime = (System.nanoTime() / 1_000_000_000.0) * profile.motionScale();
		if (lastWindBucket != 0)
		{
			for (int band = 0; band < BAND_COUNT; band++)
			{
				initializeSpectrum(profile, band);
			}
			lastWindBucket = 0;
		}

		for (int band = 0; band < BAND_COUNT; band++)
		{
			glUseProgram(updateProgram);
			setUpdateSpectrumUniforms(updateProgram, profile, band);
			glUniform1f(glGetUniformLocation(updateProgram, "uTime"), (float) (baseTime * BAND_TIME_SCALES[band]));
			glBindImageTexture(0, initialSpectrumTextures[band], 0, false, 0, GL_READ_ONLY, GL_RGBA32F);
			glBindImageTexture(1, spectrumTextures[band], 0, false, 0, GL_WRITE_ONLY, GL_RG32F);
			dispatchGrid(resolution, resolution, 8, 8);
			glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

			runInverseFft(band);

			glUseProgram(finalizeProgram);
			glUniform1i(glGetUniformLocation(finalizeProgram, "uResolution"), resolution);
			glUniform1f(glGetUniformLocation(finalizeProgram, "uDomain"), profile.bandDomain(band));
			glUniform1f(glGetUniformLocation(finalizeProgram, "uHeightScale"), bandHeightScale(profile, band));
			glUniform1f(glGetUniformLocation(finalizeProgram, "uChoppyLambda"), BAND_CHOPPY_LAMBDA[band] * profile.lambda());
			glBindImageTexture(0, spectrumTextures[band], 0, false, 0, GL_READ_ONLY, GL_RG32F);
			glBindImageTexture(1, displacementTextures[band], 0, false, 0, GL_READ_WRITE, GL_RGBA16F);
			glBindImageTexture(2, slopeTextures[band], 0, false, 0, GL_WRITE_ONLY, GL_RG16F);
			dispatchGrid(resolution, resolution, 8, 8);
			glMemoryBarrier(GL_ALL_BARRIER_BITS);

			glBindTexture(GL_TEXTURE_2D, displacementTextures[band]);
			glGenerateMipmap(GL_TEXTURE_2D);
			glBindTexture(GL_TEXTURE_2D, slopeTextures[band]);
			glGenerateMipmap(GL_TEXTURE_2D);
		}
	}

	private void updateWaterMetaTexture(Hd117Handle handle, TidesPlugin plugin)
	{
		WorldView worldView = plugin.getClient().getTopLevelWorldView();
		if (worldView == null)
		{
			waterMetaWidth = 0;
			waterMetaHeight = 0;
			waterMetaOriginX = 0;
			waterMetaOriginY = 0;
			lastMetaSnapshot = TidesSceneSnapshot.empty();
			return;
		}

		int minSceneX = 0;
		int minSceneY = 0;
		int maxSceneX = Math.max(0, worldView.getSizeX() - 1);
		int maxSceneY = Math.max(0, worldView.getSizeY() - 1);
		int drawDistance = resolveDrawDistance(handle, worldView);
		Player player = plugin.getClient().getLocalPlayer();
		if (player != null)
		{
			LocalPoint playerLocalPoint = player.getLocalLocation();
			if (playerLocalPoint != null)
			{
				int margin = 2;
				int centerSceneX = playerLocalPoint.getSceneX();
				int centerSceneY = playerLocalPoint.getSceneY();
				minSceneX = Math.max(0, centerSceneX - drawDistance - margin);
				minSceneY = Math.max(0, centerSceneY - drawDistance - margin);
				maxSceneX = Math.min(worldView.getSizeX() - 1, centerSceneX + drawDistance + margin);
				maxSceneY = Math.min(worldView.getSizeY() - 1, centerSceneY + drawDistance + margin);
			}
		}

		int width = Math.max(1, maxSceneX - minSceneX + 1);
		int height = Math.max(1, maxSceneY - minSceneY + 1);
		int nextOriginX = minSceneX * TidesPlugin.LOCAL_TILE_SIZE;
		int nextOriginY = minSceneY * TidesPlugin.LOCAL_TILE_SIZE;
		TidesSceneSnapshot snapshot = plugin.getSceneSnapshot();
		if (snapshot == lastMetaSnapshot
			&& width == waterMetaWidth
			&& height == waterMetaHeight
			&& nextOriginX == waterMetaOriginX
			&& nextOriginY == waterMetaOriginY)
		{
			return;
		}

		FloatBuffer data = BufferUtils.createFloatBuffer(width * height * 2);
		for (int y = minSceneY; y <= maxSceneY; y++)
		{
			for (int x = minSceneX; x <= maxSceneX; x++)
			{
				TidesWaterTile tile = snapshot.getTile(worldView.getBaseX() + x, worldView.getBaseY() + y, worldView.getPlane());
				data.put(tile == null ? 1.0f : tile.getWaveAmplitudeScale());
				data.put(tile == null ? 0.0f : tile.getShoreDepthScale());
			}
		}
		data.flip();

		glBindTexture(GL_TEXTURE_2D, waterMetaTexture);
		if (width != waterMetaWidth || height != waterMetaHeight)
		{
			glTexImage2D(GL_TEXTURE_2D, 0, GL_RG16F, width, height, 0, GL_RG, GL_FLOAT, (ByteBuffer) null);
			waterMetaWidth = width;
			waterMetaHeight = height;
		}
		waterMetaOriginX = nextOriginX;
		waterMetaOriginY = nextOriginY;
		lastMetaSnapshot = snapshot;
		org.lwjgl.opengl.GL11C.glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RG, GL_FLOAT, data);
	}

	private void runInverseFft(int band)
	{
		glUseProgram(fftProgram);
		glUniform1i(glGetUniformLocation(fftProgram, "uResolution"), resolution);
		glUniform1i(glGetUniformLocation(fftProgram, "uLog2Resolution"), log2Resolution);

		glUniform1i(glGetUniformLocation(fftProgram, "uHorizontal"), 1);
		glBindImageTexture(0, spectrumTextures[band], 0, false, 0, GL_READ_ONLY, GL_RG32F);
		glBindImageTexture(1, pingTextures[band], 0, false, 0, GL_WRITE_ONLY, GL_RG32F);
		glDispatchCompute(resolution, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);

		glUniform1i(glGetUniformLocation(fftProgram, "uHorizontal"), 0);
		glBindImageTexture(0, pingTextures[band], 0, false, 0, GL_READ_ONLY, GL_RG32F);
		glBindImageTexture(1, spectrumTextures[band], 0, false, 0, GL_WRITE_ONLY, GL_RG32F);
		glDispatchCompute(resolution, 1, 1);
		glMemoryBarrier(GL_SHADER_IMAGE_ACCESS_BARRIER_BIT);
	}

	private void bindSceneUniforms(Hd117Handle handle, TidesFftProfile profile) throws ReflectiveOperationException
	{
		Object sceneProgram = Hd117Reflection.getField(handle.renderer, "sceneProgram");
		if (sceneProgram == null)
		{
			status = "fft scene program unavailable";
			return;
		}

		int program = (int) Hd117Reflection.getField(sceneProgram, "program");
		if (program == 0)
		{
			status = "fft scene program pending";
			return;
		}

		float[] windDir = profile.averagedWindDirection();
		int currentProgram = glGetInteger(GL_CURRENT_PROGRAM);
		glUseProgram(program);

		for (int band = 0; band < BAND_COUNT; band++)
		{
			glActiveTexture(FFT_DISPLACEMENT_TEXTURE_UNITS[band]);
			glBindTexture(GL_TEXTURE_2D, displacementTextures[band]);
			glActiveTexture(FFT_SLOPE_TEXTURE_UNITS[band]);
			glBindTexture(GL_TEXTURE_2D, slopeTextures[band]);
		}
		glActiveTexture(TEXTURE_UNIT_WATER_META);
		glBindTexture(GL_TEXTURE_2D, waterMetaTexture);

		setUniform(program, "tidesFftEnabled", 1);
		for (int band = 0; band < BAND_COUNT; band++)
		{
			setUniform(program, "tidesFftDisplacementMap" + band, FFT_DISPLACEMENT_TEXTURE_UNITS[band] - GL_TEXTURE0);
			setUniform(program, "tidesFftSlopeMap" + band, FFT_SLOPE_TEXTURE_UNITS[band] - GL_TEXTURE0);
		}
		setUniform(program, "tidesWaterMetaEnabled", waterMetaWidth > 0 && waterMetaHeight > 0 ? 1 : 0);
		setUniform(program, "tidesWaterMetaMap", TEXTURE_UNIT_WATER_META - GL_TEXTURE0);
		setUniform2f(program, "tidesSceneOriginLocal", waterMetaOriginX, waterMetaOriginY);
		setUniform2f(program, "tidesSceneSizeLocal", waterMetaWidth * TidesPlugin.LOCAL_TILE_SIZE, waterMetaHeight * TidesPlugin.LOCAL_TILE_SIZE);
		setUniform2f(program, "tidesFftWindDir", windDir[0], windDir[1]);

		glUseProgram(currentProgram);
	}

	private void disableSceneUniforms(Hd117Handle handle)
	{
		if (!hasCurrentGlContext())
		{
			return;
		}

		try
		{
			Object sceneProgram = Hd117Reflection.getField(handle.renderer, "sceneProgram");
			if (sceneProgram == null)
			{
				return;
			}

			int program = (int) Hd117Reflection.getField(sceneProgram, "program");
			if (program == 0)
			{
				return;
			}

			int currentProgram = glGetInteger(GL_CURRENT_PROGRAM);
			glUseProgram(program);
			setUniform(program, "tidesFftEnabled", 0);
			setUniform(program, "tidesWaterMetaEnabled", 0);
			setUniform2f(program, "tidesSceneOriginLocal", 0.0f, 0.0f);
			glUseProgram(currentProgram);
		}
		catch (ReflectiveOperationException | RuntimeException ex)
		{
			log.debug("Unable to disable FFT uniforms cleanly", ex);
		}
	}

	private void setInitSpectrumUniforms(int program, TidesFftProfile profile, int band)
	{
		glUniform1i(glGetUniformLocation(program, "uResolution"), resolution);
		glUniform1f(glGetUniformLocation(program, "uDomain"), profile.bandDomain(band));
		glUniform1f(glGetUniformLocation(program, "uGravity"), 9.81f);
		glUniform1f(glGetUniformLocation(program, "uDepth"), 25.0f);
		glUniform1i(glGetUniformLocation(program, "uBandIndex"), band);
	}

	private void setUpdateSpectrumUniforms(int program, TidesFftProfile profile, int band)
	{
		glUniform1i(glGetUniformLocation(program, "uResolution"), resolution);
		glUniform1f(glGetUniformLocation(program, "uDomain"), profile.bandDomain(band));
		glUniform1f(glGetUniformLocation(program, "uGravity"), 9.81f);
		glUniform1f(glGetUniformLocation(program, "uDepth"), 25.0f);
	}

	private void setUniform(int program, String uniformName, int value)
	{
		int location = glGetUniformLocation(program, uniformName);
		if (location != -1)
		{
			glUniform1i(location, value);
		}
	}

	private void setUniform2f(int program, String uniformName, float x, float y)
	{
		int location = glGetUniformLocation(program, uniformName);
		if (location != -1)
		{
			glUniform2f(location, x, y);
		}
	}

	private int createTexture(int internalFormat, int minFilter, boolean mipmapped)
	{
		int texture = glGenTextures();
		glBindTexture(GL_TEXTURE_2D, texture);
		int levels = mipmapped ? (log2Resolution + 1) : 1;
		glTexStorage2D(GL_TEXTURE_2D, levels, internalFormat, resolution, resolution);
		org.lwjgl.opengl.GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, minFilter);
		org.lwjgl.opengl.GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
		org.lwjgl.opengl.GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_REPEAT);
		org.lwjgl.opengl.GL11C.glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_REPEAT);
		return texture;
	}

	private static void dispatchGrid(int width, int height, int localX, int localY)
	{
		glDispatchCompute((width + localX - 1) / localX, (height + localY - 1) / localY, 1);
	}

	private void destroy()
	{
		boolean canDeleteGlResources = hasCurrentGlContext();
		deleteProgram(initProgram, canDeleteGlResources);
		deleteProgram(packProgram, canDeleteGlResources);
		deleteProgram(updateProgram, canDeleteGlResources);
		deleteProgram(fftProgram, canDeleteGlResources);
		deleteProgram(finalizeProgram, canDeleteGlResources);
		initProgram = packProgram = updateProgram = fftProgram = finalizeProgram = 0;

		for (int band = 0; band < BAND_COUNT; band++)
		{
			deleteTexture(initialSpectrumTextures[band], canDeleteGlResources);
			deleteTexture(spectrumTextures[band], canDeleteGlResources);
			deleteTexture(pingTextures[band], canDeleteGlResources);
			deleteTexture(displacementTextures[band], canDeleteGlResources);
			deleteTexture(slopeTextures[band], canDeleteGlResources);
			initialSpectrumTextures[band] = 0;
			spectrumTextures[band] = 0;
			pingTextures[band] = 0;
			displacementTextures[band] = 0;
			slopeTextures[band] = 0;
		}

		deleteBuffer(spectrumSettingsBuffer, canDeleteGlResources);
		spectrumSettingsBuffer = 0;

		deleteTexture(waterMetaTexture, canDeleteGlResources);
		waterMetaTexture = 0;

		signature = null;
		resolution = 0;
		log2Resolution = 0;
		waterMetaWidth = 0;
		waterMetaHeight = 0;
		waterMetaOriginX = 0;
		waterMetaOriginY = 0;
		lastMetaSnapshot = TidesSceneSnapshot.empty();
		lastWindBucket = Integer.MIN_VALUE;
	}

	private int resolveDrawDistance(Hd117Handle handle, WorldView worldView)
	{
		if (handle != null)
		{
			try
			{
				Object value = Hd117Reflection.invoke(handle.plugin, "getDrawDistance", new Class<?>[0]);
				if (value instanceof Number)
				{
					int drawDistance = ((Number) value).intValue();
					return Math.max(16, Math.min(drawDistance, Math.max(worldView.getSizeX(), worldView.getSizeY())));
				}
			}
			catch (ReflectiveOperationException | RuntimeException ex)
			{
				log.debug("Unable to resolve 117HD draw distance, using scene bounds", ex);
			}
		}

		return Math.max(worldView.getSizeX(), worldView.getSizeY());
	}

	private static void deleteProgram(int program, boolean canDelete)
	{
		if (canDelete && program != 0)
		{
			glDeleteProgram(program);
		}
	}

	private static void deleteBuffer(int buffer, boolean canDelete)
	{
		if (canDelete && buffer != 0)
		{
			glDeleteBuffers(buffer);
		}
	}

	private static void deleteTexture(int texture, boolean canDelete)
	{
		if (canDelete && texture != 0)
		{
			glDeleteTextures(texture);
		}
	}

	private static boolean hasCurrentGlContext()
	{
		try
		{
			return GL.getCapabilities() != null;
		}
		catch (IllegalStateException ex)
		{
			return false;
		}
	}

	private static int normalizeResolution(int resolution)
	{
		int clamped = Math.max(64, Math.min(MAX_FFT_SIZE, resolution));
		int highest = Integer.highestOneBit(clamped);
		return highest == clamped ? clamped : highest;
	}

	private static int compileComputeProgram(String source)
	{
		int shader = glCreateShader(GL_COMPUTE_SHADER);
		glShaderSource(shader, source);
		glCompileShader(shader);
		if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE)
		{
			String logText = glGetShaderInfoLog(shader);
			glDeleteShader(shader);
			throw new IllegalStateException("FFT compute shader compile failed: " + logText);
		}

		int program = glCreateProgram();
		glAttachShader(program, shader);
		glLinkProgram(program);
		glDeleteShader(shader);
		if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE)
		{
			String logText = glGetProgramInfoLog(program);
			glDeleteProgram(program);
			throw new IllegalStateException("FFT compute program link failed: " + logText);
		}

		return program;
	}

	private static String loadShader(String fileName) throws IOException
	{
		String resourcePath = "/org/tides/shaders/" + fileName;
		try (InputStream input = Hd117FftWater.class.getResourceAsStream(resourcePath))
		{
			if (input == null)
			{
				throw new IOException("Missing shader resource: " + resourcePath);
			}
			return new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static float bandHeightScale(TidesFftProfile profile, int band)
	{
		float bandScale = profile.bandAverageScale(band);
		float resolutionScale = resolutionFactor(profile.resolution());
		return Math.max(4.0f, bandScale * HEIGHT_SCALE_UNIT * resolutionScale * resolutionScale * BAND_HEIGHT_VISUAL_SCALES[band]);
	}

	private void updateSpectrumSettingsBuffer(TidesFftProfile profile)
	{
		FloatBuffer buffer = BufferUtils.createFloatBuffer(SPECTRUM_COUNT * SPECTRUM_FLOAT_STRIDE);
		for (int index = 0; index < SPECTRUM_COUNT; index++)
		{
			TidesSpectrumParameters spectrum = profile.spectrum(index);
			float windSpeed = Math.max(0.01f, spectrum.windSpeed());
			float fetch = Math.max(1.0f, spectrum.fetch());
			putSpectrum(
				buffer,
				spectrum.scale(),
				(float) Math.toRadians(spectrum.windDirectionDegrees()),
				spectrum.spreadBlend(),
				spectrum.swell(),
				jonswapAlpha(fetch, windSpeed),
				jonswapPeakFrequency(fetch, windSpeed),
				Math.max(1.0f, spectrum.peakEnhancement()),
				Math.max(0.0f, spectrum.shortWavesFade())
			);
		}
		buffer.flip();
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, spectrumSettingsBuffer);
		glBufferData(GL_SHADER_STORAGE_BUFFER, buffer, GL_DYNAMIC_DRAW);
		glBindBuffer(GL_SHADER_STORAGE_BUFFER, 0);
	}

	private static void putSpectrum(
		FloatBuffer buffer,
		float scale,
		float angle,
		float spreadBlend,
		float swell,
		float alpha,
		float peakOmega,
		float gamma,
		float shortWavesFade)
	{
		buffer.put(scale);
		buffer.put(angle);
		buffer.put(spreadBlend);
		buffer.put(swell);
		buffer.put(alpha);
		buffer.put(peakOmega);
		buffer.put(gamma);
		buffer.put(shortWavesFade);
	}

	private static float jonswapAlpha(float fetch, float windSpeed)
	{
		return 0.076f * (float) Math.pow((9.81f * fetch) / Math.max(windSpeed * windSpeed, 1.0f), -0.22f);
	}

	private static float jonswapPeakFrequency(float fetch, float windSpeed)
	{
		return 22.0f * (float) Math.pow(Math.max((windSpeed * fetch) / (9.81f * 9.81f), 1e-4f), -0.33f);
	}

	private static float resolutionFactor(int configuredResolution)
	{
		return normalizeResolution(configuredResolution);
	}
}
