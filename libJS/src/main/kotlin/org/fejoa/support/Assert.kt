package org.fejoa.support


actual fun assert(value: Boolean) {}
actual fun assert(value: Boolean, lazyMessage: () -> Any) { }