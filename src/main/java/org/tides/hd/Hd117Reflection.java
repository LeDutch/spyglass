package org.tides.hd;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URISyntaxException;
import java.nio.file.Path;

final class Hd117Reflection
{
	private static final Object UNSAFE;
	private static final Method STATIC_FIELD_BASE;
	private static final Method STATIC_FIELD_OFFSET;
	private static final Method OBJECT_FIELD_OFFSET;
	private static final Method PUT_OBJECT;

	static
	{
		try
		{
			Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
			Field unsafeField = unsafeClass.getDeclaredField("theUnsafe");
			unsafeField.setAccessible(true);
			UNSAFE = unsafeField.get(null);
			STATIC_FIELD_BASE = unsafeClass.getMethod("staticFieldBase", Field.class);
			STATIC_FIELD_OFFSET = unsafeClass.getMethod("staticFieldOffset", Field.class);
			OBJECT_FIELD_OFFSET = unsafeClass.getMethod("objectFieldOffset", Field.class);
			PUT_OBJECT = unsafeClass.getMethod("putObject", Object.class, long.class, Object.class);
		}
		catch (ReflectiveOperationException ex)
		{
			throw new ExceptionInInitializerError(ex);
		}
	}

	private Hd117Reflection()
	{
	}

	static Class<?> loadClass(Hd117Handle handle, String className) throws ClassNotFoundException
	{
		return Class.forName(className, false, handle.classLoader);
	}

	static Object getField(Object target, String fieldName) throws ReflectiveOperationException
	{
		Field field = findField(target.getClass(), fieldName);
		field.setAccessible(true);
		return field.get(target);
	}

	static Object getStaticField(Class<?> type, String fieldName) throws ReflectiveOperationException
	{
		Field field = findField(type, fieldName);
		field.setAccessible(true);
		return field.get(null);
	}

	static void setField(Object target, String fieldName, Object value) throws ReflectiveOperationException
	{
		Field field = findField(target.getClass(), fieldName);
		field.setAccessible(true);
		long offset = (long) OBJECT_FIELD_OFFSET.invoke(UNSAFE, field);
		PUT_OBJECT.invoke(UNSAFE, target, offset, value);
	}

	static void setStaticField(Class<?> type, String fieldName, Object value) throws ReflectiveOperationException
	{
		Field field = findField(type, fieldName);
		field.setAccessible(true);
		Object base = STATIC_FIELD_BASE.invoke(UNSAFE, field);
		long offset = (long) STATIC_FIELD_OFFSET.invoke(UNSAFE, field);
		PUT_OBJECT.invoke(UNSAFE, base, offset, value);
	}

	static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args)
		throws ReflectiveOperationException
	{
		Method method = findMethod(target.getClass(), methodName, parameterTypes);
		method.setAccessible(true);
		return method.invoke(target, args);
	}

	static Object invokeStatic(Class<?> type, String methodName, Class<?>[] parameterTypes, Object... args)
		throws ReflectiveOperationException
	{
		Method method = findMethod(type, methodName, parameterTypes);
		method.setAccessible(true);
		return method.invoke(null, args);
	}

	static Path codeSourcePath(Hd117Handle handle)
	{
		try
		{
			return Path.of(handle.plugin.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
		}
		catch (URISyntaxException ex)
		{
			return Path.of(handle.plugin.getClass().getProtectionDomain().getCodeSource().getLocation().getPath());
		}
	}

	private static Field findField(Class<?> type, String fieldName) throws NoSuchFieldException
	{
		Class<?> current = type;
		while (current != null)
		{
			try
			{
				return current.getDeclaredField(fieldName);
			}
			catch (NoSuchFieldException ex)
			{
				current = current.getSuperclass();
			}
		}

		throw new NoSuchFieldException(fieldName);
	}

	private static Method findMethod(Class<?> type, String methodName, Class<?>[] parameterTypes)
		throws NoSuchMethodException
	{
		Class<?> current = type;
		while (current != null)
		{
			try
			{
				return current.getDeclaredMethod(methodName, parameterTypes);
			}
			catch (NoSuchMethodException ex)
			{
				current = current.getSuperclass();
			}
		}

		throw new NoSuchMethodException(methodName);
	}
}
