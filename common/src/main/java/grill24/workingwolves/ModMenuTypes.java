package grill24.workingwolves;

import grill24.workingwolves.inventory.DogBedMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.MenuType;

import java.util.function.Supplier;

public class ModMenuTypes {
    // Set by each platform during registration
    public static Supplier<MenuType<DogBedMenu>> DOG_BED_MENU_SUPPLIER;

    // Temporary store for the bed position while the client-side menu is being created
    // Set by the BedStatePacket handler before the menu open packet is processed
    public static BlockPos pendingBedPos = BlockPos.ZERO;

    public static MenuType<DogBedMenu> getDogBedMenuType() {
        return DOG_BED_MENU_SUPPLIER != null ? DOG_BED_MENU_SUPPLIER.get() : null;
    }
}
