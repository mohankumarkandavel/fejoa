package org.fejoa.storage


/**
 * Result type for an insertion into a hash like table.
 *
 * @param <Type> key of the inserted data
</Type> */
class PutResult<Type>(val key: Type, // indicate if the data was already in the database
                      val wasInDatabase: Boolean)
