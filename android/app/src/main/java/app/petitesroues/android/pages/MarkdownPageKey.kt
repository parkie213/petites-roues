package app.petitesroues.android.pages

import app.petitesroues.model.pages.MarkdownPage

/** Stable UI selection key across content roots and page collections. */
val MarkdownPage.key: String
  get() = "${repository()}/${id()}"
