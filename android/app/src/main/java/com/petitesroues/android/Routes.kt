package com.petitesroues.android

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** The library's shelves, with its Notes and Quiz me tabs: the first screen. */
@Serializable data object LibraryRoute : NavKey

/** The player or comic reader for the title at [path] in the index. */
@Serializable data class TitleRoute(val path: String) : NavKey

/** The titles a paired desktop offers, to follow and sync. */
@Serializable data object DesktopTitlesRoute : NavKey
