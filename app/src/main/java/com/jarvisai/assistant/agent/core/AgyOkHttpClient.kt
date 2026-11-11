package com.jarvisai.assistant.agent.core

import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object AgyOkHttpClient {
    val client: OkHttpClient by lazy {
        val dispatcher = Dispatcher().apply {
            maxRequests = 256
            maxRequestsPerHost = 256
        }
        val connectionPool = ConnectionPool(32, 5, TimeUnit.MINUTES)

        OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectionPool(connectionPool)
            .protocols(listOf(okhttp3.Protocol.H2_PRIOR_KNOWLEDGE))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS) // Indefinite read timeout for streaming
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                    .header("User-Agent", "MyAI-Android-Native")
                    .header("X-Grpc-Web", "1")
                chain.proceed(requestBuilder.build())
            }
            .build()
    }
}
