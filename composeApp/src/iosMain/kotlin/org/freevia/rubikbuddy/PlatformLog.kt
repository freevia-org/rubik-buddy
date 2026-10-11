package org.freevia.rubikbuddy

import platform.Foundation.NSLog

actual fun platformLog(tag: String, msg: String) {
    NSLog("$tag: $msg")
}
