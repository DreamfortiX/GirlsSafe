package com.example.gamified

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GamifiedApp : Application()

// Also, make sure to update your AndroidManifest.xml to use this Application class
