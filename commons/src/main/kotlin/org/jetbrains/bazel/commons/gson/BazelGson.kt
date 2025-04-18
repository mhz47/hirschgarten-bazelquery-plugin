package org.jetbrains.bazel.commons.gson

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import java.nio.file.Path
import kotlin.jvm.internal.Reflection
import kotlin.reflect.KClass

val bazelGson: Gson =
  GsonBuilder()
    .registerTypeAdapterFactory(SealedClassTypeAdapterFactory)
    .registerTypeHierarchyAdapter(Path::class.java, PathSerializer)
    .create()

object SealedClassTypeAdapterFactory : TypeAdapterFactory {
  override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
    val kclass = Reflection.getOrCreateKotlinClass(type.rawType)
    return if (kclass.sealedSubclasses.isNotEmpty()) {
      SealedClassTypeAdapter(kclass, gson)
    } else {
      null
    }
  }
}

object PathSerializer : TypeAdapter<Path?>() {
  override fun read(jsonReader: JsonReader): Path? = jsonReader.nextString()?.let { Path.of(it) }

  override fun write(out: JsonWriter, value: Path?) {
    out.value(value.toString())
  }
}

@Suppress("UNCHECKED_CAST")
class SealedClassTypeAdapter<T : Any>(val kclass: KClass<Any>, val gson: Gson) : TypeAdapter<T>() {
  override fun read(jsonReader: JsonReader): T? {
    jsonReader.beginObject() // start reading the object
    val nextName = jsonReader.nextName() // get the name on the object
    val innerClass =
      kclass.sealedSubclasses.firstOrNull {
        it.qualifiedName == nextName
      } ?: throw Exception("$nextName is not found to be a data class of the sealed class ${kclass.qualifiedName}")
    val x = gson.fromJson<T>(jsonReader, innerClass.javaObjectType)
    jsonReader.endObject()
    // if there a static object, actually return that back to ensure equality and such!
    return innerClass.objectInstance as T? ?: x
  }

  override fun write(out: JsonWriter, value: T?) {
    if (value == null) {
      out.nullValue()
      return
    }
    val json = gson.toJsonTree(value)
    out.beginObject()
    out.name(
      value.javaClass.canonicalName,
    )
    gson.toJson(json, out)
    out.endObject()
  }
}
