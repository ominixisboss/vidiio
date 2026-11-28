package com.ominix.vidiio.data.live

import android.util.Log
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class XtreamAccount(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val serverUrl: String,
    val username: String,
    val password: String,
    val isActive: Boolean = true
) {
    val cleanServerUrl: String
        get() = serverUrl.trim().removeSuffix("/")
}

@JsonClass(generateAdapter = true)
data class XtreamUserInfo(
    val username: String? = null,
    val status: String? = null,
    @Json(name = "exp_date") val expDate: String? = null,
    @Json(name = "is_trial") val isTrial: String? = null,
    @Json(name = "active_cons") val activeCons: String? = null,
    @Json(name = "max_connections") val maxConnections: String? = null
)

@JsonClass(generateAdapter = true)
data class XtreamServerInfo(
    val url: String? = null,
    val port: String? = null,
    @Json(name = "server_protocol") val serverProtocol: String? = null,
    @Json(name = "rtmp_port") val rtmpPort: String? = null,
    val timezone: String? = null
)

@JsonClass(generateAdapter = true)
data class XtreamAuthResponse(
    @Json(name = "user_info") val userInfo: XtreamUserInfo? = null,
    @Json(name = "server_info") val serverInfo: XtreamServerInfo? = null
)

@JsonClass(generateAdapter = true)
data class XtreamCategory(
    @Json(name = "category_id") val categoryId: String,
    @Json(name = "category_name") val categoryName: String,
    @Json(name = "parent_id") val parentId: Int = 0
)

@JsonClass(generateAdapter = true)
data class XtreamLiveStream(
    @Json(name = "stream_id") val streamId: Int,
    val name: String,
    @Json(name = "stream_icon") val streamIcon: String? = null,
    @Json(name = "epg_channel_id") val epgChannelId: String? = null,
    @Json(name = "category_id") val categoryId: String? = null,
    @Json(name = "direct_source") val directSource: String? = null
)

class XtreamService(
    private val client: OkHttpClient,
    moshi: Moshi = Moshi.Builder().build()
) {
    private val authAdapter = moshi.adapter(XtreamAuthResponse::class.java)
    private val catAdapter = moshi.adapter<List<XtreamCategory>>(Types.newParameterizedType(List::class.java, XtreamCategory::class.java))
    private val streamAdapter = moshi.adapter<List<XtreamLiveStream>>(Types.newParameterizedType(List::class.java, XtreamLiveStream::class.java))

    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    suspend fun authenticate(account: XtreamAccount): XtreamAuthResponse? = withContext(Dispatchers.IO) {
        val url = "${account.cleanServerUrl}/player_api.php?username=${account.username}&password=${account.password}"
        try {
            val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext null
                val body = resp.body?.string().orEmpty()
                authAdapter.fromJson(body)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("XtreamService", "Auth error for ${account.serverUrl}", e)
            null
        }
    }

    suspend fun getLiveCategories(account: XtreamAccount): List<XtreamCategory> = withContext(Dispatchers.IO) {
        val url = "${account.cleanServerUrl}/player_api.php?username=${account.username}&password=${account.password}&action=get_live_categories"
        try {
            val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                catAdapter.fromJson(body).orEmpty()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("XtreamService", "Categories error for ${account.serverUrl}", e)
            emptyList()
        }
    }

    suspend fun getLiveStreams(account: XtreamAccount, categoryId: String? = null): List<XtreamLiveStream> = withContext(Dispatchers.IO) {
        var url = "${account.cleanServerUrl}/player_api.php?username=${account.username}&password=${account.password}&action=get_live_streams"
        if (!categoryId.isNullOrEmpty()) {
            url += "&category_id=$categoryId"
        }
        try {
            val req = Request.Builder().url(url).header("User-Agent", userAgent).build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext emptyList()
                val body = resp.body?.string().orEmpty()
                streamAdapter.fromJson(body).orEmpty()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e("XtreamService", "Streams error for ${account.serverUrl}", e)
            emptyList()
        }
    }

    fun buildStreamUrl(account: XtreamAccount, streamId: Int, extension: String = "m3u8"): String {
        return "${account.cleanServerUrl}/live/${account.username}/${account.password}/$streamId.$extension"
    }
}
