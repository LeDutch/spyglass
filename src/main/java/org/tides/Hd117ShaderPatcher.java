package org.tides;

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
	private static final String PATCH_VERSION = "tides-wave-v3";

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
		if (!config.hotPatch117HdShaders())
		{
			status = "shader patch disabled";
			return;
		}

		String signature = PATCH_VERSION
			+ "|" + handle.rendererName
			+ "|" + config.amplitude()
			+ "|" + config.wavelength()
			+ "|" + config.speed()
			+ "|" + config.shorelineFade();
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
		Path helper = shaderRoot.resolve(Path.of("utils", "tides_water_vertex_waves.glsl"));

		Files.writeString(sceneVert, patchSceneVert(Files.readString(sceneVert)), StandardCharsets.UTF_8);
		Files.writeString(sceneGeom, patchSceneGeom(Files.readString(sceneGeom)), StandardCharsets.UTF_8);
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

	private static String buildWaveHelper(TidesConfig config)
	{
		float amplitude = config.amplitude();
		float wavelength = Math.max(16, config.wavelength());
		float speed = config.speed() / 100.0f;
		String shoreFade = config.shorelineFade() ? "true" : "false";
		return "#pragma once\n"
			+ "\n"
			+ "#include <uniforms/global.glsl>\n"
			+ "#include <uniforms/water_types.glsl>\n"
			+ "\n"
			+ "const float TIDES_WAVE_AMPLITUDE = " + amplitude + ";\n"
			+ "const float TIDES_WAVE_WAVELENGTH = " + wavelength + ";\n"
			+ "const float TIDES_WAVE_SPEED = " + speed + ";\n"
			+ "const bool TIDES_SHORE_FADE = " + shoreFade + ";\n"
			+ "\n"
			+ "float tidesWaterVertexWaveShoreFade(int alphaBiasHsl) {\n"
			+ "    if (!TIDES_SHORE_FADE)\n"
			+ "        return 1.0;\n"
			+ "    float fade = clamp(float(alphaBiasHsl & 127) / 127.0, 0.0, 1.0);\n"
			+ "    fade = clamp((fade - 0.12) / 0.88, 0.0, 1.0);\n"
			+ "    fade *= fade;\n"
			+ "    return fade * fade * (3.0 - 2.0 * fade);\n"
			+ "}\n"
			+ "\n"
			+ "float sampleTidesWaterVertexWave(vec3 position, int terrainData, int alphaBiasHsl) {\n"
			+ "    bool isTerrain = (terrainData & 1) != 0;\n"
			+ "    int waterTypeIndex = isTerrain ? terrainData >> 3 & 0xFF : 0;\n"
			+ "    if (waterTypeIndex <= 0)\n"
			+ "        return 0.0;\n"
			+ "\n"
			+ "    WaterType waterType = getWaterType(waterTypeIndex);\n"
			+ "    float time = elapsedTime * TIDES_WAVE_SPEED * max(0.35, waterType.duration);\n"
			+ "    float primary = sin(position.x / TIDES_WAVE_WAVELENGTH + time);\n"
			+ "    float secondary = cos(position.z / (TIDES_WAVE_WAVELENGTH * 0.78) - time * 1.28);\n"
			+ "    float diagonal = sin((position.x + position.z) / (TIDES_WAVE_WAVELENGTH * 1.45) + time * 0.73);\n"
			+ "    float combined = primary * 0.55 + secondary * 0.30 + diagonal * 0.15;\n"
			+ "    return combined * TIDES_WAVE_AMPLITUDE * tidesWaterVertexWaveShoreFade(alphaBiasHsl);\n"
			+ "}\n"
			+ "\n"
			+ "vec3 applyTidesWaterVertexWaves(vec3 position, int terrainData, int alphaBiasHsl) {\n"
			+ "    position.y += sampleTidesWaterVertexWave(position, terrainData, alphaBiasHsl);\n"
			+ "    return position;\n"
			+ "}\n";
	}
}
