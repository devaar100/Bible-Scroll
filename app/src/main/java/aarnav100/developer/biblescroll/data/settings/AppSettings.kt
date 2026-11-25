package aarnav100.developer.biblescroll.data.settings

/** App-wide display settings (S2-S4). Colours for each option live in ui/theme/Appearance.kt. */
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    /** Background of the verse feed, independent of [themeMode]. Dark by default, per spec. */
    val readingBackground: ReadingBackground = ReadingBackground.Charcoal,
    val accent: AccentPreset = AccentPreset.Amber,
    /** "Match my wallpaper"; only honoured on Android 12+. */
    val dynamicColor: Boolean = false,
    val redLetters: Boolean = true,
    val scriptureSize: ScriptureSize = ScriptureSize.Default,
    /** S2 "Hide AI studies": hides B8/D1/D2 study content. Scripture, notes, plans stay. */
    val hideAiStudies: Boolean = false,
    /** S2 "Verse scrubber": the position control on the start edge of B1, B2 and H11. */
    val verseScrubber: ScrubberMode = ScrubberMode.Always,
)

/** S2 "Verse scrubber". Stored by name. */
enum class ScrubberMode {
    /** Always visible (default). */
    Always,
    /** Fades in on scroll or touch, out about 1.5 s later. */
    WhileScrolling,
    Off,
}

enum class ThemeMode { System, Light, Dark }

enum class ReadingBackground { Paper, Sepia, Charcoal, TrueBlack }

enum class AccentPreset { Crimson, Rose, Amber, Sage, Teal, Indigo }

/** Multiplier for Scripture text, applied on top of the system font scale. */
enum class ScriptureSize(val multiplier: Float) {
    Default(1f),
    Large(1.15f),
    Larger(1.3f),
    Largest(1.5f),
}
