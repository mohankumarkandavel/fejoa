package org.fejoa.storage

import kotlinx.serialization.*
import kotlinx.serialization.internal.SerialClassDescImpl


@Serializer(forClass = HashValue::class)
object HashValueDataSerializer : KSerializer<HashValue> {
    override val serialClassDesc: KSerialClassDesc
        get() = SerialClassDescImpl("org.fejoa.storage.HashValue")

    override fun save(output: KOutput, obj: HashValue) {
        output.writeStringValue(obj.toHex())
    }

    override fun load(input: KInput): HashValue {
        return HashValue.fromHex(input.readStringValue())
    }
}