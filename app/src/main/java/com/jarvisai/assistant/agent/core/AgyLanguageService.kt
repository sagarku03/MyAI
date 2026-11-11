package com.jarvisai.assistant.agent.core

import com.squareup.wire.GrpcClient
import exa.language_server_pb.GrpcLanguageServerServiceClient
import exa.language_server_pb.LanguageServerServiceClient

val AgyLanguageService: LanguageServerServiceClient by lazy {
    val hubUrl = "http://127.0.0.1:1235"
    GrpcLanguageServerServiceClient(
        GrpcClient.Builder()
            .client(AgyOkHttpClient.client)
            .baseUrl(hubUrl)
            .build()
    )
}
