package org.fejoa.support

actual fun assert(value: Boolean) { kotlin.assert(value)}
actual fun assert(value: Boolean, lazyMessage: () -> Any) { kotlin.assert(value, lazyMessage) }