package org.freevia.rubikbuddy

expect fun platformTimeMillis(): Long
expect val platformDebugEnabled: Boolean
expect val platformIsSimulator: Boolean
expect val platformAppVersion: String
expect val platformBuildNumber: String
