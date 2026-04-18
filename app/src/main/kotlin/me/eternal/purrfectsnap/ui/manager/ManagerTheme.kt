package cock.crest.purrfectsnap.lite.ui.manager

import cock.crest.purrfectsnap.lite.ui.manager.pages.themes.aphelion.AphelionTheme
import cock.crest.purrfectsnap.lite.ui.manager.pages.themes.legacy.LegacyTheme

/**
 * ManagerTheme is the registry of all available themes.
 *
 * Adding a new theme requires changes only in this file:
 * - Add a new object entry to the sealed class
 * - Add its ID string to fromId()
 *
 * No other file needs to change when a new theme is added.
 */
sealed class ManagerTheme(val theme: ThemeContract) {
    object Legacy   : ManagerTheme(LegacyTheme)
    object Aphelion : ManagerTheme(AphelionTheme)

    companion object {
        fun fromId(id: String): ManagerTheme = when (id) {
            "APHELION" -> Aphelion
            else       -> Legacy   // default fallback is always Legacy
        }
    }
}
