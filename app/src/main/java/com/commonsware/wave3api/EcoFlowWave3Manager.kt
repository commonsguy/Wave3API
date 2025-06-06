@file:OptIn(ExperimentalSerializationApi::class)

package com.commonsware.wave3api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonIgnoreUnknownKeys
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

interface EcoFlowWave3Manager {
    suspend fun listDevices(): List<EcoFlowDevice>

    suspend fun changePowerState(
        serialNumber: String = BuildConfig.ECOFLOW_ONE_SERIAL,
        powerState: PowerState,
    )

    suspend fun testCall(): String?

    suspend fun changeSubMode(
        serialNumber: String = BuildConfig.ECOFLOW_ONE_SERIAL,
        subMode: SubMode,
    )

    suspend fun getFullState(serialNumber: String = BuildConfig.ECOFLOW_ONE_SERIAL): Map<String, String>?
}

private const val HASH_ALGORITHM = "HmacSHA256"
private const val HTTP_OK = 200
private const val NONCE_MIN = 100000
private const val NONCE_MAX = 999999
private const val ECOFLOW_URL_PREFIX = "https://api-a.ecoflow.com"

@Suppress("detekt:MagicNumber")
enum class PowerState(
    val ecoFlowCode: Int,
) {
    On(1),
    Standby(2),
    ShutDown(3),
}

@Suppress("detekt:MagicNumber")
enum class SubMode(
    val ecoFlowCode: Int,
) {
    Max(0),
    Eco(1),
    Sleep(2),
    Manual(3),
}

@Serializable
@JsonIgnoreUnknownKeys
data class EcoFlowDevice(
    @SerialName("sn") val serialNumber: String,
    val deviceName: String,
    val online: Int,
) {
    val isOnline = online == 1
}

interface EcoFlowParamSource {
    fun toParamList(): List<Pair<String, String>>
}

@Serializable
data class PowerStateParams(
    val powerMode: Int,
) : EcoFlowParamSource {
    override fun toParamList(): List<Pair<String, String>> =
        listOf(
            "params.powerMode" to powerMode.toString(),
        )
}

@Serializable
data class SubModeParams(
    val subMode: Int,
) : EcoFlowParamSource {
    override fun toParamList(): List<Pair<String, String>> =
        listOf(
            "params.subMode" to subMode.toString(),
        )
}

@Serializable
data class EcoFlowRequestWrapper<T : EcoFlowParamSource>(
    val id: Long = System.currentTimeMillis(),
    val version: String = "1.0",
    @SerialName("sn") val serialNumber: String,
    val moduleType: Int = 1,
    val operateType: String,
    val params: T,
) {
    fun toParamList(): List<Pair<String, String>> =
        listOf(
            "id" to id.toString(),
            "version" to version,
            "sn" to serialNumber,
            "moduleType" to moduleType.toString(),
            "operateType" to operateType,
        ) + params.toParamList()
}

@Serializable
@JsonIgnoreUnknownKeys
private data class EcoFlowResponseWrapper<T>(
    val code: String,
    val message: String,
    val data: T?,
)

@Serializable
@JsonIgnoreUnknownKeys
private data class EcoFlowSimpleResponseWrapper(
    val code: String,
    val message: String,
)

class EcoFlowWave3ManagerImpl : EcoFlowWave3Manager {
    private val okHttp =
        OkHttpClient
            .Builder()
            .addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BODY))
            .build()
    private val json = Json { encodeDefaults = true }

    override suspend fun listDevices(): List<EcoFlowDevice> = callEcoFlow<List<EcoFlowDevice>>("/iot-open/sign/device/list").orEmpty()

    override suspend fun changePowerState(
        serialNumber: String,
        powerState: PowerState,
    ) {
        val params = PowerStateParams(powerMode = powerState.ecoFlowCode)
        val request =
            EcoFlowRequestWrapper(
                serialNumber = serialNumber,
                operateType = "powerMode",
                params = params,
            )

        callEcoFlow<Any>(
            params = request.toParamList(),
            payload = json.encodeToString(request),
        )
    }

    override suspend fun changeSubMode(
        serialNumber: String,
        subMode: SubMode,
    ) {
        val params = SubModeParams(subMode = subMode.ecoFlowCode)
        val request =
            EcoFlowRequestWrapper(
                serialNumber = serialNumber,
                operateType = "subMode",
                params = params,
            )

        callEcoFlow<Any>(
            params = request.toParamList(),
            payload = json.encodeToString(request),
        )
    }

    override suspend fun getFullState(serialNumber: String) =
        callEcoFlow<Map<String, String>>(
            path = "/iot-open/sign/device/quota/all?sn=$serialNumber",
            params = listOf("sn" to serialNumber),
        )

    override suspend fun testCall(): String? {
        val request =
            signRequest(
                params =
                    listOf(
                        "params.cmdSet" to "11",
                        "params.eps" to "0",
                        "params.id" to "24",
                        "sn" to "123456789",
                    ),
                accessKey = "Fp4SvIprYSDPXtYJidEtUAd1o",
                secretKey = "WIbFEKre0s6sLnh4ei7SPUeYnptHG6V",
                nonce = "345164",
                timestamp = 1671171709428,
                request = Request.Builder().url("https://www.foo.com").build(),
            )

        return request.header("sign")
    }

    private suspend inline fun <reified T> callEcoFlow(
        path: String = "/iot-open/sign/device/quota",
        params: List<Pair<String, String>> = emptyList(),
        payload: String? = null,
        accessKey: String = BuildConfig.ECOFLOW_ACCESS_KEY,
        secretKey: String = BuildConfig.ECOFLOW_SECRET_KEY,
    ): T? {
        val baseRequest =
            Request
                .Builder()
                .url("$ECOFLOW_URL_PREFIX$path")
                .apply {
                    if (payload != null) put(payload.toRequestBody("application/json;charset=UTF-8".toMediaType()))
                }.build()
        val signedRequest =
            signRequest(baseRequest, params, accessKey = accessKey, secretKey = secretKey)

        return withContext(Dispatchers.IO) {
            val response = okHttp.newCall(signedRequest).execute()

            if (response.code == HTTP_OK) {
                response.body?.let { body ->
                    val rawBody = body.string()

                    if (payload == null) {
                        try {
                            val wrapper: EcoFlowResponseWrapper<T> = Json.decodeFromString(rawBody)

                            if (wrapper.code == "0") {
                                wrapper.data
                            } else {
                                throw EcoFlowException("Invalid response: $rawBody")
                            }
                        } catch (t: Throwable) {
                            val wrapper: EcoFlowSimpleResponseWrapper =
                                Json.decodeFromString(rawBody)

                            if (wrapper.code == "0") {
                                null
                            } else {
                                throw EcoFlowException("Invalid response: $rawBody")
                            }
                        }
                    } else {
                        val wrapper: EcoFlowSimpleResponseWrapper = Json.decodeFromString(rawBody)

                        if (wrapper.code == "0") {
                            null
                        } else {
                            throw EcoFlowException("Invalid response: $rawBody")
                        }
                    }
                } ?: run {
                    null
                }
            } else {
                throw EcoFlowException("Invalid response: $response")
            }
        }
    }

    @OptIn(ExperimentalStdlibApi::class)
    private fun signRequest(
        request: Request,
        params: List<Pair<String, String>>,
        timestamp: Long = System.currentTimeMillis(),
        nonce: String = Random.nextInt(NONCE_MIN, NONCE_MAX).toString(),
        accessKey: String = BuildConfig.ECOFLOW_ACCESS_KEY,
        secretKey: String = BuildConfig.ECOFLOW_SECRET_KEY,
    ): Request {
        val baseQuery =
            HttpUrl
                .Builder()
                .also { builder ->
                    builder.scheme("https")
                    builder.host("www.this-is-unused.com")
                    params
                        .sortedBy { it.first }
                        .forEach { builder.addQueryParameter(it.first, it.second) }
                }.build()
                .encodedQuery
        val querySuffix = "accessKey=$accessKey&nonce=$nonce&timestamp=$timestamp"
        val fullQuery = if (baseQuery.isNullOrEmpty()) querySuffix else "$baseQuery&$querySuffix"
        val mac =
            Mac
                .getInstance(HASH_ALGORITHM)
                .also { it.init(SecretKeySpec(secretKey.toByteArray(), HASH_ALGORITHM)) }
        val sig = mac.doFinal(fullQuery.toByteArray()).toHexString()

        val builder = request.newBuilder()

        return builder
            .addHeader("accessKey", accessKey)
            .addHeader("nonce", nonce)
            .addHeader("timestamp", timestamp.toString())
            .addHeader("sign", sig)
            .build()
    }
}

class EcoFlowException(
    message: String,
) : RuntimeException("EcoFlow - $message")
