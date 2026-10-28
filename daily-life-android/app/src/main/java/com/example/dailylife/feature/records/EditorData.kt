package com.example.dailylife.feature.records

import com.example.dailylife.data.local.CardField
import com.example.dailylife.data.local.newId
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

data class EditorData(
    val recordId: String = newId(), val typeId: String = "", val baseRevision: Long = 0,
    val title: String = "", val body: String = "", val url: String = "",
    val diaryDate: String = LocalDate.now().toString(), val mood: String = "",
    val folderId: String? = null, val tags: Set<String> = emptySet(),
    val organized: Boolean = false, val fields: List<CardField> = emptyList(),
    val allowDuplicateUrl: Boolean = false, val projectId:String? = null,
    val presentation: String = "{}"
) {
    fun json(): String = JSONObject().apply {
        put("recordId", recordId); put("typeId", typeId); put("baseRevision", baseRevision)
        put("title", title); put("body", body); put("url", url); put("diaryDate", diaryDate)
        put("mood", mood); put("folderId", folderId ?: JSONObject.NULL)
        put("tags", JSONArray(tags.toList())); put("organized", organized)
        put("allowDuplicateUrl", allowDuplicateUrl)
        put("projectId",projectId ?: JSONObject.NULL)
        put("presentation", presentation)
        put("fields", JSONArray().apply { fields.forEach { f -> put(JSONObject().apply {
            put("id", f.id); put("name", f.name); put("value", f.value); put("valueType", f.valueType)
            put("private", f.isPrivate); put("sortOrder", f.sortOrder)
        }) } })
    }.toString()
    companion object {
        fun fromJson(value: String): EditorData {
            val o = JSONObject(value)
            val id = o.getString("recordId")
            val tags = o.optJSONArray("tags") ?: JSONArray()
            val fields = o.optJSONArray("fields") ?: JSONArray()
            return EditorData(id, o.getString("typeId"), o.optLong("baseRevision"),
                o.optString("title"), o.optString("body"), o.optString("url"),
                o.optString("diaryDate", LocalDate.now().toString()), o.optString("mood"),
                if (o.isNull("folderId")) null else o.optString("folderId").ifBlank { null },
                (0 until tags.length()).map { tags.getString(it) }.toSet(), o.optBoolean("organized"),
                (0 until fields.length()).map { i -> val f = fields.getJSONObject(i)
                    CardField(f.getString("id"), id, f.optString("name"), f.optString("value"),
                        f.optString("valueType", "text"), f.optBoolean("private"), f.optInt("sortOrder", i))
                }, o.optBoolean("allowDuplicateUrl"), if(o.isNull("projectId")) null else o.optString("projectId").ifBlank {null},
                o.optString("presentation", "{}"))
        }
    }
}
