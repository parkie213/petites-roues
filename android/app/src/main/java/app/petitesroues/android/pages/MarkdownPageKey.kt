package app.petitesroues.android.pages

import app.petitesroues.model.library.pages.MarkdownPageEntry

/** Stable UI selection key across content roots and page collections. */
val MarkdownPageEntry.key: String
  get() = "${repository().id()}/${path().removeSuffix(".md")}"
