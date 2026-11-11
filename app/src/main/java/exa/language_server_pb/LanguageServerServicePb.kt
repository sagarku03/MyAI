package exa.language_server_pb

import com.squareup.wire.FieldEncoding
import com.squareup.wire.GrpcCall
import com.squareup.wire.GrpcClient
import com.squareup.wire.GrpcMethod
import com.squareup.wire.GrpcStreamingCall
import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import com.squareup.wire.ProtoReader
import com.squareup.wire.ProtoWriter
import com.squareup.wire.ReverseProtoWriter
import com.squareup.wire.Service
import com.squareup.wire.Syntax.PROTO_3
import com.squareup.wire.WireField
import com.squareup.wire.internal.immutableCopyOf
import okio.ByteString

interface LanguageServerServiceClient : Service {
    fun StartCascade(): GrpcCall<StartCascadeRequest, StartCascadeResponse>
    fun SendUserCascadeMessage(): GrpcCall<SendUserCascadeMessageRequest, SendUserCascadeMessageResponse>
    fun GetCascadeTrajectory(): GrpcCall<GetCascadeTrajectoryRequest, GetCascadeTrajectoryResponse>
    fun StreamCascadeReactiveUpdates(): GrpcStreamingCall<StreamCascadeReactiveUpdatesRequest, StreamCascadeReactiveUpdatesResponse>
    fun CancelCascadeInvocation(): GrpcCall<CancelCascadeInvocationRequest, CancelCascadeInvocationResponse>
    fun HandleCascadeUserInteraction(): GrpcCall<HandleCascadeUserInteractionRequest, HandleCascadeUserInteractionResponse>
    fun GetUserStatus(): GrpcCall<GetUserStatusRequest, GetUserStatusResponse>
}

class GrpcLanguageServerServiceClient(
    private val client: GrpcClient
) : LanguageServerServiceClient {
    override fun StartCascade(): GrpcCall<StartCascadeRequest, StartCascadeResponse> =
        client.newCall(
            GrpcMethod(
                path = "/exa.language_server_pb.LanguageServerService/StartCascade",
                requestAdapter = StartCascadeRequest.ADAPTER,
                responseAdapter = StartCascadeResponse.ADAPTER
            )
        )

    override fun SendUserCascadeMessage(): GrpcCall<SendUserCascadeMessageRequest, SendUserCascadeMessageResponse> =
        client.newCall(
            GrpcMethod(
                path = "/exa.language_server_pb.LanguageServerService/SendUserCascadeMessage",
                requestAdapter = SendUserCascadeMessageRequest.ADAPTER,
                responseAdapter = SendUserCascadeMessageResponse.ADAPTER
            )
        )

    override fun GetCascadeTrajectory(): GrpcCall<GetCascadeTrajectoryRequest, GetCascadeTrajectoryResponse> =
        client.newCall(
            GrpcMethod(
                path = "/exa.language_server_pb.LanguageServerService/GetCascadeTrajectory",
                requestAdapter = GetCascadeTrajectoryRequest.ADAPTER,
                responseAdapter = GetCascadeTrajectoryResponse.ADAPTER
            )
        )

    override fun StreamCascadeReactiveUpdates(): GrpcStreamingCall<StreamCascadeReactiveUpdatesRequest, StreamCascadeReactiveUpdatesResponse> =
        client.newStreamingCall(
            GrpcMethod(
                path = "/exa.language_server_pb.LanguageServerService/StreamCascadeReactiveUpdates",
                requestAdapter = StreamCascadeReactiveUpdatesRequest.ADAPTER,
                responseAdapter = StreamCascadeReactiveUpdatesResponse.ADAPTER
            )
        )

    override fun CancelCascadeInvocation(): GrpcCall<CancelCascadeInvocationRequest, CancelCascadeInvocationResponse> =
        client.newCall(
            GrpcMethod(
                path = "/exa.language_server_pb.LanguageServerService/CancelCascadeInvocation",
                requestAdapter = CancelCascadeInvocationRequest.ADAPTER,
                responseAdapter = CancelCascadeInvocationResponse.ADAPTER
            )
        )

    override fun HandleCascadeUserInteraction(): GrpcCall<HandleCascadeUserInteractionRequest, HandleCascadeUserInteractionResponse> =
        client.newCall(
            GrpcMethod(
                path = "/exa.language_server_pb.LanguageServerService/HandleCascadeUserInteraction",
                requestAdapter = HandleCascadeUserInteractionRequest.ADAPTER,
                responseAdapter = HandleCascadeUserInteractionResponse.ADAPTER
            )
        )

    override fun GetUserStatus(): GrpcCall<GetUserStatusRequest, GetUserStatusResponse> =
        client.newCall(
            GrpcMethod(
                path = "/exa.language_server_pb.LanguageServerService/GetUserStatus",
                requestAdapter = GetUserStatusRequest.ADAPTER,
                responseAdapter = GetUserStatusResponse.ADAPTER
            )
        )
}

class Metadata(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val ide_name: String = "",
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val ide_version: String = "",
    @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val extension_name: String = "",
    @field:WireField(tag = 4, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val extension_version: String = "",
    @field:WireField(tag = 5, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val api_key: String = "",
    @field:WireField(tag = 6, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val locale: String = "",
    @field:WireField(tag = 7, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val session_id: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<Metadata, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<Metadata> = object : ProtoAdapter<Metadata>(
            FieldEncoding.LENGTH_DELIMITED,
            Metadata::class,
            "type.googleapis.com/exa.language_server_pb.Metadata",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: Metadata): Int {
                var size = value.unknownFields.size
                if (value.ide_name != "") size += ProtoAdapter.STRING.encodedSizeWithTag(1, value.ide_name)
                if (value.ide_version != "") size += ProtoAdapter.STRING.encodedSizeWithTag(2, value.ide_version)
                if (value.extension_name != "") size += ProtoAdapter.STRING.encodedSizeWithTag(3, value.extension_name)
                if (value.extension_version != "") size += ProtoAdapter.STRING.encodedSizeWithTag(4, value.extension_version)
                if (value.api_key != "") size += ProtoAdapter.STRING.encodedSizeWithTag(5, value.api_key)
                if (value.locale != "") size += ProtoAdapter.STRING.encodedSizeWithTag(6, value.locale)
                if (value.session_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(7, value.session_id)
                return size
            }

            override fun encode(writer: ProtoWriter, value: Metadata) {
                if (value.ide_name != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.ide_name)
                if (value.ide_version != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.ide_version)
                if (value.extension_name != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.extension_name)
                if (value.extension_version != "") ProtoAdapter.STRING.encodeWithTag(writer, 4, value.extension_version)
                if (value.api_key != "") ProtoAdapter.STRING.encodeWithTag(writer, 5, value.api_key)
                if (value.locale != "") ProtoAdapter.STRING.encodeWithTag(writer, 6, value.locale)
                if (value.session_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 7, value.session_id)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: Metadata) {
                writer.writeBytes(value.unknownFields)
                if (value.session_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 7, value.session_id)
                if (value.locale != "") ProtoAdapter.STRING.encodeWithTag(writer, 6, value.locale)
                if (value.api_key != "") ProtoAdapter.STRING.encodeWithTag(writer, 5, value.api_key)
                if (value.extension_version != "") ProtoAdapter.STRING.encodeWithTag(writer, 4, value.extension_version)
                if (value.extension_name != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.extension_name)
                if (value.ide_version != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.ide_version)
                if (value.ide_name != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.ide_name)
            }

            override fun decode(reader: ProtoReader): Metadata {
                var ide_name = ""
                var ide_version = ""
                var extension_name = ""
                var extension_version = ""
                var api_key = ""
                var locale = ""
                var session_id = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> ide_name = ProtoAdapter.STRING.decode(reader)
                        2 -> ide_version = ProtoAdapter.STRING.decode(reader)
                        3 -> extension_name = ProtoAdapter.STRING.decode(reader)
                        4 -> extension_version = ProtoAdapter.STRING.decode(reader)
                        5 -> api_key = ProtoAdapter.STRING.decode(reader)
                        6 -> locale = ProtoAdapter.STRING.decode(reader)
                        7 -> session_id = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return Metadata(ide_name, ide_version, extension_name, extension_version, api_key, locale, session_id, unknownFields)
            }

            override fun redact(value: Metadata): Metadata = value
        }
    }
}

class ModelOrAlias(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#INT32", oneofName = "choice")
    val model: Int? = null,
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#INT32", oneofName = "choice")
    val alias: Int? = null,
    @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#STRING", oneofName = "choice")
    val model_uid: String? = null,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<ModelOrAlias, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<ModelOrAlias> = object : ProtoAdapter<ModelOrAlias>(
            FieldEncoding.LENGTH_DELIMITED,
            ModelOrAlias::class,
            "type.googleapis.com/exa.language_server_pb.ModelOrAlias",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: ModelOrAlias): Int {
                var size = value.unknownFields.size
                size += ProtoAdapter.INT32.encodedSizeWithTag(1, value.model)
                size += ProtoAdapter.INT32.encodedSizeWithTag(2, value.alias)
                size += ProtoAdapter.STRING.encodedSizeWithTag(3, value.model_uid)
                return size
            }

            override fun encode(writer: ProtoWriter, value: ModelOrAlias) {
                ProtoAdapter.INT32.encodeWithTag(writer, 1, value.model)
                ProtoAdapter.INT32.encodeWithTag(writer, 2, value.alias)
                ProtoAdapter.STRING.encodeWithTag(writer, 3, value.model_uid)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: ModelOrAlias) {
                writer.writeBytes(value.unknownFields)
                ProtoAdapter.STRING.encodeWithTag(writer, 3, value.model_uid)
                ProtoAdapter.INT32.encodeWithTag(writer, 2, value.alias)
                ProtoAdapter.INT32.encodeWithTag(writer, 1, value.model)
            }

            override fun decode(reader: ProtoReader): ModelOrAlias {
                var model: Int? = null
                var alias: Int? = null
                var model_uid: String? = null
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> model = ProtoAdapter.INT32.decode(reader)
                        2 -> alias = ProtoAdapter.INT32.decode(reader)
                        3 -> model_uid = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return ModelOrAlias(model, alias, model_uid, unknownFields)
            }

            override fun redact(value: ModelOrAlias): ModelOrAlias = value
        }
    }
}

class CascadeConversationalPlannerConfig(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#INT32", label = WireField.Label.OMIT_IDENTITY)
    val planner_mode: Int = 0,
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#BOOL", label = WireField.Label.OMIT_IDENTITY)
    val agentic_mode: Boolean = false,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<CascadeConversationalPlannerConfig, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<CascadeConversationalPlannerConfig> = object : ProtoAdapter<CascadeConversationalPlannerConfig>(
            FieldEncoding.LENGTH_DELIMITED,
            CascadeConversationalPlannerConfig::class,
            "type.googleapis.com/exa.language_server_pb.CascadeConversationalPlannerConfig",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: CascadeConversationalPlannerConfig): Int {
                var size = value.unknownFields.size
                if (value.planner_mode != 0) size += ProtoAdapter.INT32.encodedSizeWithTag(1, value.planner_mode)
                if (value.agentic_mode) size += ProtoAdapter.BOOL.encodedSizeWithTag(2, value.agentic_mode)
                return size
            }

            override fun encode(writer: ProtoWriter, value: CascadeConversationalPlannerConfig) {
                if (value.planner_mode != 0) ProtoAdapter.INT32.encodeWithTag(writer, 1, value.planner_mode)
                if (value.agentic_mode) ProtoAdapter.BOOL.encodeWithTag(writer, 2, value.agentic_mode)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: CascadeConversationalPlannerConfig) {
                writer.writeBytes(value.unknownFields)
                if (value.agentic_mode) ProtoAdapter.BOOL.encodeWithTag(writer, 2, value.agentic_mode)
                if (value.planner_mode != 0) ProtoAdapter.INT32.encodeWithTag(writer, 1, value.planner_mode)
            }

            override fun decode(reader: ProtoReader): CascadeConversationalPlannerConfig {
                var planner_mode = 0
                var agentic_mode = false
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> planner_mode = ProtoAdapter.INT32.decode(reader)
                        2 -> agentic_mode = ProtoAdapter.BOOL.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return CascadeConversationalPlannerConfig(planner_mode, agentic_mode, unknownFields)
            }

            override fun redact(value: CascadeConversationalPlannerConfig): CascadeConversationalPlannerConfig = value
        }
    }
}

class CascadePlannerConfig(
    @field:WireField(tag = 1, adapter = "exa.language_server_pb.CascadeConversationalPlannerConfig#ADAPTER", oneofName = "config")
    val conversational: CascadeConversationalPlannerConfig? = null,
    @field:WireField(tag = 2, adapter = "exa.language_server_pb.ModelOrAlias#ADAPTER", label = WireField.Label.OMIT_IDENTITY)
    val plan_model: ModelOrAlias? = null,
    @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#BOOL", label = WireField.Label.OMIT_IDENTITY)
    val include_thinking: Boolean = false,
    @field:WireField(tag = 4, adapter = "com.squareup.wire.ProtoAdapter#INT32", label = WireField.Label.OMIT_IDENTITY)
    val thinking_budget: Int = 0,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<CascadePlannerConfig, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<CascadePlannerConfig> = object : ProtoAdapter<CascadePlannerConfig>(
            FieldEncoding.LENGTH_DELIMITED,
            CascadePlannerConfig::class,
            "type.googleapis.com/exa.language_server_pb.CascadePlannerConfig",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: CascadePlannerConfig): Int {
                var size = value.unknownFields.size
                size += CascadeConversationalPlannerConfig.ADAPTER.encodedSizeWithTag(1, value.conversational)
                if (value.plan_model != null) size += ModelOrAlias.ADAPTER.encodedSizeWithTag(2, value.plan_model)
                if (value.include_thinking) size += ProtoAdapter.BOOL.encodedSizeWithTag(3, value.include_thinking)
                if (value.thinking_budget != 0) size += ProtoAdapter.INT32.encodedSizeWithTag(4, value.thinking_budget)
                return size
            }

            override fun encode(writer: ProtoWriter, value: CascadePlannerConfig) {
                CascadeConversationalPlannerConfig.ADAPTER.encodeWithTag(writer, 1, value.conversational)
                if (value.plan_model != null) ModelOrAlias.ADAPTER.encodeWithTag(writer, 2, value.plan_model)
                if (value.include_thinking) ProtoAdapter.BOOL.encodeWithTag(writer, 3, value.include_thinking)
                if (value.thinking_budget != 0) ProtoAdapter.INT32.encodeWithTag(writer, 4, value.thinking_budget)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: CascadePlannerConfig) {
                writer.writeBytes(value.unknownFields)
                if (value.thinking_budget != 0) ProtoAdapter.INT32.encodeWithTag(writer, 4, value.thinking_budget)
                if (value.include_thinking) ProtoAdapter.BOOL.encodeWithTag(writer, 3, value.include_thinking)
                if (value.plan_model != null) ModelOrAlias.ADAPTER.encodeWithTag(writer, 2, value.plan_model)
                CascadeConversationalPlannerConfig.ADAPTER.encodeWithTag(writer, 1, value.conversational)
            }

            override fun decode(reader: ProtoReader): CascadePlannerConfig {
                var conversational: CascadeConversationalPlannerConfig? = null
                var plan_model: ModelOrAlias? = null
                var include_thinking = false
                var thinking_budget = 0
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> conversational = CascadeConversationalPlannerConfig.ADAPTER.decode(reader)
                        2 -> plan_model = ModelOrAlias.ADAPTER.decode(reader)
                        3 -> include_thinking = ProtoAdapter.BOOL.decode(reader)
                        4 -> thinking_budget = ProtoAdapter.INT32.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return CascadePlannerConfig(conversational, plan_model, include_thinking, thinking_budget, unknownFields)
            }

            override fun redact(value: CascadePlannerConfig): CascadePlannerConfig = value
        }
    }
}

class McpServerCommand(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val server_name: String = "",
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val command: String = "",
    @field:WireField(tag = 5, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val server_url: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<McpServerCommand, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<McpServerCommand> = object : ProtoAdapter<McpServerCommand>(
            FieldEncoding.LENGTH_DELIMITED,
            McpServerCommand::class,
            "type.googleapis.com/exa.language_server_pb.McpServerCommand",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: McpServerCommand): Int {
                var size = value.unknownFields.size
                if (value.server_name != "") size += ProtoAdapter.STRING.encodedSizeWithTag(1, value.server_name)
                if (value.command != "") size += ProtoAdapter.STRING.encodedSizeWithTag(2, value.command)
                if (value.server_url != "") size += ProtoAdapter.STRING.encodedSizeWithTag(5, value.server_url)
                return size
            }

            override fun encode(writer: ProtoWriter, value: McpServerCommand) {
                if (value.server_name != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.server_name)
                if (value.command != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.command)
                if (value.server_url != "") ProtoAdapter.STRING.encodeWithTag(writer, 5, value.server_url)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: McpServerCommand) {
                writer.writeBytes(value.unknownFields)
                if (value.server_url != "") ProtoAdapter.STRING.encodeWithTag(writer, 5, value.server_url)
                if (value.command != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.command)
                if (value.server_name != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.server_name)
            }

            override fun decode(reader: ProtoReader): McpServerCommand {
                var server_name = ""
                var command = ""
                var server_url = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> server_name = ProtoAdapter.STRING.decode(reader)
                        2 -> command = ProtoAdapter.STRING.decode(reader)
                        5 -> server_url = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return McpServerCommand(server_name, command, server_url, unknownFields)
            }

            override fun redact(value: McpServerCommand): McpServerCommand = value
        }
    }
}

class CascadeConfig(
    @field:WireField(tag = 1, adapter = "exa.language_server_pb.CascadePlannerConfig#ADAPTER", label = WireField.Label.OMIT_IDENTITY)
    val planner_config: CascadePlannerConfig? = null,
    mcp_servers: List<McpServerCommand> = emptyList(),
    unknownFields: ByteString = ByteString.EMPTY
) : Message<CascadeConfig, Nothing>(ADAPTER, unknownFields) {
    @field:WireField(tag = 2, adapter = "exa.language_server_pb.McpServerCommand#ADAPTER", label = WireField.Label.REPEATED)
    val mcp_servers: List<McpServerCommand> = immutableCopyOf("mcp_servers", mcp_servers)

    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<CascadeConfig> = object : ProtoAdapter<CascadeConfig>(
            FieldEncoding.LENGTH_DELIMITED,
            CascadeConfig::class,
            "type.googleapis.com/exa.language_server_pb.CascadeConfig",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: CascadeConfig): Int {
                var size = value.unknownFields.size
                if (value.planner_config != null) size += CascadePlannerConfig.ADAPTER.encodedSizeWithTag(1, value.planner_config)
                size += McpServerCommand.ADAPTER.asRepeated().encodedSizeWithTag(2, value.mcp_servers)
                return size
            }

            override fun encode(writer: ProtoWriter, value: CascadeConfig) {
                if (value.planner_config != null) CascadePlannerConfig.ADAPTER.encodeWithTag(writer, 1, value.planner_config)
                McpServerCommand.ADAPTER.asRepeated().encodeWithTag(writer, 2, value.mcp_servers)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: CascadeConfig) {
                writer.writeBytes(value.unknownFields)
                McpServerCommand.ADAPTER.asRepeated().encodeWithTag(writer, 2, value.mcp_servers)
                if (value.planner_config != null) CascadePlannerConfig.ADAPTER.encodeWithTag(writer, 1, value.planner_config)
            }

            override fun decode(reader: ProtoReader): CascadeConfig {
                var planner_config: CascadePlannerConfig? = null
                val mcp_servers = mutableListOf<McpServerCommand>()
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> planner_config = CascadePlannerConfig.ADAPTER.decode(reader)
                        2 -> mcp_servers.add(McpServerCommand.ADAPTER.decode(reader))
                        else -> reader.readUnknownField(tag)
                    }
                }
                return CascadeConfig(planner_config, mcp_servers, unknownFields)
            }

            override fun redact(value: CascadeConfig): CascadeConfig = value
        }
    }
}

class StartCascadeRequest(
    @field:WireField(tag = 1, adapter = "exa.language_server_pb.Metadata#ADAPTER", label = WireField.Label.OMIT_IDENTITY)
    val metadata: Metadata? = null,
    @field:WireField(tag = 2, adapter = "exa.language_server_pb.CascadeConfig#ADAPTER", label = WireField.Label.OMIT_IDENTITY)
    val config: CascadeConfig? = null,
    @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val workspace_uri: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<StartCascadeRequest, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<StartCascadeRequest> = object : ProtoAdapter<StartCascadeRequest>(
            FieldEncoding.LENGTH_DELIMITED,
            StartCascadeRequest::class,
            "type.googleapis.com/exa.language_server_pb.StartCascadeRequest",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: StartCascadeRequest): Int {
                var size = value.unknownFields.size
                if (value.metadata != null) size += Metadata.ADAPTER.encodedSizeWithTag(1, value.metadata)
                if (value.config != null) size += CascadeConfig.ADAPTER.encodedSizeWithTag(2, value.config)
                if (value.workspace_uri != "") size += ProtoAdapter.STRING.encodedSizeWithTag(3, value.workspace_uri)
                return size
            }

            override fun encode(writer: ProtoWriter, value: StartCascadeRequest) {
                if (value.metadata != null) Metadata.ADAPTER.encodeWithTag(writer, 1, value.metadata)
                if (value.config != null) CascadeConfig.ADAPTER.encodeWithTag(writer, 2, value.config)
                if (value.workspace_uri != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.workspace_uri)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: StartCascadeRequest) {
                writer.writeBytes(value.unknownFields)
                if (value.workspace_uri != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.workspace_uri)
                if (value.config != null) CascadeConfig.ADAPTER.encodeWithTag(writer, 2, value.config)
                if (value.metadata != null) Metadata.ADAPTER.encodeWithTag(writer, 1, value.metadata)
            }

            override fun decode(reader: ProtoReader): StartCascadeRequest {
                var metadata: Metadata? = null
                var config: CascadeConfig? = null
                var workspace_uri = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> metadata = Metadata.ADAPTER.decode(reader)
                        2 -> config = CascadeConfig.ADAPTER.decode(reader)
                        3 -> workspace_uri = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return StartCascadeRequest(metadata, config, workspace_uri, unknownFields)
            }

            override fun redact(value: StartCascadeRequest): StartCascadeRequest = value
        }
    }
}

class StartCascadeResponse(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val cascade_id: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<StartCascadeResponse, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<StartCascadeResponse> = object : ProtoAdapter<StartCascadeResponse>(
            FieldEncoding.LENGTH_DELIMITED,
            StartCascadeResponse::class,
            "type.googleapis.com/exa.language_server_pb.StartCascadeResponse",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: StartCascadeResponse): Int {
                var size = value.unknownFields.size
                if (value.cascade_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(1, value.cascade_id)
                return size
            }

            override fun encode(writer: ProtoWriter, value: StartCascadeResponse) {
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: StartCascadeResponse) {
                writer.writeBytes(value.unknownFields)
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
            }

            override fun decode(reader: ProtoReader): StartCascadeResponse {
                var cascade_id = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> cascade_id = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return StartCascadeResponse(cascade_id, unknownFields)
            }

            override fun redact(value: StartCascadeResponse): StartCascadeResponse = value
        }
    }
}

class TextOrScopeItem(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", oneofName = "item")
    val text: String? = null,
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#STRING", oneofName = "item")
    val file_uri: String? = null,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<TextOrScopeItem, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<TextOrScopeItem> = object : ProtoAdapter<TextOrScopeItem>(
            FieldEncoding.LENGTH_DELIMITED,
            TextOrScopeItem::class,
            "type.googleapis.com/exa.language_server_pb.TextOrScopeItem",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: TextOrScopeItem): Int {
                var size = value.unknownFields.size
                size += ProtoAdapter.STRING.encodedSizeWithTag(1, value.text)
                size += ProtoAdapter.STRING.encodedSizeWithTag(2, value.file_uri)
                return size
            }

            override fun encode(writer: ProtoWriter, value: TextOrScopeItem) {
                ProtoAdapter.STRING.encodeWithTag(writer, 1, value.text)
                ProtoAdapter.STRING.encodeWithTag(writer, 2, value.file_uri)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: TextOrScopeItem) {
                writer.writeBytes(value.unknownFields)
                ProtoAdapter.STRING.encodeWithTag(writer, 2, value.file_uri)
                ProtoAdapter.STRING.encodeWithTag(writer, 1, value.text)
            }

            override fun decode(reader: ProtoReader): TextOrScopeItem {
                var text: String? = null
                var file_uri: String? = null
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> text = ProtoAdapter.STRING.decode(reader)
                        2 -> file_uri = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return TextOrScopeItem(text, file_uri, unknownFields)
            }

            override fun redact(value: TextOrScopeItem): TextOrScopeItem = value
        }
    }
}

class SendUserCascadeMessageRequest(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val cascade_id: String = "",
    items: List<TextOrScopeItem> = emptyList(),
    @field:WireField(tag = 3, adapter = "exa.language_server_pb.Metadata#ADAPTER", label = WireField.Label.OMIT_IDENTITY)
    val metadata: Metadata? = null,
    @field:WireField(tag = 4, adapter = "exa.language_server_pb.CascadeConfig#ADAPTER", label = WireField.Label.OMIT_IDENTITY)
    val cascade_config: CascadeConfig? = null,
    @field:WireField(tag = 5, adapter = "com.squareup.wire.ProtoAdapter#BOOL", label = WireField.Label.OMIT_IDENTITY)
    val blocking: Boolean = false,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<SendUserCascadeMessageRequest, Nothing>(ADAPTER, unknownFields) {
    @field:WireField(tag = 2, adapter = "exa.language_server_pb.TextOrScopeItem#ADAPTER", label = WireField.Label.REPEATED)
    val items: List<TextOrScopeItem> = immutableCopyOf("items", items)

    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<SendUserCascadeMessageRequest> = object : ProtoAdapter<SendUserCascadeMessageRequest>(
            FieldEncoding.LENGTH_DELIMITED,
            SendUserCascadeMessageRequest::class,
            "type.googleapis.com/exa.language_server_pb.SendUserCascadeMessageRequest",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: SendUserCascadeMessageRequest): Int {
                var size = value.unknownFields.size
                if (value.cascade_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(1, value.cascade_id)
                size += TextOrScopeItem.ADAPTER.asRepeated().encodedSizeWithTag(2, value.items)
                if (value.metadata != null) size += Metadata.ADAPTER.encodedSizeWithTag(3, value.metadata)
                if (value.cascade_config != null) size += CascadeConfig.ADAPTER.encodedSizeWithTag(4, value.cascade_config)
                if (value.blocking) size += ProtoAdapter.BOOL.encodedSizeWithTag(5, value.blocking)
                return size
            }

            override fun encode(writer: ProtoWriter, value: SendUserCascadeMessageRequest) {
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
                TextOrScopeItem.ADAPTER.asRepeated().encodeWithTag(writer, 2, value.items)
                if (value.metadata != null) Metadata.ADAPTER.encodeWithTag(writer, 3, value.metadata)
                if (value.cascade_config != null) CascadeConfig.ADAPTER.encodeWithTag(writer, 4, value.cascade_config)
                if (value.blocking) ProtoAdapter.BOOL.encodeWithTag(writer, 5, value.blocking)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: SendUserCascadeMessageRequest) {
                writer.writeBytes(value.unknownFields)
                if (value.blocking) ProtoAdapter.BOOL.encodeWithTag(writer, 5, value.blocking)
                if (value.cascade_config != null) CascadeConfig.ADAPTER.encodeWithTag(writer, 4, value.cascade_config)
                if (value.metadata != null) Metadata.ADAPTER.encodeWithTag(writer, 3, value.metadata)
                TextOrScopeItem.ADAPTER.asRepeated().encodeWithTag(writer, 2, value.items)
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
            }

            override fun decode(reader: ProtoReader): SendUserCascadeMessageRequest {
                var cascade_id = ""
                val items = mutableListOf<TextOrScopeItem>()
                var metadata: Metadata? = null
                var cascade_config: CascadeConfig? = null
                var blocking = false
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> cascade_id = ProtoAdapter.STRING.decode(reader)
                        2 -> items.add(TextOrScopeItem.ADAPTER.decode(reader))
                        3 -> metadata = Metadata.ADAPTER.decode(reader)
                        4 -> cascade_config = CascadeConfig.ADAPTER.decode(reader)
                        5 -> blocking = ProtoAdapter.BOOL.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return SendUserCascadeMessageRequest(cascade_id, items, metadata, cascade_config, blocking, unknownFields)
            }

            override fun redact(value: SendUserCascadeMessageRequest): SendUserCascadeMessageRequest = value
        }
    }
}

class SendUserCascadeMessageResponse(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val cascade_id: String = "",
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val response_text: String = "",
    @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val thinking_text: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<SendUserCascadeMessageResponse, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<SendUserCascadeMessageResponse> = object : ProtoAdapter<SendUserCascadeMessageResponse>(
            FieldEncoding.LENGTH_DELIMITED,
            SendUserCascadeMessageResponse::class,
            "type.googleapis.com/exa.language_server_pb.SendUserCascadeMessageResponse",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: SendUserCascadeMessageResponse): Int {
                var size = value.unknownFields.size
                if (value.cascade_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(1, value.cascade_id)
                if (value.response_text != "") size += ProtoAdapter.STRING.encodedSizeWithTag(2, value.response_text)
                if (value.thinking_text != "") size += ProtoAdapter.STRING.encodedSizeWithTag(3, value.thinking_text)
                return size
            }

            override fun encode(writer: ProtoWriter, value: SendUserCascadeMessageResponse) {
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
                if (value.response_text != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.response_text)
                if (value.thinking_text != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.thinking_text)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: SendUserCascadeMessageResponse) {
                writer.writeBytes(value.unknownFields)
                if (value.thinking_text != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.thinking_text)
                if (value.response_text != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.response_text)
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
            }

            override fun decode(reader: ProtoReader): SendUserCascadeMessageResponse {
                var cascade_id = ""
                var response_text = ""
                var thinking_text = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> cascade_id = ProtoAdapter.STRING.decode(reader)
                        2 -> response_text = ProtoAdapter.STRING.decode(reader)
                        3 -> thinking_text = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return SendUserCascadeMessageResponse(cascade_id, response_text, thinking_text, unknownFields)
            }

            override fun redact(value: SendUserCascadeMessageResponse): SendUserCascadeMessageResponse = value
        }
    }
}

class GetCascadeTrajectoryRequest(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val cascade_id: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<GetCascadeTrajectoryRequest, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<GetCascadeTrajectoryRequest> = object : ProtoAdapter<GetCascadeTrajectoryRequest>(
            FieldEncoding.LENGTH_DELIMITED,
            GetCascadeTrajectoryRequest::class,
            "type.googleapis.com/exa.language_server_pb.GetCascadeTrajectoryRequest",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: GetCascadeTrajectoryRequest): Int =
                value.unknownFields.size + if (value.cascade_id != "") ProtoAdapter.STRING.encodedSizeWithTag(1, value.cascade_id) else 0

            override fun encode(writer: ProtoWriter, value: GetCascadeTrajectoryRequest) {
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: GetCascadeTrajectoryRequest) {
                writer.writeBytes(value.unknownFields)
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
            }

            override fun decode(reader: ProtoReader): GetCascadeTrajectoryRequest {
                var cascade_id = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> cascade_id = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return GetCascadeTrajectoryRequest(cascade_id, unknownFields)
            }

            override fun redact(value: GetCascadeTrajectoryRequest): GetCascadeTrajectoryRequest = value
        }
    }
}

class GetCascadeTrajectoryResponse(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val cascade_id: String = "",
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val status: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<GetCascadeTrajectoryResponse, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<GetCascadeTrajectoryResponse> = object : ProtoAdapter<GetCascadeTrajectoryResponse>(
            FieldEncoding.LENGTH_DELIMITED,
            GetCascadeTrajectoryResponse::class,
            "type.googleapis.com/exa.language_server_pb.GetCascadeTrajectoryResponse",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: GetCascadeTrajectoryResponse): Int {
                var size = value.unknownFields.size
                if (value.cascade_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(1, value.cascade_id)
                if (value.status != "") size += ProtoAdapter.STRING.encodedSizeWithTag(2, value.status)
                return size
            }

            override fun encode(writer: ProtoWriter, value: GetCascadeTrajectoryResponse) {
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
                if (value.status != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.status)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: GetCascadeTrajectoryResponse) {
                writer.writeBytes(value.unknownFields)
                if (value.status != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.status)
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
            }

            override fun decode(reader: ProtoReader): GetCascadeTrajectoryResponse {
                var cascade_id = ""
                var status = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> cascade_id = ProtoAdapter.STRING.decode(reader)
                        2 -> status = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return GetCascadeTrajectoryResponse(cascade_id, status, unknownFields)
            }

            override fun redact(value: GetCascadeTrajectoryResponse): GetCascadeTrajectoryResponse = value
        }
    }
}

class StreamCascadeReactiveUpdatesRequest(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#UINT32", label = WireField.Label.OMIT_IDENTITY)
    val protocol_version: Int = 0,
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val id: String = "",
    @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val subscriber_id: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<StreamCascadeReactiveUpdatesRequest, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<StreamCascadeReactiveUpdatesRequest> = object : ProtoAdapter<StreamCascadeReactiveUpdatesRequest>(
            FieldEncoding.LENGTH_DELIMITED,
            StreamCascadeReactiveUpdatesRequest::class,
            "type.googleapis.com/exa.language_server_pb.StreamCascadeReactiveUpdatesRequest",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: StreamCascadeReactiveUpdatesRequest): Int {
                var size = value.unknownFields.size
                if (value.protocol_version != 0) size += ProtoAdapter.UINT32.encodedSizeWithTag(1, value.protocol_version)
                if (value.id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(2, value.id)
                if (value.subscriber_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(3, value.subscriber_id)
                return size
            }

            override fun encode(writer: ProtoWriter, value: StreamCascadeReactiveUpdatesRequest) {
                if (value.protocol_version != 0) ProtoAdapter.UINT32.encodeWithTag(writer, 1, value.protocol_version)
                if (value.id != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.id)
                if (value.subscriber_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.subscriber_id)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: StreamCascadeReactiveUpdatesRequest) {
                writer.writeBytes(value.unknownFields)
                if (value.subscriber_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.subscriber_id)
                if (value.id != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.id)
                if (value.protocol_version != 0) ProtoAdapter.UINT32.encodeWithTag(writer, 1, value.protocol_version)
            }

            override fun decode(reader: ProtoReader): StreamCascadeReactiveUpdatesRequest {
                var protocol_version = 0
                var id = ""
                var subscriber_id = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> protocol_version = ProtoAdapter.UINT32.decode(reader)
                        2 -> id = ProtoAdapter.STRING.decode(reader)
                        3 -> subscriber_id = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return StreamCascadeReactiveUpdatesRequest(protocol_version, id, subscriber_id, unknownFields)
            }

            override fun redact(value: StreamCascadeReactiveUpdatesRequest): StreamCascadeReactiveUpdatesRequest = value
        }
    }
}

class StreamCascadeReactiveUpdatesResponse(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#UINT64", label = WireField.Label.OMIT_IDENTITY)
    val version: Long = 0L,
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val cascade_id: String = "",
    @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val delta_text: String = "",
    @field:WireField(tag = 4, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val delta_thought: String = "",
    @field:WireField(tag = 5, adapter = "com.squareup.wire.ProtoAdapter#BOOL", label = WireField.Label.OMIT_IDENTITY)
    val is_complete: Boolean = false,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<StreamCascadeReactiveUpdatesResponse, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<StreamCascadeReactiveUpdatesResponse> = object : ProtoAdapter<StreamCascadeReactiveUpdatesResponse>(
            FieldEncoding.LENGTH_DELIMITED,
            StreamCascadeReactiveUpdatesResponse::class,
            "type.googleapis.com/exa.language_server_pb.StreamCascadeReactiveUpdatesResponse",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: StreamCascadeReactiveUpdatesResponse): Int {
                var size = value.unknownFields.size
                if (value.version != 0L) size += ProtoAdapter.UINT64.encodedSizeWithTag(1, value.version)
                if (value.cascade_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(2, value.cascade_id)
                if (value.delta_text != "") size += ProtoAdapter.STRING.encodedSizeWithTag(3, value.delta_text)
                if (value.delta_thought != "") size += ProtoAdapter.STRING.encodedSizeWithTag(4, value.delta_thought)
                if (value.is_complete) size += ProtoAdapter.BOOL.encodedSizeWithTag(5, value.is_complete)
                return size
            }

            override fun encode(writer: ProtoWriter, value: StreamCascadeReactiveUpdatesResponse) {
                if (value.version != 0L) ProtoAdapter.UINT64.encodeWithTag(writer, 1, value.version)
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.cascade_id)
                if (value.delta_text != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.delta_text)
                if (value.delta_thought != "") ProtoAdapter.STRING.encodeWithTag(writer, 4, value.delta_thought)
                if (value.is_complete) ProtoAdapter.BOOL.encodeWithTag(writer, 5, value.is_complete)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: StreamCascadeReactiveUpdatesResponse) {
                writer.writeBytes(value.unknownFields)
                if (value.is_complete) ProtoAdapter.BOOL.encodeWithTag(writer, 5, value.is_complete)
                if (value.delta_thought != "") ProtoAdapter.STRING.encodeWithTag(writer, 4, value.delta_thought)
                if (value.delta_text != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.delta_text)
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.cascade_id)
                if (value.version != 0L) ProtoAdapter.UINT64.encodeWithTag(writer, 1, value.version)
            }

            override fun decode(reader: ProtoReader): StreamCascadeReactiveUpdatesResponse {
                var version = 0L
                var cascade_id = ""
                var delta_text = ""
                var delta_thought = ""
                var is_complete = false
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> version = ProtoAdapter.UINT64.decode(reader)
                        2 -> cascade_id = ProtoAdapter.STRING.decode(reader)
                        3 -> delta_text = ProtoAdapter.STRING.decode(reader)
                        4 -> delta_thought = ProtoAdapter.STRING.decode(reader)
                        5 -> is_complete = ProtoAdapter.BOOL.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return StreamCascadeReactiveUpdatesResponse(version, cascade_id, delta_text, delta_thought, is_complete, unknownFields)
            }

            override fun redact(value: StreamCascadeReactiveUpdatesResponse): StreamCascadeReactiveUpdatesResponse = value
        }
    }
}

class CancelCascadeInvocationRequest(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val cascade_id: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<CancelCascadeInvocationRequest, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<CancelCascadeInvocationRequest> = object : ProtoAdapter<CancelCascadeInvocationRequest>(
            FieldEncoding.LENGTH_DELIMITED,
            CancelCascadeInvocationRequest::class,
            "type.googleapis.com/exa.language_server_pb.CancelCascadeInvocationRequest",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: CancelCascadeInvocationRequest): Int =
                value.unknownFields.size + if (value.cascade_id != "") ProtoAdapter.STRING.encodedSizeWithTag(1, value.cascade_id) else 0

            override fun encode(writer: ProtoWriter, value: CancelCascadeInvocationRequest) {
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: CancelCascadeInvocationRequest) {
                writer.writeBytes(value.unknownFields)
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
            }

            override fun decode(reader: ProtoReader): CancelCascadeInvocationRequest {
                var cascade_id = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> cascade_id = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return CancelCascadeInvocationRequest(cascade_id, unknownFields)
            }

            override fun redact(value: CancelCascadeInvocationRequest): CancelCascadeInvocationRequest = value
        }
    }
}

class CancelCascadeInvocationResponse(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#BOOL", label = WireField.Label.OMIT_IDENTITY)
    val cancelled: Boolean = false,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<CancelCascadeInvocationResponse, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<CancelCascadeInvocationResponse> = object : ProtoAdapter<CancelCascadeInvocationResponse>(
            FieldEncoding.LENGTH_DELIMITED,
            CancelCascadeInvocationResponse::class,
            "type.googleapis.com/exa.language_server_pb.CancelCascadeInvocationResponse",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: CancelCascadeInvocationResponse): Int =
                value.unknownFields.size + if (value.cancelled) ProtoAdapter.BOOL.encodedSizeWithTag(1, value.cancelled) else 0

            override fun encode(writer: ProtoWriter, value: CancelCascadeInvocationResponse) {
                if (value.cancelled) ProtoAdapter.BOOL.encodeWithTag(writer, 1, value.cancelled)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: CancelCascadeInvocationResponse) {
                writer.writeBytes(value.unknownFields)
                if (value.cancelled) ProtoAdapter.BOOL.encodeWithTag(writer, 1, value.cancelled)
            }

            override fun decode(reader: ProtoReader): CancelCascadeInvocationResponse {
                var cancelled = false
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> cancelled = ProtoAdapter.BOOL.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return CancelCascadeInvocationResponse(cancelled, unknownFields)
            }

            override fun redact(value: CancelCascadeInvocationResponse): CancelCascadeInvocationResponse = value
        }
    }
}

class HandleCascadeUserInteractionRequest(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val cascade_id: String = "",
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val interaction_id: String = "",
    @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#BOOL", label = WireField.Label.OMIT_IDENTITY)
    val approved: Boolean = false,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<HandleCascadeUserInteractionRequest, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<HandleCascadeUserInteractionRequest> = object : ProtoAdapter<HandleCascadeUserInteractionRequest>(
            FieldEncoding.LENGTH_DELIMITED,
            HandleCascadeUserInteractionRequest::class,
            "type.googleapis.com/exa.language_server_pb.HandleCascadeUserInteractionRequest",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: HandleCascadeUserInteractionRequest): Int {
                var size = value.unknownFields.size
                if (value.cascade_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(1, value.cascade_id)
                if (value.interaction_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(2, value.interaction_id)
                if (value.approved) size += ProtoAdapter.BOOL.encodedSizeWithTag(3, value.approved)
                return size
            }

            override fun encode(writer: ProtoWriter, value: HandleCascadeUserInteractionRequest) {
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
                if (value.interaction_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.interaction_id)
                if (value.approved) ProtoAdapter.BOOL.encodeWithTag(writer, 3, value.approved)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: HandleCascadeUserInteractionRequest) {
                writer.writeBytes(value.unknownFields)
                if (value.approved) ProtoAdapter.BOOL.encodeWithTag(writer, 3, value.approved)
                if (value.interaction_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.interaction_id)
                if (value.cascade_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.cascade_id)
            }

            override fun decode(reader: ProtoReader): HandleCascadeUserInteractionRequest {
                var cascade_id = ""
                var interaction_id = ""
                var approved = false
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> cascade_id = ProtoAdapter.STRING.decode(reader)
                        2 -> interaction_id = ProtoAdapter.STRING.decode(reader)
                        3 -> approved = ProtoAdapter.BOOL.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return HandleCascadeUserInteractionRequest(cascade_id, interaction_id, approved, unknownFields)
            }

            override fun redact(value: HandleCascadeUserInteractionRequest): HandleCascadeUserInteractionRequest = value
        }
    }
}

class HandleCascadeUserInteractionResponse(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#BOOL", label = WireField.Label.OMIT_IDENTITY)
    val handled: Boolean = false,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<HandleCascadeUserInteractionResponse, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<HandleCascadeUserInteractionResponse> = object : ProtoAdapter<HandleCascadeUserInteractionResponse>(
            FieldEncoding.LENGTH_DELIMITED,
            HandleCascadeUserInteractionResponse::class,
            "type.googleapis.com/exa.language_server_pb.HandleCascadeUserInteractionResponse",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: HandleCascadeUserInteractionResponse): Int =
                value.unknownFields.size + if (value.handled) ProtoAdapter.BOOL.encodedSizeWithTag(1, value.handled) else 0

            override fun encode(writer: ProtoWriter, value: HandleCascadeUserInteractionResponse) {
                if (value.handled) ProtoAdapter.BOOL.encodeWithTag(writer, 1, value.handled)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: HandleCascadeUserInteractionResponse) {
                writer.writeBytes(value.unknownFields)
                if (value.handled) ProtoAdapter.BOOL.encodeWithTag(writer, 1, value.handled)
            }

            override fun decode(reader: ProtoReader): HandleCascadeUserInteractionResponse {
                var handled = false
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> handled = ProtoAdapter.BOOL.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return HandleCascadeUserInteractionResponse(handled, unknownFields)
            }

            override fun redact(value: HandleCascadeUserInteractionResponse): HandleCascadeUserInteractionResponse = value
        }
    }
}

class GetUserStatusRequest(
    @field:WireField(tag = 1, adapter = "exa.language_server_pb.Metadata#ADAPTER", label = WireField.Label.OMIT_IDENTITY)
    val metadata: Metadata? = null,
    unknownFields: ByteString = ByteString.EMPTY
) : Message<GetUserStatusRequest, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<GetUserStatusRequest> = object : ProtoAdapter<GetUserStatusRequest>(
            FieldEncoding.LENGTH_DELIMITED,
            GetUserStatusRequest::class,
            "type.googleapis.com/exa.language_server_pb.GetUserStatusRequest",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: GetUserStatusRequest): Int =
                value.unknownFields.size + if (value.metadata != null) Metadata.ADAPTER.encodedSizeWithTag(1, value.metadata) else 0

            override fun encode(writer: ProtoWriter, value: GetUserStatusRequest) {
                if (value.metadata != null) Metadata.ADAPTER.encodeWithTag(writer, 1, value.metadata)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: GetUserStatusRequest) {
                writer.writeBytes(value.unknownFields)
                if (value.metadata != null) Metadata.ADAPTER.encodeWithTag(writer, 1, value.metadata)
            }

            override fun decode(reader: ProtoReader): GetUserStatusRequest {
                var metadata: Metadata? = null
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> metadata = Metadata.ADAPTER.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return GetUserStatusRequest(metadata, unknownFields)
            }

            override fun redact(value: GetUserStatusRequest): GetUserStatusRequest = value
        }
    }
}

class GetUserStatusResponse(
    @field:WireField(tag = 1, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val email: String = "",
    @field:WireField(tag = 2, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val tier: String = "",
    @field:WireField(tag = 3, adapter = "com.squareup.wire.ProtoAdapter#STRING", label = WireField.Label.OMIT_IDENTITY)
    val project_id: String = "",
    unknownFields: ByteString = ByteString.EMPTY
) : Message<GetUserStatusResponse, Nothing>(ADAPTER, unknownFields) {
    override fun newBuilder(): Nothing = throw AssertionError()

    companion object {
        @JvmField
        val ADAPTER: ProtoAdapter<GetUserStatusResponse> = object : ProtoAdapter<GetUserStatusResponse>(
            FieldEncoding.LENGTH_DELIMITED,
            GetUserStatusResponse::class,
            "type.googleapis.com/exa.language_server_pb.GetUserStatusResponse",
            PROTO_3,
            null,
            "LanguageServerService.proto"
        ) {
            override fun encodedSize(value: GetUserStatusResponse): Int {
                var size = value.unknownFields.size
                if (value.email != "") size += ProtoAdapter.STRING.encodedSizeWithTag(1, value.email)
                if (value.tier != "") size += ProtoAdapter.STRING.encodedSizeWithTag(2, value.tier)
                if (value.project_id != "") size += ProtoAdapter.STRING.encodedSizeWithTag(3, value.project_id)
                return size
            }

            override fun encode(writer: ProtoWriter, value: GetUserStatusResponse) {
                if (value.email != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.email)
                if (value.tier != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.tier)
                if (value.project_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.project_id)
                writer.writeBytes(value.unknownFields)
            }

            override fun encode(writer: ReverseProtoWriter, value: GetUserStatusResponse) {
                writer.writeBytes(value.unknownFields)
                if (value.project_id != "") ProtoAdapter.STRING.encodeWithTag(writer, 3, value.project_id)
                if (value.tier != "") ProtoAdapter.STRING.encodeWithTag(writer, 2, value.tier)
                if (value.email != "") ProtoAdapter.STRING.encodeWithTag(writer, 1, value.email)
            }

            override fun decode(reader: ProtoReader): GetUserStatusResponse {
                var email = ""
                var tier = ""
                var project_id = ""
                val unknownFields = reader.forEachTag { tag ->
                    when (tag) {
                        1 -> email = ProtoAdapter.STRING.decode(reader)
                        2 -> tier = ProtoAdapter.STRING.decode(reader)
                        3 -> project_id = ProtoAdapter.STRING.decode(reader)
                        else -> reader.readUnknownField(tag)
                    }
                }
                return GetUserStatusResponse(email, tier, project_id, unknownFields)
            }

            override fun redact(value: GetUserStatusResponse): GetUserStatusResponse = value
        }
    }
}
