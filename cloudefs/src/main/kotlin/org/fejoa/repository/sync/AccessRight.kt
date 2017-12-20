package org.fejoa.repository.sync


enum class AccessRight(val value: Int) {
    NONE(0),
    PULL(0x01),
    PUSH(0x02),
    PULL_CHUNK_STORE(0x04),
    PULL_PUSH(PULL.value or PUSH.value),
    ALL(PULL_PUSH.value or PULL_CHUNK_STORE.value)
}

