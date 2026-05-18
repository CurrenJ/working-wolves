package grill24.workingwolves;

import grill24.workingwolves.architectury.RegistrationApiSided;
import grill24.workingwolves.item.CollarItem;
import grill24.workingwolves.item.DispatchWhistleItem;
import grill24.workingwolves.item.RecallWhistleItem;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ModItems {
    public static Holder<Item> LEATHER_COLLAR;
    public static Holder<Item> IRON_STUDDED_COLLAR;
    public static Holder<Item> GOLD_TRIMMED_COLLAR;
    public static Holder<Item> DISPATCH_WHISTLE;
    public static Holder<Item> RECALL_WHISTLE;

    public static void registerItems() {
        var api = RegistrationApiSided.getInstance();

        LEATHER_COLLAR = api.registerItem("leather_collar", loc ->
                new CollarItem(props(loc).stacksTo(1), 1, 5, Config.expeditionDurationMinutesTier1 * 60 * 20));

        IRON_STUDDED_COLLAR = api.registerItem("iron_studded_collar", loc ->
                new CollarItem(props(loc).stacksTo(1), 2, 9, Config.expeditionDurationMinutesTier2 * 60 * 20));

        GOLD_TRIMMED_COLLAR = api.registerItem("gold_trimmed_collar", loc ->
                new CollarItem(props(loc).stacksTo(1), 3, 15, Config.expeditionDurationMinutesTier3 * 60 * 20));

        DISPATCH_WHISTLE = api.registerItem("dispatch_whistle", loc ->
                new DispatchWhistleItem(props(loc).stacksTo(1)));

        RECALL_WHISTLE = api.registerItem("recall_whistle", loc ->
                new RecallWhistleItem(props(loc).stacksTo(1)));
    }

    private static Item.Properties props(Identifier loc) {
        return new Item.Properties().setId(ResourceKey.create(Registries.ITEM, loc));
    }
}
