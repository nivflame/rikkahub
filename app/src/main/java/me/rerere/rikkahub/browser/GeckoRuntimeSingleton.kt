package me.rerere.rikkahub.browser

import android.content.Context
import org.mozilla.geckoview.GeckoRuntime

object GeckoRuntimeSingleton {
    @Volatile
    private var runtime: GeckoRuntime? = null

    fun getRuntime(context: Context): GeckoRuntime {
        return runtime ?: synchronized(this) {
            runtime ?: GeckoRuntime.create(context).also { runtime = it }
        }
    }
}
