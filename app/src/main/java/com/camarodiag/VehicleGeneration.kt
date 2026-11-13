package com.example.camarodiag.model

enum class VehicleGeneration(val displayName: String, val years: String) {
    GEN_5("Chevrolet Camaro Gen 5", "2010 - 2015"),
    GEN_6("Chevrolet Camaro Gen 6", "2016 - 2024+")
}

enum class EngineType(val code: String, val displayName: String, val generation: VehicleGeneration) {
    // Gen 5
    LFX_36("LFX", "3.6L V6 (Gen 5)", VehicleGeneration.GEN_5),
    LS3_62("LS3", "6.2L V8 SS (Gen 5)", VehicleGeneration.GEN_5),
    L99_62("L99", "6.2L V8 SS Auto (Gen 5)", VehicleGeneration.GEN_5),
    LSA_62("LSA", "6.2L V8 Supercharged ZL1 (Gen 5)", VehicleGeneration.GEN_5),
    LS7_70("LS7", "7.0L V8 Z/28 (Gen 5)", VehicleGeneration.GEN_5),

    // Gen 6 (Tous moteurs & toutes versions)
    LTG_20("LTG", "2.0L Turbo 4-Cyl (Gen 6 - LT)", VehicleGeneration.GEN_6),
    LGX_36("LGX", "3.6L V6 (Gen 6 - LT/RS)", VehicleGeneration.GEN_6),
    LT1_62("LT1", "6.2L V8 (Gen 6 - SS)", VehicleGeneration.GEN_6),
    LT4_62("LT4", "6.2L V8 Supercharged (Gen 6 - ZL1)", VehicleGeneration.GEN_6),
    LT5_62("LT5", "6.2L V8 Supercharged (Gen 6 - ZL1 1LE / Spécial)", VehicleGeneration.GEN_6)
}
