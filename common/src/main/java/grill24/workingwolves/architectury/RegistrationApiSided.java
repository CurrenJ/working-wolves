package grill24.workingwolves.architectury;

public class RegistrationApiSided {
    private static IRegistrationApi instance;

    public static void set(IRegistrationApi api) {
        instance = api;
    }

    public static IRegistrationApi getInstance() {
        return instance;
    }
}
