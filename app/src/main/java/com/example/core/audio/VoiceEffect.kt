package com.example.core.audio

/**
 * Voice transformation presets.
 * Specifically tuned for natural female and boy voices as well as deep and effect voices.
 */
enum class VoiceEffect(
    val id: String,
    val displayName: String,
    val subtitle: String,
    val pitchFactor: Float,
    val formantShift: Float, // > 1.0 shifts formants higher (smaller vocal tract), < 1.0 shifts lower
    val trebleBoostDb: Float, // Upper-mid brightness for natural vocal clarity
    val bassBoostDb: Float,   // Low-end body/warmth
    val isRobotic: Boolean = false,
    val ringModFrequencyHz: Float = 0f
) {
    ORIGINAL(
        id = "original",
        displayName = "Original Voice",
        subtitle = "Natural unmodified voice",
        pitchFactor = 1.0f,
        formantShift = 1.0f,
        trebleBoostDb = 0f,
        bassBoostDb = 0f
    ),
    NATURAL_FEMALE(
        id = "female",
        displayName = "Natural Female",
        subtitle = "Smooth feminine pitch & warm clarity",
        pitchFactor = 1.34f,
        formantShift = 1.15f,
        trebleBoostDb = 3.5f,
        bassBoostDb = -2.0f
    ),
    YOUNG_BOY(
        id = "boy",
        displayName = "Young Boy",
        subtitle = "Crisp, lively youthful tone",
        pitchFactor = 1.22f,
        formantShift = 1.10f,
        trebleBoostDb = 2.5f,
        bassBoostDb = -1.0f
    ),
    DEEP_MAN(
        id = "deep",
        displayName = "Deep Male",
        subtitle = "Resonant low timbre & depth",
        pitchFactor = 0.82f,
        formantShift = 0.90f,
        trebleBoostDb = -1.5f,
        bassBoostDb = 4.0f
    ),
    CHIPMUNK(
        id = "chipmunk",
        displayName = "High Pitch",
        subtitle = "Ultra bright playful voice",
        pitchFactor = 1.58f,
        formantShift = 1.25f,
        trebleBoostDb = 4.0f,
        bassBoostDb = -4.0f
    ),
    ROBOT(
        id = "robot",
        displayName = "Robotic",
        subtitle = "Futuristic metallic synthesis",
        pitchFactor = 1.0f,
        formantShift = 1.0f,
        trebleBoostDb = 2.0f,
        bassBoostDb = 1.0f,
        isRobotic = true,
        ringModFrequencyHz = 60f
    );

    companion object {
        val DEFAULT = NATURAL_FEMALE
    }
}
