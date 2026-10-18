package org.freevia.rubikbuddy

actual fun platformTimeMillis(): Long = System.currentTimeMillis()
actual val platformDebugEnabled: Boolean get() = BuildConfig.DEBUG
actual val platformIsSimulator: Boolean get() = false
