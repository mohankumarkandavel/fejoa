package org.fejoa.protocolbufferlight

import kotlinx.serialization.SerialId
import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoBuf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


class ProtocolBufferLightTest {
    @Serializable
    data class TestChildClass(@SerialId(0) val value: Int)

    fun TestChildClass.write(bufferLight: ProtocolBufferLight) {
        bufferLight.put(0, value)
    }

    fun readTestChild(bufferLight: ProtocolBufferLight): TestChildClass {
        val int = bufferLight.getLong(0)?.toInt() ?: throw Exception()
        return TestChildClass(int)
    }

    @Serializable
    data class TestClass(
            @SerialId(0)
            val int: Int,
            @SerialId(1)
            val long: Long,
            @SerialId(2)
            val string: String,
            @SerialId(3)
            val child: TestChildClass
    )

    fun TestClass.write(bufferLight: ProtocolBufferLight) {
        bufferLight.put(0, int)
        bufferLight.put(1, long)
        bufferLight.put(2, string)
        val buffer = ProtocolBufferLight()
        child.write(buffer)
        bufferLight.put(3, buffer.toByteArray())
    }

    fun readTestClass(bufferLight: ProtocolBufferLight): TestClass {
        val int = bufferLight.getLong(0)?.toInt() ?: throw Exception()
        val long = bufferLight.getLong(1) ?: throw Exception()
        val string = bufferLight.getString(2) ?: throw Exception()
        val buffer = bufferLight.getBytes(3) ?: throw Exception()
        val child = readTestChild(ProtocolBufferLight(buffer))
        return TestClass(int, long, string, child)
    }

    @Test
    fun testProtocolBuffer() {
        val original = TestClass(4, 10, "Hello", TestChildClass(5))

        val lightBuffer = ProtocolBufferLight()
        original.write(lightBuffer)
        val lightRawBuffer = lightBuffer.toByteArray()

        val protoBuffer = ProtoBuf.dump(original)

        assertTrue(lightRawBuffer contentEquals protoBuffer)

        assertEquals(original, ProtoBuf.load(lightRawBuffer))
        assertEquals(original, readTestClass(ProtocolBufferLight(protoBuffer)))
    }
}