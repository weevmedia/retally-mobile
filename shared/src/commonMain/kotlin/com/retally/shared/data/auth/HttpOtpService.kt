package com.retally.shared.data.auth

import com.retally.shared.domain.auth.CodeRequest
import com.retally.shared.domain.auth.OtpException
import com.retally.shared.domain.auth.OtpService
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable

/**
 * Production OTP backend: Firebase Cloud Functions 2nd gen (`lookupAccount` /
 * `requestOtp` / `verifyOtp`, see backend/functions). The functions email the 6-digit
 * code via Resend and return a Firebase custom token on successful verification;
 * the app signs in with that token (see
 * [com.retally.shared.presentation.auth.OtpContract.Store]). `lookupAccount`
 * reports whether the email already has an account without sending a code, so the
 * app only sends a code after a new user completes the name form.
 */
class HttpOtpService(
    private val client: HttpClient,
    private val baseUrl: String = DEFAULT_BASE_URL,
) : OtpService {

    @Serializable
    private data class RequestBody(val email: String)

    @Serializable
    private data class VerifyBody(val email: String, val code: String, val displayName: String)

    @Serializable
    private data class VerifyResponse(val customToken: String = "")

    @Serializable
    private data class RequestCodeResponse(
        val exists: Boolean = false,
        val firstName: String? = null,
    )

    override suspend fun lookupAccount(email: String): CodeRequest {
        val response = client.post("$baseUrl/lookupAccount") {
            contentType(ContentType.Application.Json)
            setBody(RequestBody(email))
        }
        if (!response.status.isSuccess()) throw OtpException.SendFailed
        val body = response.body<RequestCodeResponse>()
        return CodeRequest(
            exists = body.exists,
            firstName = body.firstName?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun requestCode(email: String): CodeRequest {
        val response = client.post("$baseUrl/requestOtp") {
            contentType(ContentType.Application.Json)
            setBody(RequestBody(email))
        }
        when {
            response.status == HttpStatusCode.TooManyRequests -> throw OtpException.RateLimited
            !response.status.isSuccess() -> throw OtpException.SendFailed
        }
        val body = response.body<RequestCodeResponse>()
        return CodeRequest(
            exists = body.exists,
            firstName = body.firstName?.takeIf { it.isNotBlank() },
        )
    }

    override suspend fun verifyCode(email: String, code: String, displayName: String): String {
        val response = client.post("$baseUrl/verifyOtp") {
            contentType(ContentType.Application.Json)
            setBody(VerifyBody(email, code, displayName))
        }
        return when (response.status) {
            HttpStatusCode.OK -> response.body<VerifyResponse>().customToken
                .takeIf { it.isNotBlank() } ?: throw OtpException.SendFailed
            HttpStatusCode.Unauthorized -> throw OtpException.CodeMismatch
            HttpStatusCode.Gone, HttpStatusCode.NotFound -> throw OtpException.CodeExpired
            HttpStatusCode.TooManyRequests -> throw OtpException.TooManyAttempts
            else -> throw OtpException.SendFailed
        }
    }

    companion object {
        /** Cloud Functions base URL for project retally-c5212, region us-east1. */
        const val DEFAULT_BASE_URL = "https://us-east1-retally-c5212.cloudfunctions.net"
    }
}
