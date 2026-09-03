package il.co.tradesmanager.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Structured columns are stored as JSON rather than as extra tables or extra
 * columns per language. That is what lets a fifth language, or a new
 * specification attribute, arrive without a schema migration.
 */
object Converters {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val stringMap = MapSerializer(String.serializer(), String.serializer())
    private val stringList = ListSerializer(String.serializer())

    @TypeConverter
    fun mapToJson(value: Map<String, String>?): String =
        json.encodeToString(stringMap, value.orEmpty())

    @TypeConverter
    fun jsonToMap(value: String?): Map<String, String> =
        if (value.isNullOrBlank()) emptyMap()
        else runCatching { json.decodeFromString(stringMap, value) }.getOrDefault(emptyMap())

    @TypeConverter
    fun listToJson(value: List<String>?): String =
        json.encodeToString(stringList, value.orEmpty())

    @TypeConverter
    fun jsonToList(value: String?): List<String> =
        if (value.isNullOrBlank()) emptyList()
        else runCatching { json.decodeFromString(stringList, value) }.getOrDefault(emptyList())
}
