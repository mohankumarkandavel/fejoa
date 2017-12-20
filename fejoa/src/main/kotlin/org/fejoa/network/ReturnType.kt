package org.fejoa.network

enum class ReturnType(val code: Int) {
    DONE(0),
    OK(0),
    ERROR(-1),
    EXCEPTION(-2),

    // json handler
    NO_HANDLER_FOR_REQUEST(-10),
    INVALID_JSON_REQUEST(-11),

    // access
    ACCESS_DENIED(-20),

    // migration
    MIGRATION_ALREADY_STARTED(-30),
}

fun ensureError(error: ReturnType): ReturnType {
    if (error.code >= 0)
        return ReturnType.ERROR

    return error
}
