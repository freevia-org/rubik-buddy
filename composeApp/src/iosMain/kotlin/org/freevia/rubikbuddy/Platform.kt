package org.freevia.rubikbuddy

import platform.Foundation.NSDate
import platform.Foundation.NSBundle
import platform.Foundation.timeIntervalSince1970
import kotlin.experimental.ExperimentalNativeApi

actual fun platformTimeMillis(): Long = (NSDate().timeIntervalSince1970 * 1000).toLong()
@OptIn(ExperimentalNativeApi::class)
actual val platformDebugEnabled: Boolean get() = kotlin.native.Platform.isDebugBinary
actual val platformIsSimulator: Boolean get() = platform.Foundation.NSProcessInfo.processInfo.environment["SIMULATOR_MODEL_IDENTIFIER"] != null
actual val platformAppVersion: String
    get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleShortVersionString") as? String ?: "—"
actual val platformBuildNumber: String
    get() = NSBundle.mainBundle.objectForInfoDictionaryKey("CFBundleVersion") as? String ?: "—"
