package com.hjw.qbremote.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonParseException
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonToken
import com.google.gson.stream.JsonWriter

/**
 * Gson for server payloads whose field types vary between versions and builds
 * (e.g. a number sent as a string or a float, a boolean sent as 0/1, a value
 * larger than Int). Scalars are coerced when possible; a value that cannot be
 * coerced is skipped and the field keeps its default instead of failing the
 * whole response, which used to surface as a misleading "Non-JSON response".
 */
internal fun tolerantGson(): Gson = GsonBuilder()
    .registerTypeAdapterFactory(TolerantScalarAdapterFactory)
    .create()

private object TolerantScalarAdapterFactory : TypeAdapterFactory {
    @Suppress("UNCHECKED_CAST")
    override fun <T> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        val raw = type.rawType
        val adapter: TypeAdapter<*> = when (raw) {
            Int::class.javaPrimitiveType -> ScalarAdapter(nullable = false, fallback = 0) { it.toLongOrClamp().coerceToInt() }
            Int::class.javaObjectType -> ScalarAdapter(nullable = true, fallback = null) { it.toLongOrClamp().coerceToInt() }
            Long::class.javaPrimitiveType -> ScalarAdapter(nullable = false, fallback = 0L) { it.toLongOrClamp() }
            Long::class.javaObjectType -> ScalarAdapter(nullable = true, fallback = null) { it.toLongOrClamp() }
            Double::class.javaPrimitiveType -> ScalarAdapter(nullable = false, fallback = 0.0) { it.toDouble() }
            Double::class.javaObjectType -> ScalarAdapter(nullable = true, fallback = null) { it.toDouble() }
            Boolean::class.javaPrimitiveType -> BooleanAdapter(nullable = false)
            Boolean::class.javaObjectType -> BooleanAdapter(nullable = true)
            String::class.java -> StringAdapter
            else -> return null
        }
        return adapter as TypeAdapter<T>
    }
}

/** Reads NUMBER / numeric STRING / BOOLEAN into a number; anything else falls back. */
private class ScalarAdapter<N>(
    private val nullable: Boolean,
    private val fallback: N?,
    private val convert: (Double) -> N,
) : TypeAdapter<N?>() {
    override fun write(out: JsonWriter, value: N?) {
        if (value == null) out.nullValue() else out.value(value as Number)
    }

    override fun read(reader: JsonReader): N? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                if (nullable) null else fallback
            }
            JsonToken.NUMBER, JsonToken.STRING -> {
                val text = reader.nextString().trim()
                text.toDoubleOrNull()?.takeIf { !it.isNaN() }?.let(convert) ?: fallback
            }
            JsonToken.BOOLEAN -> convert(if (reader.nextBoolean()) 1.0 else 0.0)
            else -> {
                reader.skipValue()
                fallback
            }
        }
    }
}

private class BooleanAdapter(private val nullable: Boolean) : TypeAdapter<Boolean?>() {
    override fun write(out: JsonWriter, value: Boolean?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): Boolean? {
        return when (reader.peek()) {
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER -> reader.nextString().toDoubleOrNull()?.let { it != 0.0 } ?: false
            JsonToken.STRING -> when (reader.nextString().trim().lowercase()) {
                "true", "1", "yes" -> true
                else -> false
            }
            JsonToken.NULL -> {
                reader.nextNull()
                if (nullable) null else false
            }
            else -> {
                reader.skipValue()
                if (nullable) null else false
            }
        }
    }
}

private object StringAdapter : TypeAdapter<String?>() {
    override fun write(out: JsonWriter, value: String?) {
        if (value == null) out.nullValue() else out.value(value)
    }

    override fun read(reader: JsonReader): String? {
        return when (reader.peek()) {
            JsonToken.NULL -> {
                reader.nextNull()
                null
            }
            JsonToken.STRING, JsonToken.NUMBER -> reader.nextString()
            JsonToken.BOOLEAN -> reader.nextBoolean().toString()
            else -> {
                // An array/object where text was expected (e.g. a list of labels).
                reader.skipValue()
                ""
            }
        }
    }
}

private fun Double.toLongOrClamp(): Long = when {
    this >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
    this <= Long.MIN_VALUE.toDouble() -> Long.MIN_VALUE
    else -> toLong()
}

private fun Long.coerceToInt(): Int = coerceIn(Int.MIN_VALUE.toLong(), Int.MAX_VALUE.toLong()).toInt()

/**
 * Describes a parse failure: valid JSON whose shape we could not map is not
 * "Non-JSON", so name the offending path instead.
 */
internal fun describeJsonMappingFailure(error: Throwable): String? {
    val cause = generateSequence(error) { it.cause }.firstOrNull {
        it is JsonParseException || it is IllegalStateException || it is NumberFormatException
    } ?: return null
    return cause.message?.takeIf { it.isNotBlank() }?.take(160)
}
