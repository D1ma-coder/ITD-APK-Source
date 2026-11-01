package com.itd.app.data.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class ModelContractTest {
    @Test
    fun modelsRetainApiFieldTypesAndDefaults() {
        val text = requireNotNull(javaClass.getResourceAsStream("/api-model-contracts.json"))
            .bufferedReader().use { it.readText() }
        val contracts = Json.parseToJsonElement(text).jsonArray
        assertEquals(112, contracts.size)
        for (entry in contracts) {
            val contract = entry.jsonObject
            val name = contract.getValue("name").jsonPrimitive.content
            val type = Class.forName("com.itd.app.data.model.$name")
            val companion = type.getField("Companion").get(null)
            val serializer = companion.javaClass.getMethod("serializer").invoke(companion) as KSerializer<*>
            val descriptor = serializer.descriptor
            val fields = contract.getValue("fields").jsonArray
            assertEquals(name, fields.size, descriptor.elementsCount)
            var requiredMask = 0
            for ((index, element) in fields.withIndex()) {
                val field = element.jsonObject
                val label = "$name.${field.getValue("wire").jsonPrimitive.content}"
                assertEquals(label, field.getValue("wire").jsonPrimitive.content, descriptor.getElementName(index))
                assertEquals(label, field.getValue("name").jsonPrimitive.content, descriptor.getElementDescriptor(index).serialName)
                assertEquals(label, field.getValue("nullable").jsonPrimitive.boolean, descriptor.getElementDescriptor(index).isNullable)
                val optional = field.getValue("optional").jsonPrimitive.boolean
                assertEquals(label, optional, descriptor.isElementOptional(index))
                if (!optional) requiredMask = requiredMask or (1 shl index)
            }
            val constructor = type.declaredConstructors.single {
                it.parameterCount == fields.size + 2 &&
                    it.parameterTypes.last().name.endsWith("SerializationConstructorMarker")
            }
            constructor.isAccessible = true
            val arguments = constructor.parameterTypes.mapIndexed<Class<*>, Any?> { index, parameter ->
                when {
                    index == 0 -> requiredMask
                    parameter == String::class.java -> "fixture"
                    parameter == Int::class.javaPrimitiveType -> 0
                    parameter == Long::class.javaPrimitiveType -> 0L
                    parameter == Boolean::class.javaPrimitiveType -> false
                    parameter == Double::class.javaPrimitiveType -> 0.0
                    else -> null
                }
            }.toTypedArray()
            val model = constructor.newInstance(*arguments)
            for ((index, element) in fields.withIndex()) {
                val field = element.jsonObject
                if (!field.getValue("optional").jsonPrimitive.boolean) continue
                val value = type.getMethod("component${index + 1}").invoke(model)
                val actual: JsonElement = when (value) {
                    null -> JsonNull
                    is String -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Collection<*> -> JsonArray(value.map { JsonPrimitive(it.toString()) })
                    else -> error("Unexpected default type: ${value.javaClass.name}")
                }
                assertEquals("$name default for ${field["wire"]}", field.getValue("default"), actual)
            }
        }
    }

    @Test
    fun loginRequestOmitsUnusedCaptchaFields() {
        val json = Json { explicitNulls = false }
        val body = json.encodeToJsonElement(LoginRequest.serializer(), LoginRequest("user@example.invalid", "password"))
        assertEquals(setOf("email", "password"), body.jsonObject.keys)
    }

    @Test
    fun captchaUsesItsSpecifiedField() {
        val json = Json { explicitNulls = false }
        val body = json.encodeToJsonElement(LoginRequest.serializer(), LoginRequest("user@example.invalid", "password", turnstileToken = "answer"))
        assertEquals("answer", body.jsonObject.getValue("turnstileToken").jsonPrimitive.content)
        assertFalse(body.jsonObject.containsKey("token"))
    }
}
