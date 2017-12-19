package org.fejoa.network

object Errors {
    val FOLLOW_UP_JOB = 1

    val DONE = 0
    val OK = 0
    val ERROR = -1
    val EXCEPTION = -2

    // json handler
    val NO_HANDLER_FOR_REQUEST = -10
    val INVALID_JSON_REQUEST = -11

    // access
    val ACCESS_DENIED = -20

    // migration
    val MIGRATION_ALREADY_STARTED = -30
}
