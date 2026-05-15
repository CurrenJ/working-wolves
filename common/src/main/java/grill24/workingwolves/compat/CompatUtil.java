package grill24.workingwolves.compat;

import java.lang.reflect.Method;

public final class CompatUtil {
    public static final String GELATIN_UI_CLASS = "io.github.currenj.gelatinui.GelatinUi";

    private CompatUtil() {}

    public static boolean invokeIfDependencyPresent(String dependencyClass, String targetClass, String methodName, Object... params) {
        try {
            Class.forName(dependencyClass);
            Class<?> clazz = Class.forName(targetClass);
            Class<?>[] paramTypes = new Class<?>[params.length];
            for (int i = 0; i < params.length; i++) {
                paramTypes[i] = params[i].getClass();
            }
            Method method = clazz.getDeclaredMethod(methodName, paramTypes);
            method.invoke(null, params);
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}
