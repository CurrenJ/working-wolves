package grill24.workingwolves;

import grill24.workingwolves.architectury.RegistrationApiSided;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

public class ModSoundEvents {
    public static Holder<SoundEvent> WHISTLE_DISPATCH;
    public static Holder<SoundEvent> WHISTLE_RECALL;

    public static void registerSoundEvents() {
        var api = RegistrationApiSided.getInstance();
        WHISTLE_DISPATCH = api.registerSoundEvent("whistle_dispatch");
        WHISTLE_RECALL = api.registerSoundEvent("whistle_recall");
    }
}
