package com.terrariaserver.manager;

public final class Catalog {
    private Catalog() {}

    // Pacote oficial: terraria.org/api/download/pc-dedicated-server/terraria-server-<codigo>.zip
    public static final String[] VERSION_LABELS = {
            "1.4.5.8 (mais recente que roda no celular)", "1.4.5.7", "1.4.5.6", "1.4.5.5",
            "1.4.5.4", "1.4.5.3", "1.4.5.2", "1.4.5.1", "1.4.5.0", "1.4.4.9", "1.4.4.8.1",
            "1.4.6.1 (NÃO roda no celular)", "1.4.6 (NÃO roda no celular)"
    };
    public static final String[] VERSION_CODES = {
            "1458", "1457", "1456", "1455",
            "1454", "1453", "1452", "1451", "1450", "1449", "14481",
            "1461", "146"
    };

    public static final String[] SIZES = {"Pequeno", "Médio", "Grande"};

    public static final String[] DIFFICULTIES = {"Clássico", "Expert", "Mestre", "Jornada"};

    // Seeds especiais (codigos oficiais). Posicao 0 = aleatoria, ultima = personalizada.
    public static final String[] SEED_LABELS = {
            "Aleatória", "Not the Bees", "Drunk (Corruption + Crimson)", "Celebration Mk 10",
            "The Constant", "For the Worthy", "No Traps", "Remix (Don't Dig Up)",
            "Zenith (Get Fixed Boi)", "Skyblock (1.4.5+)", "Personalizada..."
    };
    public static final String[] SEED_CODES = {
            "", "not the bees", "5162020", "celebrationmk10",
            "the constant", "for the worthy", "no traps", "dont dig up",
            "get fixed boi", "skyblock", ""
    };
    public static final int SEED_CUSTOM = SEED_LABELS.length - 1;

    public static final String[] LANG_LABELS = {
            "Português (BR)", "English", "Español", "Deutsch", "Français",
            "Italiano", "Русский", "Polski", "中文"
    };
    public static final String[] LANG_CODES = {
            "pt-BR", "en-US", "es-ES", "de-DE", "fr-FR", "it-IT", "ru-RU", "pl-PL", "zh-Hans"
    };

    public static final String[] JOURNEY_LABELS = {
            "Bloqueado para todos", "Só o host", "Todos podem"
    };

    public static final String[] JOURNEY_KEYS = {
            "time_setfrozen", "time_setdawn", "time_setnoon", "time_setdusk", "time_setmidnight",
            "godmode", "wind_setstrength", "rain_setstrength", "time_setspeed", "rain_setfrozen",
            "wind_setfrozen", "increaseplacementrange", "setdifficulty", "biomespread_setfrozen",
            "setspawnrate"
    };
}
