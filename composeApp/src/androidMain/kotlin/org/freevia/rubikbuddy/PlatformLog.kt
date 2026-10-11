package org.freevia.rubikbuddy

actual fun platformLog(tag: String, msg: String) {
    android.util.Log.i(tag, msg)
}
