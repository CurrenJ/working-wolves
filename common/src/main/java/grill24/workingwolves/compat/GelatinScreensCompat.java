package grill24.workingwolves.compat;

import grill24.workingwolves.WorkingWolves;

public final class GelatinScreensCompat {
    private static final String GELATIN_SCREENS_CLASS = "grill24.workingwolves.compat.GelatinScreens";
    private static final String REGISTER_METHOD = "registerScreens";

    public static synchronized void init() {
        boolean success = CompatUtil.invokeIfDependencyPresent(
            CompatUtil.GELATIN_UI_CLASS,
            GELATIN_SCREENS_CLASS,
            REGISTER_METHOD
        );
        if (success) {
            WorkingWolves.LOGGER.info("Gelatin UI detected; dog bed screen enabled.");
        } else {
            WorkingWolves.LOGGER.info("Gelatin UI not found; dog bed screen disabled.");
        }
    }

    private GelatinScreensCompat() {}
}
