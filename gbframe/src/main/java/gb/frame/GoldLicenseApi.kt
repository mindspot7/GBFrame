package gb.frame

import android.content.Context
import android.provider.Settings
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Server-side verification for the shared Google Play Gold entitlement. */
internal object GoldLicenseApi {
    enum class Operation(val path: String) {
        VerifyPurchase("/api/public/verify-purchase"),
        CheckLicense("/api/public/check-license"),
    }

    sealed interface Result {
        data object Licensed : Result
        data class Rejected(val reason: String?) : Result
        data object Unavailable : Result
    }

    fun validate(context: Context, productId: String, purchaseToken: String, operation: Operation): Result {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            ?.takeIf(String::isNotBlank) ?: return Result.Unavailable
        val payload = JSONObject()
            .put("packageName", context.packageName)
            .put("productId", productId)
            .put("purchaseToken", purchaseToken)
            .put("deviceId", deviceId)
            .toString()
            .toByteArray(Charsets.UTF_8)

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://gblicence.lovable.app${operation.path}").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 12_000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.outputStream.use { it.write(payload) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use(::readLimited) ?: return Result.Unavailable
            val json = runCatching { JSONObject(body) }.getOrNull() ?: return Result.Unavailable
            if (status in 200..299 && json.optBoolean("success") && json.optBoolean("licensed")) {
                Result.Licensed
            } else if (status in 400..499 || (status in 200..299 && !json.optBoolean("licensed"))) {
                Result.Rejected(json.optString("error").takeIf(String::isNotBlank))
            } else {
                Result.Unavailable
            }
        } catch (_: Exception) {
            Result.Unavailable
        } finally {
            connection?.disconnect()
        }
    }

    private fun readLimited(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(2048)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > MAX_RESPONSE_BYTES) throw IOException("License response is too large")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private const val MAX_RESPONSE_BYTES = 32 * 1024
}
