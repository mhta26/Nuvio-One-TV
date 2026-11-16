package com.nuvio.tv.data.repository

import com.nuvio.tv.data.remote.api.TraktApi
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceCodeRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktDeviceTokenRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRefreshTokenRequestDto
import com.nuvio.tv.data.remote.dto.trakt.TraktRevokeRequestDto
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

/** Exercise Retrofit routing and generated Moshi adapters without any account/network access. */
class TraktOAuthContractTest {
    private val moshi = Moshi.Builder().build()

    @Test
    fun `device codes use the auth host and public client ID`() = runTest {
        val request = capture { it.requestDeviceCode(TraktDeviceCodeRequestDto("public-app")) }
        assertAuthRequest(request, "/oauth/device/code", mapOf("client_id" to "public-app"))
    }

    @Test
    fun `device token exchange does not send a client secret`() = runTest {
        val request = capture {
            it.requestDeviceToken(TraktDeviceTokenRequestDto(code = "device-code", clientId = "public-app"))
        }
        assertAuthRequest(request, "/oauth/device/token", mapOf("code" to "device-code", "client_id" to "public-app"))
    }

    @Test
    fun `refresh uses the auth host without a client secret`() = runTest {
        val request = capture {
            it.refreshToken(TraktRefreshTokenRequestDto(
                refreshToken = "refresh-token",
                clientId = "public-app",
                redirectUri = "https://example.org/callback"
            ))
        }
        assertAuthRequest(request, "/oauth/token", mapOf(
            "refresh_token" to "refresh-token",
            "client_id" to "public-app",
            "redirect_uri" to "https://example.org/callback",
            "grant_type" to "refresh_token"
        ))
    }

    @Test
    fun `revocation does not require a client secret`() = runTest {
        val request = capture { it.revokeToken(TraktRevokeRequestDto(token = "access-token", clientId = "public-app")) }
        assertAuthRequest(request, "/oauth/revoke", mapOf("token" to "access-token", "client_id" to "public-app"))
    }

    @Test
    fun `ordinary API calls retain the API host and authorization header`() = runTest {
        val request = capture { it.getUserSettings(authorization = "Bearer access-token") }
        assertEquals("api.trakt.tv", request.url.host)
        assertEquals("/users/settings", request.url.encodedPath)
        assertEquals("Bearer access-token", request.header("Authorization"))
    }

    private suspend fun capture(call: suspend (TraktApi) -> Response<*>): Request {
        var captured: Request? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            captured = chain.request()
            // A rejected response avoids decoding user data; the request still uses real adapters.
            okhttp3.Response.Builder()
                .request(chain.request())
                .protocol(Protocol.HTTP_1_1)
                .code(400)
                .message("Test response")
                .body("{}".toResponseBody())
                .build()
        }.build()
        try {
            val api = Retrofit.Builder()
                .baseUrl("https://api.trakt.tv/")
                .client(client)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .build()
                .create(TraktApi::class.java)
            call(api)
            assertNotNull(captured)
            return checkNotNull(captured)
        } finally {
            client.dispatcher.executorService.shutdown()
            client.connectionPool.evictAll()
        }
    }

    private fun assertAuthRequest(request: Request, path: String, expected: Map<String, String>) {
        assertEquals("https", request.url.scheme)
        assertEquals("auth.trakt.tv", request.url.host)
        assertEquals(path, request.url.encodedPath)
        assertEquals("POST", request.method)
        val buffer = Buffer()
        checkNotNull(request.body).writeTo(buffer)
        val body = checkNotNull(moshi.adapter(Map::class.java).fromJson(buffer.readUtf8()))
        assertEquals(expected, body)
        assertFalse(body.containsKey("client_secret"))
    }
}
