package grill24.workingwolves;

import com.mojang.logging.LogUtils;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.AABB;
import org.slf4j.Logger;

public class WorkingWolves {
    public static final String MODID = "workingwolves";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final AABB ALL_ENTITIES = new AABB(-30000000, -64, -30000000, 30000000, 320, 30000000);

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MODID, path);
    }
}
