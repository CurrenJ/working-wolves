package grill24.workingwolves.compat;

import grill24.workingwolves.ModMenuTypes;
import grill24.workingwolves.WorkingWolves;
import grill24.workingwolves.client.DogBedScreen;
import grill24.workingwolves.inventory.DogBedMenu;
import io.github.currenj.gelatinui.registration.menu.ScreenRegistrationEvent;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;

public class GelatinScreens {
    public static void registerScreens() {
        ScreenRegistrationEvent.registerListener(GelatinScreens::_registerScreens);
        WorkingWolves.LOGGER.info("Registered Working Wolves screen registration listener with Gelatin UI.");
    }

    private static void _registerScreens(ScreenRegistrationEvent.ScreenRegistrar registrar) {
        MenuType<DogBedMenu> menuType = (MenuType<DogBedMenu>) ModMenuTypes.getDogBedMenuType();
        MenuScreens.ScreenConstructor<DogBedMenu, DogBedScreen> ctor = DogBedScreen::new;
        registrar.register(menuType, ctor);
        WorkingWolves.LOGGER.info("Registered DogBedScreen with Gelatin UI.");
    }
}
