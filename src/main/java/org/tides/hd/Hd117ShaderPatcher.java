package org.tides.hd;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Enumeration;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.tides.config.TidesConfig;
import org.tides.water.fft.TidesFftProfile;
import org.tides.water.fft.TidesFftProfiles;

@Slf4j
@Singleton
public class Hd117ShaderPatcher
{
	private static final String PROPS_CLASS = "rs117.hd.utils.Props";
	private static final String RESOURCE_PATH_CLASS = "rs117.hd.utils.ResourcePath";
	private static final String HD_PLUGIN_CLASS = "rs117.hd.HdPlugin";
	private static final String PROP_SHADER_PATH = "rlhd.shader-path";
	private static final String PROP_RESOURCE_PATH = "rlhd.resource-path";
	private static final String FIELD_SHADER_PATH = "SHADER_PATH";
	private static final String FIELD_RESOURCE_PATH = "RESOURCE_PATH";
	private static final String PATCH_VERSION = "tides-wave-v27";

	private Path overrideRoot;
	private String appliedSignature;
	private String status = "shader patch idle";
	private boolean savedState;
	private String originalShaderProp;
	private String originalResourceProp;
	private Object originalShaderPath;
	private Object originalResourcePath;

	public String getStatus()
	{
		return status;
	}

	public void refresh(Hd117Handle handle, TidesConfig config)
	{
		TidesFftProfile profile = TidesFftProfiles.fromConfig(config);
		String signature = PATCH_VERSION
			+ "|" + handle.rendererName
			+ "|" + profile.signature()
			+ "|" + config.shorelineLock()
			+ "|" + config.foam();
		if (Objects.equals(signature, appliedSignature))
		{
			return;
		}

		try
		{
			saveOriginalState(handle);
			prepareOverrideTree(handle, config);
			applyOverridePath(handle);
			Hd117Reflection.invoke(handle.plugin, "recompilePrograms", new Class<?>[0]);
			appliedSignature = signature;
			status = "shader patch active";
		}
		catch (IOException | ReflectiveOperationException ex)
		{
			status = "shader patch failed";
			log.error("Unable to apply 117HD shader patch", ex);
		}
	}

	public void restore(Hd117Handle handle)
	{
		if (!savedState)
		{
			appliedSignature = null;
			status = "shader patch idle";
			return;
		}

		try
		{
			Class<?> propsClass = Hd117Reflection.loadClass(handle, PROPS_CLASS);
			Class<?> resourcePathClass = Hd117Reflection.loadClass(handle, RESOURCE_PATH_CLASS);
			Class<?> hdPluginClass = Hd117Reflection.loadClass(handle, HD_PLUGIN_CLASS);

			Hd117Reflection.invokeStatic(propsClass, "set", new Class<?>[] {String.class, String.class}, PROP_SHADER_PATH, originalShaderProp);
			Hd117Reflection.invokeStatic(propsClass, "set", new Class<?>[] {String.class, String.class}, PROP_RESOURCE_PATH, originalResourceProp);
			Hd117Reflection.setStaticField(resourcePathClass, FIELD_RESOURCE_PATH, originalResourcePath);
			Hd117Reflection.setStaticField(hdPluginClass, FIELD_SHADER_PATH, originalShaderPath);
			Hd117Reflection.invoke(handle.plugin, "recompilePrograms", new Class<?>[0]);
		}
		catch (ReflectiveOperationException ex)
		{
			log.error("Unable to restore 117HD shader path state", ex);
		}
		finally
		{
			appliedSignature = null;
			status = "shader patch restored";
			savedState = false;
		}
	}

	private void saveOriginalState(Hd117Handle handle) throws ReflectiveOperationException
	{
		if (savedState)
		{
			return;
		}

		Class<?> propsClass = Hd117Reflection.loadClass(handle, PROPS_CLASS);
		Class<?> resourcePathClass = Hd117Reflection.loadClass(handle, RESOURCE_PATH_CLASS);
		Class<?> hdPluginClass = Hd117Reflection.loadClass(handle, HD_PLUGIN_CLASS);
		originalShaderProp = (String) Hd117Reflection.invokeStatic(propsClass, "get", new Class<?>[] {String.class}, PROP_SHADER_PATH);
		originalResourceProp = (String) Hd117Reflection.invokeStatic(propsClass, "get", new Class<?>[] {String.class}, PROP_RESOURCE_PATH);
		originalResourcePath = Hd117Reflection.getStaticField(resourcePathClass, FIELD_RESOURCE_PATH);
		originalShaderPath = Hd117Reflection.getStaticField(hdPluginClass, FIELD_SHADER_PATH);
		savedState = true;
	}

	private void prepareOverrideTree(Hd117Handle handle, TidesConfig config) throws IOException
	{
		Path codeSource = Hd117Reflection.codeSourcePath(handle);
		overrideRoot = Path.of(System.getProperty("user.home"), ".runelite", "tides", "117hd-shader-patch");
		Files.createDirectories(overrideRoot);
		extractHdResources(codeSource, overrideRoot);
		writePatchedShaders(config);
	}

	private void extractHdResources(Path jarPath, Path destinationRoot) throws IOException
	{
		try (JarFile jarFile = new JarFile(jarPath.toFile()))
		{
			Enumeration<JarEntry> entries = jarFile.entries();
			while (entries.hasMoreElements())
			{
				JarEntry entry = entries.nextElement();
				String name = entry.getName();
				if (!name.startsWith("rs117/hd/") || entry.isDirectory())
				{
					continue;
				}

				Path target = destinationRoot.resolve(name.replace('/', java.io.File.separatorChar));
				Files.createDirectories(target.getParent());
				try (InputStream input = jarFile.getInputStream(entry))
				{
					Files.copy(input, target, StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
	}

	private void writePatchedShaders(TidesConfig config) throws IOException
	{
		Path shaderRoot = overrideRoot.resolve(Path.of("rs117", "hd"));
		Path sceneVert = shaderRoot.resolve("scene_vert.glsl");
		Path sceneGeom = shaderRoot.resolve("scene_geom.glsl");
		Path water = shaderRoot.resolve(Path.of("utils", "water.glsl"));
		Path helper = shaderRoot.resolve(Path.of("utils", "tides_water_vertex_waves.glsl"));

		Files.writeString(sceneVert, patchSceneVert(Files.readString(sceneVert)), StandardCharsets.UTF_8);
		Files.writeString(sceneGeom, patchSceneGeom(Files.readString(sceneGeom)), StandardCharsets.UTF_8);
		Files.writeString(water, patchWaterShader(Files.readString(water)), StandardCharsets.UTF_8);
		Files.createDirectories(helper.getParent());
		Files.writeString(helper, buildWaveHelper(config), StandardCharsets.UTF_8);
	}

	private void applyOverridePath(Hd117Handle handle) throws ReflectiveOperationException
	{
		Class<?> propsClass = Hd117Reflection.loadClass(handle, PROPS_CLASS);
		Class<?> resourcePathClass = Hd117Reflection.loadClass(handle, RESOURCE_PATH_CLASS);
		Class<?> hdPluginClass = Hd117Reflection.loadClass(handle, HD_PLUGIN_CLASS);
		Object overrideResourceRoot = Hd117Reflection.invokeStatic(
			resourcePathClass,
			"path",
			new Class<?>[] {String[].class},
			(Object) new String[] {overrideRoot.toString()}
		);
		overrideResourceRoot = Hd117Reflection.invoke(overrideResourceRoot, "chroot", new Class<?>[0]);

		Object shaderPath = Hd117Reflection.invokeStatic(
			resourcePathClass,
			"path",
			new Class<?>[] {String[].class},
			(Object) new String[] {overrideRoot.resolve(Path.of("rs117", "hd")).toString()}
		);
		shaderPath = Hd117Reflection.invoke(shaderPath, "chroot", new Class<?>[0]);

		Hd117Reflection.invokeStatic(propsClass, "set", new Class<?>[] {String.class, String.class}, PROP_RESOURCE_PATH, overrideRoot.toString());
		Hd117Reflection.invokeStatic(
			propsClass,
			"set",
			new Class<?>[] {String.class, String.class},
			PROP_SHADER_PATH,
			overrideRoot.resolve(Path.of("rs117", "hd")).toString()
		);
		Hd117Reflection.setStaticField(resourcePathClass, FIELD_RESOURCE_PATH, overrideResourceRoot);
		Hd117Reflection.setStaticField(hdPluginClass, FIELD_SHADER_PATH, shaderPath);
	}

	private static String patchSceneVert(String source)
	{
		String include = "#include <utils/tides_water_vertex_waves.glsl>";
		if (!source.contains(include))
		{
			if (source.contains("#include <utils/uvs.glsl>"))
			{
				source = source.replace("#include <utils/uvs.glsl>", "#include <utils/uvs.glsl>\n" + include);
			}
			else if (source.contains("#include <uniforms/world_views.glsl>"))
			{
				source = source.replace(
					"#include <uniforms/world_views.glsl>",
					"#include <uniforms/world_views.glsl>\n#include <uniforms/water_types.glsl>\n" + include
				);
			}
		}

		if (!source.contains("applyTidesWaterVertexWaves"))
		{
			if (source.contains("        OUT.position = worldPosition;"))
			{
				String injection = "        worldPosition = applyTidesWaterVertexWaves(worldPosition, terrainData, alphaBiasHsl);\n";
				source = source.replace("        OUT.position = worldPosition;", injection + "        OUT.position = worldPosition;");
			}
			else if (source.contains("    gPosition = vec3(worldViewProjection * vec4(sceneOffset + vPosition, 1));"))
			{
				source = source.replace(
					"    gPosition = vec3(worldViewProjection * vec4(sceneOffset + vPosition, 1));",
					"    gPosition = applyTidesWaterVertexWaves(vec3(worldViewProjection * vec4(sceneOffset + vPosition, 1)), vTerrainData, vAlphaBiasHsl);"
				);
			}
		}

		return source;
	}

	private static String patchSceneGeom(String source)
	{
		String include = "#include <utils/tides_water_vertex_waves.glsl>";
		if (!source.contains(include))
		{
			source = source.replace("#include <utils/misc.glsl>", "#include <utils/misc.glsl>\n" + include);
		}

		if (!source.contains("applyTidesWaterVertexWaves"))
		{
			source = source.replace(
				"        vec4 pos = vec4(gPosition[i], 1);",
				"        vec4 pos = vec4(applyTidesWaterVertexWaves(gPosition[i], gTerrainData[i], gAlphaBiasHsl[i]), 1);"
			);
		}

		return source;
	}

	private static String patchWaterShader(String source)
	{
		String include = "#include <utils/tides_water_vertex_waves.glsl>";
		if (!source.contains(include))
		{
			source = source.replace("#include <utils/misc.glsl>", "#include <utils/misc.glsl>\n" + include);
		}

		if (!source.contains("applyTidesWaterSlopeNormals"))
		{
			String alphaBiasSymbol = null;
			if (source.contains("vAlphaBiasHsl"))
			{
				alphaBiasSymbol = "vAlphaBiasHsl";
			}
			else if (source.contains("fAlphaBiasHsl"))
			{
				alphaBiasSymbol = "fAlphaBiasHsl";
			}

			if (alphaBiasSymbol == null)
			{
				return source;
			}

			source = source.replace(
				"    vec3 normals = normalize(n1 + n2);",
				"    vec3 normals = normalize(n1 + n2);\n"
					+ "    normals = applyTidesWaterSlopeNormals(normals, IN.position, waterTypeIndex, dot(IN.texBlend, vec3(" + alphaBiasSymbol + " & 127)));"
			);
		}

		if (!source.contains("sampleTidesFftFoam(IN.position)"))
		{
			source = source.replace(
				"    foamAmount *= foamColor.r;",
				"    foamAmount *= foamColor.r;\n"
					+ "    float fftFoam = sampleTidesFftFoam(IN.position);\n"
					+ "    float fftSpray = sampleTidesFftSpray(IN.position);\n"
					+ "    foamColor = mix(foamColor, vec3(1.0), clamp(fftFoam * 1.35 + fftSpray * 0.55, 0.0, 1.0));\n"
					+ "    foamColor = mix(foamColor, vec3(1.35), clamp(fftFoam * 0.65 + fftSpray * 0.45, 0.0, 1.0));\n"
					+ "    foamAmount = clamp(max(foamAmount, fftFoam * 1.10 + fftSpray * 0.38), 0.0, 1.0);"
			);
		}

		if (!source.contains("sampleTidesFftSpray(IN.position)"))
		{
			source = source.replace(
				"                output = lerp(output, _FoamColor, saturate(foam));",
				"                output = lerp(output, _FoamColor, saturate(foam));\n"
					+ "                float fftSprayLight = sampleTidesFftSpray(IN.position);\n"
					+ "                output += vec3(1.0) * (fftSprayLight * 0.42);"
			);
			source = source.replace(
				"                output = lerp(output, _TipColor, saturate(foam));",
				"                output = lerp(output, _TipColor, saturate(foam));\n"
					+ "                float fftSprayLight = sampleTidesFftSpray(IN.position);\n"
					+ "                output += vec3(1.0) * (fftSprayLight * 0.42);"
			);
		}

		return source;
	}

	private static String buildWaveHelper(TidesConfig config)
	{
		TidesFftProfile profile = TidesFftProfiles.fromConfig(config);
		float amplitudeSwell = profile.swellFactor();
		float fftDomain0 = profile.bandDomain(0);
		float fftDomain1 = profile.bandDomain(1);
		float fftDomain2 = profile.bandDomain(2);
		float fftDomain3 = profile.bandDomain(3);
		float fftAdvection0 = Math.max(1.5f, profile.bandAverageWindSpeed(0) * 0.85f);
		float fftAdvection1 = Math.max(2.0f, profile.bandAverageWindSpeed(1) * 0.95f);
		float fftAdvection2 = Math.max(2.5f, profile.bandAverageWindSpeed(2) * 1.05f);
		float fftAdvection3 = Math.max(3.0f, profile.bandAverageWindSpeed(3) * 1.15f);
		String shorelineLock = config.shorelineLock() ? "true" : "false";
		return "#pragma once\n"
			+ "\n"
			+ "#include <uniforms/global.glsl>\n"
			+ "#include <uniforms/water_types.glsl>\n"
			+ "\n"
			+ "const float TIDES_FFT_NORMAL_GAIN = 2.35;\n"
			+ "const float TIDES_FFT_DOMAIN_0 = " + fftDomain0 + ";\n"
			+ "const float TIDES_FFT_DOMAIN_1 = " + fftDomain1 + ";\n"
			+ "const float TIDES_FFT_DOMAIN_2 = " + fftDomain2 + ";\n"
			+ "const float TIDES_FFT_DOMAIN_3 = " + fftDomain3 + ";\n"
			+ "const float TIDES_FFT_SWELL_FACTOR = " + amplitudeSwell + ";\n"
			+ "const float TIDES_FFT_ADVECTION_0 = " + fftAdvection0 + ";\n"
			+ "const float TIDES_FFT_ADVECTION_1 = " + fftAdvection1 + ";\n"
			+ "const float TIDES_FFT_ADVECTION_2 = " + fftAdvection2 + ";\n"
			+ "const float TIDES_FFT_ADVECTION_3 = " + fftAdvection3 + ";\n"
			+ "const float TIDES_FFT_ADVECTION_ANGLE_0 = 0.00;\n"
			+ "const float TIDES_FFT_ADVECTION_ANGLE_1 = 0.10;\n"
			+ "const float TIDES_FFT_ADVECTION_ANGLE_2 = -0.14;\n"
			+ "const float TIDES_FFT_ADVECTION_ANGLE_3 = 0.22;\n"
			+ "const float TIDES_SWELL_HEIGHT = " + profile.swellHeight() + ";\n"
			+ "const float TIDES_SWELL_LENGTH_0 = " + profile.swellLength0() + ";\n"
			+ "const float TIDES_SWELL_LENGTH_1 = " + profile.swellLength1() + ";\n"
			+ "const float TIDES_SWELL_LENGTH_2 = " + Math.max(profile.swellLength0() * 0.82f, 2304.0f) + ";\n"
			+ "const float TIDES_SWELL_STEEPNESS_0 = 0.36;\n"
			+ "const float TIDES_SWELL_STEEPNESS_1 = 0.24;\n"
			+ "const float TIDES_SWELL_STEEPNESS_2 = 0.18;\n"
			+ "const float TIDES_SWELL_SPEED_0 = " + profile.swellSpeed0() + ";\n"
			+ "const float TIDES_SWELL_SPEED_1 = " + profile.swellSpeed1() + ";\n"
			+ "const float TIDES_SWELL_SPEED_2 = " + Math.max(profile.swellSpeed0() * 0.58f, 0.32f) + ";\n"
			+ "const bool TIDES_FFT_FOAM_ENABLED = " + (config.foam() ? "true" : "false") + ";\n"
			+ "const bool TIDES_SHORELINE_LOCK = " + shorelineLock + ";\n"
			+ "\n"
			+ "uniform bool tidesFftEnabled;\n"
			+ "uniform sampler2D tidesFftDisplacementMap0;\n"
			+ "uniform sampler2D tidesFftSlopeMap0;\n"
			+ "uniform sampler2D tidesFftDisplacementMap1;\n"
			+ "uniform sampler2D tidesFftSlopeMap1;\n"
			+ "uniform sampler2D tidesFftDisplacementMap2;\n"
			+ "uniform sampler2D tidesFftSlopeMap2;\n"
			+ "uniform sampler2D tidesFftDisplacementMap3;\n"
			+ "uniform sampler2D tidesFftSlopeMap3;\n"
			+ "uniform bool tidesWaterMetaEnabled;\n"
			+ "uniform sampler2D tidesWaterMetaMap;\n"
			+ "uniform vec2 tidesSceneOriginLocal;\n"
			+ "uniform vec2 tidesSceneSizeLocal;\n"
			+ "uniform vec2 tidesFftWindDir;\n"
			+ "\n"
			+ "vec2 sampleTidesWaterMeta(vec3 position) {\n"
			+ "    if (!tidesWaterMetaEnabled || tidesSceneSizeLocal.x <= 0.0 || tidesSceneSizeLocal.y <= 0.0)\n"
			+ "        return vec2(1.0, 1.0);\n"
			+ "    vec2 uv = (position.xz - tidesSceneOriginLocal) / tidesSceneSizeLocal;\n"
			+ "    if (uv.x < 0.0 || uv.y < 0.0 || uv.x > 1.0 || uv.y > 1.0)\n"
			+ "        return vec2(1.0, 1.0);\n"
			+ "    return textureLod(tidesWaterMetaMap, uv, 0.0).rg;\n"
			+ "}\n"
			+ "\n"
			+ "float sampleTidesBodyAmplitudeScale(vec3 position) {\n"
			+ "    return max(sampleTidesWaterMeta(position).r, 0.35);\n"
			+ "}\n"
			+ "\n"
			+ "float sampleTidesShoreDepthScale(vec3 position) {\n"
			+ "    return 1.0;\n"
			+ "}\n"
			+ "\n"
			+ "float tidesDeepWaterFactor(float amplitudeScale) {\n"
			+ "    float t = clamp((amplitudeScale - 1.0) / 1.25, 0.0, 1.0);\n"
			+ "    return t * t * (3.0 - 2.0 * t);\n"
			+ "}\n"
			+ "\n"
			+ "vec2 tidesPerpendicular(vec2 v) {\n"
			+ "    return vec2(-v.y, v.x);\n"
			+ "}\n"
			+ "\n"
			+ "vec2 tidesRotate(vec2 v, float angle) {\n"
			+ "    float s = sin(angle);\n"
			+ "    float c = cos(angle);\n"
			+ "    return vec2(v.x * c - v.y * s, v.x * s + v.y * c);\n"
			+ "}\n"
			+ "\n"
			+ "vec2 tidesBandWindDir(int bandIndex) {\n"
			+ "    vec2 baseWindDir = normalize(length(tidesFftWindDir) > 0.0 ? tidesFftWindDir : vec2(0.94, 0.34));\n"
			+ "    if (bandIndex == 1) return tidesRotate(baseWindDir, TIDES_FFT_ADVECTION_ANGLE_1);\n"
			+ "    if (bandIndex == 2) return tidesRotate(baseWindDir, TIDES_FFT_ADVECTION_ANGLE_2);\n"
			+ "    if (bandIndex == 3) return tidesRotate(baseWindDir, TIDES_FFT_ADVECTION_ANGLE_3);\n"
			+ "    return tidesRotate(baseWindDir, TIDES_FFT_ADVECTION_ANGLE_0);\n"
			+ "}\n"
			+ "\n"
			+ "vec2 tidesChaoticSwellDir(vec2 baseDir, float seed, float timeScale) {\n"
			+ "    float angle = sin(elapsedTime * timeScale + seed) * 0.42 + sin(elapsedTime * (timeScale * 0.53) + seed * 1.73) * 0.24;\n"
			+ "    return normalize(tidesRotate(baseDir, angle));\n"
			+ "}\n"
			+ "\n"
			+ "vec3 sampleTidesSwellDisplacement(vec3 position, float deepWater) {\n"
			+ "    vec2 baseDir = tidesBandWindDir(0);\n"
			+ "    vec2 dir0 = tidesChaoticSwellDir(baseDir, 0.0, 0.11);\n"
			+ "    vec2 dir1 = tidesChaoticSwellDir(tidesPerpendicular(baseDir), 1.9, 0.08);\n"
			+ "    vec2 dir2 = tidesChaoticSwellDir(tidesRotate(baseDir, 0.85), 3.4, 0.06);\n"
			+ "    float phase0 = dot(position.xz, dir0) / TIDES_SWELL_LENGTH_0 + elapsedTime * TIDES_SWELL_SPEED_0;\n"
			+ "    float phase1 = dot(position.xz, dir1) / TIDES_SWELL_LENGTH_1 - elapsedTime * TIDES_SWELL_SPEED_1;\n"
			+ "    float phase2 = dot(position.xz, dir2) / TIDES_SWELL_LENGTH_2 + elapsedTime * TIDES_SWELL_SPEED_2;\n"
			+ "    float sin0 = sin(phase0);\n"
			+ "    float cos0 = cos(phase0);\n"
			+ "    float sin1 = sin(phase1);\n"
			+ "    float cos1 = cos(phase1);\n"
			+ "    float sin2 = sin(phase2);\n"
			+ "    float cos2 = cos(phase2);\n"
			+ "    vec3 swell0 = vec3(dir0.x * cos0 * TIDES_SWELL_HEIGHT * TIDES_SWELL_STEEPNESS_0, sin0 * TIDES_SWELL_HEIGHT, dir0.y * cos0 * TIDES_SWELL_HEIGHT * TIDES_SWELL_STEEPNESS_0);\n"
			+ "    vec3 swell1 = vec3(dir1.x * cos1 * TIDES_SWELL_HEIGHT * 0.58 * TIDES_SWELL_STEEPNESS_1, sin1 * TIDES_SWELL_HEIGHT * 0.58, dir1.y * cos1 * TIDES_SWELL_HEIGHT * 0.58 * TIDES_SWELL_STEEPNESS_1);\n"
			+ "    vec3 swell2 = vec3(dir2.x * cos2 * TIDES_SWELL_HEIGHT * 0.34 * TIDES_SWELL_STEEPNESS_2, sin2 * TIDES_SWELL_HEIGHT * 0.34, dir2.y * cos2 * TIDES_SWELL_HEIGHT * 0.34 * TIDES_SWELL_STEEPNESS_2);\n"
			+ "    return (swell0 + swell1 + swell2) * deepWater;\n"
			+ "}\n"
			+ "\n"
			+ "vec2 sampleTidesSwellSlope(vec3 position, float deepWater) {\n"
			+ "    vec2 baseDir = tidesBandWindDir(0);\n"
			+ "    vec2 dir0 = tidesChaoticSwellDir(baseDir, 0.0, 0.11);\n"
			+ "    vec2 dir1 = tidesChaoticSwellDir(tidesPerpendicular(baseDir), 1.9, 0.08);\n"
			+ "    vec2 dir2 = tidesChaoticSwellDir(tidesRotate(baseDir, 0.85), 3.4, 0.06);\n"
			+ "    float phase0 = dot(position.xz, dir0) / TIDES_SWELL_LENGTH_0 + elapsedTime * TIDES_SWELL_SPEED_0;\n"
			+ "    float phase1 = dot(position.xz, dir1) / TIDES_SWELL_LENGTH_1 - elapsedTime * TIDES_SWELL_SPEED_1;\n"
			+ "    float phase2 = dot(position.xz, dir2) / TIDES_SWELL_LENGTH_2 + elapsedTime * TIDES_SWELL_SPEED_2;\n"
			+ "    float cos0 = cos(phase0);\n"
			+ "    float cos1 = cos(phase1);\n"
			+ "    float cos2 = cos(phase2);\n"
			+ "    vec2 slope0 = dir0 * (cos0 * TIDES_SWELL_HEIGHT / TIDES_SWELL_LENGTH_0);\n"
			+ "    vec2 slope1 = dir1 * (cos1 * TIDES_SWELL_HEIGHT * 0.58 / TIDES_SWELL_LENGTH_1);\n"
			+ "    vec2 slope2 = dir2 * (cos2 * TIDES_SWELL_HEIGHT * 0.34 / TIDES_SWELL_LENGTH_2);\n"
			+ "    return (slope0 + slope1 + slope2) * deepWater * 220.0;\n"
			+ "}\n"
			+ "\n"
			+ "vec2 tidesFftUv(vec3 position, float domain, float advectionSpeed, vec2 windDir) {\n"
			+ "    vec2 advection = windDir * elapsedTime * advectionSpeed;\n"
			+ "    return fract((position.xz - advection) / domain);\n"
			+ "}\n"
			+ "\n"
			+ "float tidesFftDomainScale(float deepWater, float broadScale, float shallowScale) {\n"
			+ "    return mix(shallowScale, broadScale, deepWater);\n"
			+ "}\n"
			+ "\n"
			+ "vec3 sampleTidesFftDisplacement(vec3 position) {\n"
			+ "    float amplitudeScale = sampleTidesBodyAmplitudeScale(position);\n"
			+ "    float deepWater = tidesDeepWaterFactor(amplitudeScale);\n"
			+ "    float domainScale0 = tidesFftDomainScale(deepWater, 2.35, 1.0);\n"
			+ "    float domainScale1 = tidesFftDomainScale(deepWater, 2.05, 1.0);\n"
			+ "    float domainScale2 = tidesFftDomainScale(deepWater, 1.75, 1.0);\n"
			+ "    float domainScale3 = tidesFftDomainScale(deepWater, 1.45, 1.0);\n"
			+ "    float band1Weight = mix(0.46, 0.60, deepWater);\n"
			+ "    float band2Weight = mix(0.14, 0.10, deepWater) * mix(1.0, 0.52, TIDES_FFT_SWELL_FACTOR);\n"
			+ "    float band3Weight = mix(0.03, 0.01, deepWater) * mix(1.0, 0.18, TIDES_FFT_SWELL_FACTOR);\n"
			+ "    vec3 band0 = textureLod(tidesFftDisplacementMap0, tidesFftUv(position, TIDES_FFT_DOMAIN_0 * domainScale0, TIDES_FFT_ADVECTION_0, tidesBandWindDir(0)), 0.0).rgb;\n"
			+ "    vec3 band1 = textureLod(tidesFftDisplacementMap1, tidesFftUv(position, TIDES_FFT_DOMAIN_1 * domainScale1, TIDES_FFT_ADVECTION_1, tidesBandWindDir(1)), 0.0).rgb * band1Weight;\n"
			+ "    vec3 band2 = textureLod(tidesFftDisplacementMap2, tidesFftUv(position, TIDES_FFT_DOMAIN_2 * domainScale2, TIDES_FFT_ADVECTION_2, tidesBandWindDir(2)), 0.0).rgb * band2Weight;\n"
			+ "    vec3 band3 = textureLod(tidesFftDisplacementMap3, tidesFftUv(position, TIDES_FFT_DOMAIN_3 * domainScale3, TIDES_FFT_ADVECTION_3, tidesBandWindDir(3)), 0.0).rgb * band3Weight;\n"
			+ "    vec3 displacement = band0 + band1 + band2 + band3 + sampleTidesSwellDisplacement(position, deepWater);\n"
			+ "    displacement.xz *= amplitudeScale;\n"
			+ "    displacement.y *= amplitudeScale;\n"
			+ "    return displacement;\n"
			+ "}\n"
			+ "\n"
			+ "vec2 sampleTidesFftWaveSlope(vec3 position) {\n"
			+ "    float amplitudeScale = sampleTidesBodyAmplitudeScale(position);\n"
			+ "    float deepWater = tidesDeepWaterFactor(amplitudeScale);\n"
			+ "    float domainScale0 = tidesFftDomainScale(deepWater, 2.35, 1.0);\n"
			+ "    float domainScale1 = tidesFftDomainScale(deepWater, 2.05, 1.0);\n"
			+ "    float domainScale2 = tidesFftDomainScale(deepWater, 1.75, 1.0);\n"
			+ "    float domainScale3 = tidesFftDomainScale(deepWater, 1.45, 1.0);\n"
			+ "    float band1Weight = mix(0.48, 0.62, deepWater);\n"
			+ "    float band2Weight = mix(0.16, 0.11, deepWater) * mix(1.0, 0.56, TIDES_FFT_SWELL_FACTOR);\n"
			+ "    float band3Weight = mix(0.04, 0.015, deepWater) * mix(1.0, 0.20, TIDES_FFT_SWELL_FACTOR);\n"
			+ "    vec2 band0 = textureLod(tidesFftSlopeMap0, tidesFftUv(position, TIDES_FFT_DOMAIN_0 * domainScale0, TIDES_FFT_ADVECTION_0, tidesBandWindDir(0)), 0.0).rg;\n"
			+ "    vec2 band1 = textureLod(tidesFftSlopeMap1, tidesFftUv(position, TIDES_FFT_DOMAIN_1 * domainScale1, TIDES_FFT_ADVECTION_1, tidesBandWindDir(1)), 0.0).rg * band1Weight;\n"
			+ "    vec2 band2 = textureLod(tidesFftSlopeMap2, tidesFftUv(position, TIDES_FFT_DOMAIN_2 * domainScale2, TIDES_FFT_ADVECTION_2, tidesBandWindDir(2)), 0.0).rg * band2Weight;\n"
			+ "    vec2 band3 = textureLod(tidesFftSlopeMap3, tidesFftUv(position, TIDES_FFT_DOMAIN_3 * domainScale3, TIDES_FFT_ADVECTION_3, tidesBandWindDir(3)), 0.0).rg * band3Weight;\n"
			+ "    return ((band0 + band1 + band2 + band3) * amplitudeScale) + sampleTidesSwellSlope(position, deepWater);\n"
			+ "}\n"
			+ "\n"
			+ "float sampleTidesFftFoam(vec3 position) {\n"
			+ "    if (!TIDES_FFT_FOAM_ENABLED || !tidesFftEnabled)\n"
			+ "        return 0.0;\n"
			+ "    float amplitudeScale = sampleTidesBodyAmplitudeScale(position);\n"
			+ "    float deepWater = tidesDeepWaterFactor(amplitudeScale);\n"
			+ "    float domainScale0 = tidesFftDomainScale(deepWater, 2.35, 1.0);\n"
			+ "    float domainScale1 = tidesFftDomainScale(deepWater, 2.05, 1.0);\n"
			+ "    float domainScale2 = tidesFftDomainScale(deepWater, 1.75, 1.0);\n"
			+ "    float domainScale3 = tidesFftDomainScale(deepWater, 1.45, 1.0);\n"
			+ "    vec4 band0 = textureLod(tidesFftDisplacementMap0, tidesFftUv(position, TIDES_FFT_DOMAIN_0 * domainScale0, TIDES_FFT_ADVECTION_0, tidesBandWindDir(0)), 0.0);\n"
			+ "    vec4 band1 = textureLod(tidesFftDisplacementMap1, tidesFftUv(position, TIDES_FFT_DOMAIN_1 * domainScale1, TIDES_FFT_ADVECTION_1, tidesBandWindDir(1)), 0.0);\n"
			+ "    vec4 band2 = textureLod(tidesFftDisplacementMap2, tidesFftUv(position, TIDES_FFT_DOMAIN_2 * domainScale2, TIDES_FFT_ADVECTION_2, tidesBandWindDir(2)), 0.0);\n"
			+ "    vec4 band3 = textureLod(tidesFftDisplacementMap3, tidesFftUv(position, TIDES_FFT_DOMAIN_3 * domainScale3, TIDES_FFT_ADVECTION_3, tidesBandWindDir(3)), 0.0);\n"
			+ "    vec2 slope = sampleTidesFftWaveSlope(position);\n"
			+ "    float foam = max(max(band0.a, band1.a * 0.85), max(band2.a * 0.65, band3.a * 0.45));\n"
			+ "    float crestHeight = (abs(band0.y) + abs(band1.y) * 0.85 + abs(band2.y) * 0.50 + abs(band3.y) * 0.25) * amplitudeScale;\n"
			+ "    float pointiness = (length(band0.xz) + length(band1.xz) * 0.85 + length(band2.xz) * 0.55 + length(band3.xz) * 0.30) * amplitudeScale;\n"
			+ "    float slopeEnergy = length(slope);\n"
			+ "    float tallWaveMask = smoothstep(8.0, 18.0, crestHeight);\n"
			+ "    float pointyMask = smoothstep(6.0, 18.0, pointiness + slopeEnergy * 7.5);\n"
			+ "    float oceanMask = mix(0.55, 1.0, deepWater);\n"
			+ "    return clamp(foam * tallWaveMask * pointyMask * oceanMask * 1.35, 0.0, 1.0);\n"
			+ "}\n"
			+ "\n"
			+ "float tidesSprayNoise(vec2 p) {\n"
			+ "    vec2 i = floor(p);\n"
			+ "    vec2 f = fract(p);\n"
			+ "    float a = fract(sin(dot(i, vec2(127.1, 311.7))) * 43758.5453);\n"
			+ "    float b = fract(sin(dot(i + vec2(1.0, 0.0), vec2(127.1, 311.7))) * 43758.5453);\n"
			+ "    float c = fract(sin(dot(i + vec2(0.0, 1.0), vec2(127.1, 311.7))) * 43758.5453);\n"
			+ "    float d = fract(sin(dot(i + vec2(1.0, 1.0), vec2(127.1, 311.7))) * 43758.5453);\n"
			+ "    vec2 u = f * f * (3.0 - 2.0 * f);\n"
			+ "    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);\n"
			+ "}\n"
			+ "\n"
			+ "float sampleTidesFftSpray(vec3 position) {\n"
			+ "    if (!TIDES_FFT_FOAM_ENABLED || !tidesFftEnabled)\n"
			+ "        return 0.0;\n"
			+ "    float amplitudeScale = sampleTidesBodyAmplitudeScale(position);\n"
			+ "    float deepWater = tidesDeepWaterFactor(amplitudeScale);\n"
			+ "    vec2 slope = sampleTidesFftWaveSlope(position);\n"
			+ "    float foam = sampleTidesFftFoam(position);\n"
			+ "    float crestiness = smoothstep(10.0, 24.0, length(slope) * amplitudeScale);\n"
			+ "    vec2 windDir = tidesBandWindDir(0);\n"
			+ "    vec2 sprayUv = position.xz / max(TIDES_SWELL_LENGTH_0 * 0.18, 256.0);\n"
			+ "    sprayUv += windDir * elapsedTime * 0.35;\n"
			+ "    float sprayNoise = tidesSprayNoise(sprayUv * 3.2) * 0.65 + tidesSprayNoise(sprayUv * 6.1 + 13.7) * 0.35;\n"
			+ "    float sprayMask = smoothstep(0.62, 0.88, sprayNoise);\n"
			+ "    return clamp(foam * crestiness * sprayMask * deepWater * 0.85, 0.0, 1.0);\n"
			+ "}\n"
			+ "\n"
			+ "float tidesShorelineLockValue(float alphaBiasHslValue) {\n"
			+ "    if (!TIDES_SHORELINE_LOCK)\n"
			+ "        return 1.0;\n"
			+ "    float fade = clamp(alphaBiasHslValue / 127.0, 0.0, 1.0);\n"
			+ "    fade = clamp((fade - 0.12) / 0.88, 0.0, 1.0);\n"
			+ "    fade *= fade;\n"
			+ "    return fade * fade * (3.0 - 2.0 * fade);\n"
			+ "}\n"
			+ "\n"
			+ "float tidesShorelineLock(int alphaBiasHsl) {\n"
			+ "    return tidesShorelineLockValue(float(alphaBiasHsl & 127));\n"
			+ "}\n"
			+ "\n"
			+ "vec2 sampleTidesFftSlopeForWaterType(vec3 position, int waterTypeIndex, float alphaBiasHslValue) {\n"
			+ "    if (waterTypeIndex <= 0)\n"
			+ "        return vec2(0.0);\n"
			+ "    return sampleTidesFftWaveSlope(position) * tidesShorelineLockValue(alphaBiasHslValue);\n"
			+ "}\n"
			+ "\n"
			+ "vec3 applyTidesWaterVertexWaves(vec3 position, int terrainData, int alphaBiasHsl) {\n"
			+ "    bool isTerrain = (terrainData & 1) != 0;\n"
			+ "    int waterTypeIndex = isTerrain ? terrainData >> 3 & 0xFF : 0;\n"
			+ "    if (waterTypeIndex <= 0 || !tidesFftEnabled)\n"
			+ "        return position;\n"
			+ "    position += sampleTidesFftDisplacement(position) * tidesShorelineLock(alphaBiasHsl);\n"
			+ "    return position;\n"
			+ "}\n"
			+ "\n"
			+ "vec3 applyTidesWaterSlopeNormals(vec3 baseNormal, vec3 position, int waterTypeIndex, float alphaBiasHslValue) {\n"
			+ "    if (waterTypeIndex <= 0)\n"
			+ "        return baseNormal;\n"
			+ "\n"
			+ "    vec2 slope = sampleTidesFftSlopeForWaterType(position, waterTypeIndex, alphaBiasHslValue);\n"
			+ "    vec3 waveNormal = normalize(vec3(slope.x, -1.0, slope.y));\n"
			+ "    return normalize(baseNormal + waveNormal);\n"
			+ "}\n";
	}

}
