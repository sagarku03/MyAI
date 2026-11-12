package com.jarvisai.assistant.agent.core

import com.squareup.wire.GrpcClient
import exa.language_server_pb.GrpcLanguageServerServiceClient
import exa.language_server_pb.LanguageServerServiceClient

val AgyLanguageService: LanguageServerServiceClient by lazy {
    val hubUrl = com.jarvisai.assistant.data.remote.JarvisHubConfig.getHubBaseUrl()
    GrpcLanguageServerServiceClient(
        GrpcClient.Builder()
            .client(AgyOkHttpClient.client)
            .baseUrl(hubUrl)
            .build()
    )
}
