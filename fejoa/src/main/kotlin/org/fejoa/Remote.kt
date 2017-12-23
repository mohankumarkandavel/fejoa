package org.fejoa

import kotlinx.serialization.Serializable


@Serializable
class Remote(val id: String, val user: String, val server: String)
