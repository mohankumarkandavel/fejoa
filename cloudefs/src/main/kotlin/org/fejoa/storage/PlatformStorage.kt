package org.fejoa.storage

/**
 * @context the storage context, e.g. the base directory path
 */
expect fun platformCreateStorage(context: String): StorageBackend
