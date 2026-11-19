package aarnav100.developer.biblescroll.core.content.net

import kotlinx.serialization.json.JsonObject
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Url

/**
 * Cloud Firestore REST (https://firestore.googleapis.com/v1/), for the Community vote counter only. Calls are
 * unauthenticated: no token, no API key, no identifiers; security rules decide what's allowed (backend/firestore.rules).
 */
interface FirestoreApi {
    /** documents:commit. Response kept raw so the status decides retry vs give up. */
    @POST
    suspend fun commit(@Url url: String, @Body body: JsonObject): Response<ResponseBody>

    /** documents:batchGet. Returns a JSON array of {found}|{missing}. */
    @POST
    suspend fun batchGet(@Url url: String, @Body body: JsonObject): Response<ResponseBody>
}
