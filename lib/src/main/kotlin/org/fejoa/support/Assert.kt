package org.fejoa.support

expect fun assert(value: Boolean)
expect fun assert(value: Boolean, lazyMessage: () -> Any)