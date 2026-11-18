package aarnav100.developer.biblescroll.core.content.net

import aarnav100.developer.biblescroll.core.content.model.Manifest
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Streaming
import retrofit2.http.Url

/** The study content CDN (Firebase Hosting). Only our own files; no identifiers or cookies are sent. */
interface ContentApi {
    @GET("manifest.json")
    suspend fun manifest(): Manifest

    /** A book file (absolute URL from the manifest). Streamed so large books never sit in memory. */
    @Streaming
    @GET
    suspend fun download(@Url url: String): ResponseBody
}
