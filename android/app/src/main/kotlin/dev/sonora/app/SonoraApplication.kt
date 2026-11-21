package dev.sonora.app

import android.app.Application
import dev.sonora.design.theme.SonoraUiPreferences

class SonoraApplication : Application() {

    lateinit var graph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        SonoraUiPreferences.initialize(this)
        graph = AppGraph(this)
        graph.start()
    }
}
