package com.petitesroues.android

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/** The application: Hilt builds the app-wide objects ([com.petitesroues.android.di]) from here. */
@HiltAndroidApp class PetitesRouesApp : Application()
