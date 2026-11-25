package com.jarvisai.assistant.util

import android.content.Context
import android.content.Intent
import android.os.Environment
import androidx.core.content.FileProvider
import com.jarvisai.assistant.data.local.ChatMessageEntity
import com.jarvisai.assistant.data.local.ChatSessionEntity
import com.jarvisai.assistant.model.ChatAttachmentCodec
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ConversationExportHelper {

    enum class ExportFormat(val extension: String, val mimeType: String, val label: String) {
        MARKDOWN("md", "text/markdown", "Markdown"),
        STANDALONE_HTML("html", "text/html", "Standalone HTML")
    }

    data class ExportResult(
        val file: File,
        val displayLocation: String
    )

    private fun formatTimestamp(ms: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(ms))
    }

    private fun sanitizeFileName(raw: String, fallbackId: Long): String {
        val cleaned = raw.trim()
            .replace(Regex("[^a-zA-Z0-9._-]+"), "_")
            .trim('_')
            .take(48)
        return cleaned.ifBlank { "conversation_$fallbackId" }
    }

    private data class ParsedToolCall(
        val name: String,
        val command: String,
        val output: String,
        val status: String
    )

    private fun parseToolCalls(toolCallsJson: String?): List<ParsedToolCall> {
        if (toolCallsJson.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(toolCallsJson)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.optJSONObject(i) ?: continue
                    add(
                        ParsedToolCall(
                            name = obj.optString("name", "tool"),
                            command = obj.optString("command", ""),
                            output = obj.optString("output", ""),
                            status = obj.optString("status", "SUCCESS")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun buildMarkdown(
        session: ChatSessionEntity,
        messages: List<ChatMessageEntity>,
        conversationId: String
    ): String = buildString {
        val title = session.title.ifBlank { "Conversation #${session.id}" }
        appendLine("# $title")
        appendLine()
        appendLine("- **Conversation ID:** `$conversationId`")
        appendLine("- **Folder:** ${session.folder.ifBlank { "General" }}")
        appendLine("- **Created:** ${formatTimestamp(session.createdAt)}")
        appendLine("- **Updated:** ${formatTimestamp(session.updatedAt)}")
        appendLine("- **Exported:** ${formatTimestamp(System.currentTimeMillis())}")
        appendLine("- **Messages:** ${messages.size}")
        appendLine()
        appendLine("---")
        appendLine()

        if (messages.isEmpty()) {
            appendLine("_No messages in this conversation._")
            return@buildString
        }

        messages.forEachIndexed { index, msg ->
            val isUser = msg.role.equals("user", ignoreCase = true)
            val speaker = if (isUser) "User" else "Agentra"
            val modelTag = if (!isUser && msg.modelUsed.isNotBlank()) " · `${msg.modelUsed}`" else ""
            appendLine("## ${index + 1}. $speaker$modelTag")
            appendLine("_${formatTimestamp(msg.timestamp)}_")
            appendLine()

            if (!msg.thoughtText.isNullOrBlank()) {
                val durText = msg.thoughtDurationMs?.takeIf { it > 0 }?.let {
                    String.format(Locale.US, " (%.1fs)", it / 1000.0)
                }.orEmpty()
                appendLine("<details>")
                appendLine("<summary>Thinking Process$durText</summary>")
                appendLine()
                appendLine(msg.thoughtText.trim())
                appendLine()
                appendLine("</details>")
                appendLine()
            }

            val tools = parseToolCalls(msg.toolCallsJson)
            if (tools.isNotEmpty()) {
                appendLine("<details>")
                appendLine("<summary>Tool Calls (${tools.size})</summary>")
                appendLine()
                tools.forEach { tc ->
                    appendLine("- **${tc.name}** (`${tc.status}`): `${tc.command}`")
                    if (tc.output.isNotBlank()) {
                        appendLine("```")
                        appendLine(tc.output.take(4000))
                        appendLine("```")
                    }
                }
                appendLine("</details>")
                appendLine()
            }

            val readable = ChatAttachmentCodec.toPromptReadableText(msg.content).trim()
            appendLine(readable.ifBlank { "_(Empty message)_" })
            appendLine()
            appendLine("---")
            appendLine()
        }
    }

    fun buildShareText(
        session: ChatSessionEntity,
        messages: List<ChatMessageEntity>,
        conversationId: String
    ): String = buildString {
        val title = session.title.ifBlank { "Conversation #${session.id}" }
        appendLine(title)
        appendLine("ID: $conversationId | Folder: ${session.folder.ifBlank { "General" }}")
        appendLine("=".repeat(36))
        appendLine()
        messages.forEach { msg ->
            val label = if (msg.role.equals("user", ignoreCase = true)) "User" else "Agentra"
            val readable = ChatAttachmentCodec.toPromptReadableText(msg.content).trim()
            appendLine("[$label · ${formatTimestamp(msg.timestamp)}]")
            appendLine(readable.ifBlank { "(Empty message)" })
            appendLine()
        }
    }.trim()

    private fun escapeHtml(raw: String): String = buildString(raw.length + 16) {
        for (ch in raw) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\'' -> append("&#39;")
                else -> append(ch)
            }
        }
    }

    private fun escapeScriptJson(raw: String): String =
        raw.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\r", "")
            .replace("\n", "\\n")
            .replace("</", "<\\/")

    fun buildStandaloneHtml(
        session: ChatSessionEntity,
        messages: List<ChatMessageEntity>,
        conversationId: String
    ): String {
        val title = session.title.ifBlank { "Conversation #${session.id}" }
        val safeTitle = escapeHtml(title)
        val safeFolder = escapeHtml(session.folder.ifBlank { "General" })
        val safeConvId = escapeHtml(conversationId)
        val exportedAt = escapeHtml(formatTimestamp(System.currentTimeMillis()))

        val messagesJson = buildString {
            append("[")
            messages.forEachIndexed { idx, msg ->
                if (idx > 0) append(",")
                val readable = ChatAttachmentCodec.toPromptReadableText(msg.content)
                val tools = parseToolCalls(msg.toolCallsJson)
                append("{")
                append("\"role\":\"${escapeScriptJson(msg.role)}\",")
                append("\"model\":\"${escapeScriptJson(msg.modelUsed)}\",")
                append("\"time\":\"${escapeScriptJson(formatTimestamp(msg.timestamp))}\",")
                append("\"content\":\"${escapeScriptJson(readable)}\",")
                append("\"thought\":\"${escapeScriptJson(msg.thoughtText.orEmpty())}\",")
                append("\"thoughtMs\":${msg.thoughtDurationMs ?: 0L},")
                append("\"isError\":${msg.isError},")
                append("\"tools\":[")
                tools.forEachIndexed { tIdx, tc ->
                    if (tIdx > 0) append(",")
                    append("{")
                    append("\"name\":\"${escapeScriptJson(tc.name)}\",")
                    append("\"command\":\"${escapeScriptJson(tc.command)}\",")
                    append("\"output\":\"${escapeScriptJson(tc.output.take(8000))}\",")
                    append("\"status\":\"${escapeScriptJson(tc.status)}\"")
                    append("}")
                }
                append("]}")
            }
            append("]")
        }

        return """<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
<meta charset="UTF-8" />
<meta name="viewport" content="width=device-width, initial-scale=1.0" />
<title>$safeTitle — Agentra Export</title>
<style>
:root {
  --bg: #0b101b;
  --surface: #131a29;
  --surface-alt: #1a2336;
  --border: #25324a;
  --text: #e6edf7;
  --muted: #8ea0bc;
  --accent: #38bdf8;
  --accent-soft: rgba(56, 189, 248, 0.14);
  --user-bg: #17253e;
  --user-border: #2a4570;
  --code-bg: #090d16;
  --success: #34d399;
  --danger: #f87171;
}
html[data-theme="light"] {
  --bg: #f4f7fb;
  --surface: #ffffff;
  --surface-alt: #f0f4fa;
  --border: #d8e1ee;
  --text: #0f172a;
  --muted: #52627a;
  --accent: #0284c7;
  --accent-soft: rgba(2, 132, 199, 0.10);
  --user-bg: #e8f3ff;
  --user-border: #b6d8fc;
  --code-bg: #0f172a;
  --success: #059669;
  --danger: #dc2626;
}
* { box-sizing: border-box; }
body {
  margin: 0;
  background: var(--bg);
  color: var(--text);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Inter, sans-serif;
  line-height: 1.62;
  padding: 20px 14px 64px;
}
.container { max-width: 880px; margin: 0 auto; }
header {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 16px;
  padding: 18px 20px;
  margin-bottom: 20px;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}
.brand { font-size: 12px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: var(--accent); }
h1 { margin: 4px 0 6px; font-size: 20px; line-height: 1.3; }
.meta { font-size: 12px; color: var(--muted); display: flex; flex-wrap: wrap; gap: 10px; }
.pill {
  display: inline-flex; align-items: center; gap: 5px;
  padding: 3px 9px; border-radius: 999px;
  background: var(--surface-alt); border: 1px solid var(--border);
  font-size: 11.5px; color: var(--muted);
}
.theme-btn {
  background: var(--surface-alt); color: var(--text);
  border: 1px solid var(--border); border-radius: 10px;
  padding: 8px 13px; font-size: 12.5px; font-weight: 600; cursor: pointer;
}
.msg {
  background: var(--surface);
  border: 1px solid var(--border);
  border-radius: 14px;
  padding: 16px 18px;
  margin-bottom: 14px;
}
.msg.user { background: var(--user-bg); border-color: var(--user-border); }
.msg.error { border-color: var(--danger); }
.msg-head {
  display: flex; align-items: center; justify-content: space-between;
  gap: 8px; margin-bottom: 10px; font-size: 12px; color: var(--muted);
}
.role { font-weight: 700; color: var(--text); font-size: 13px; }
.role.agent { color: var(--accent); }
.body { font-size: 14.5px; word-break: break-word; }
.body p { margin: 0 0 10px; }
.body p:last-child { margin-bottom: 0; }
.body h1, .body h2, .body h3 { margin: 14px 0 8px; line-height: 1.3; }
.body ul, .body ol { padding-left: 22px; margin: 6px 0 10px; }
.body a { color: var(--accent); }
.body code {
  background: var(--surface-alt);
  border: 1px solid var(--border);
  border-radius: 5px;
  padding: 1px 5px;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
  font-size: 0.88em;
}
.code-wrap {
  margin: 10px 0;
  border-radius: 10px;
  overflow: hidden;
  border: 1px solid var(--border);
  background: var(--code-bg);
}
.code-bar {
  display: flex; justify-content: space-between; align-items: center;
  padding: 6px 12px; background: rgba(255,255,255,0.05);
  color: #94a3b8; font-size: 11.5px; font-family: ui-monospace, monospace;
}
.copy-btn {
  background: rgba(56,189,248,0.16); color: #7dd3fc;
  border: 1px solid rgba(56,189,248,0.35); border-radius: 6px;
  padding: 2px 8px; font-size: 11px; cursor: pointer;
}
pre {
  margin: 0; padding: 12px 14px; overflow-x: auto;
  color: #e2e8f0; font-size: 12.8px; line-height: 1.5;
  font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
}
pre code { background: none; border: none; padding: 0; color: inherit; }
details {
  background: var(--surface-alt);
  border: 1px solid var(--border);
  border-radius: 10px;
  padding: 8px 12px;
  margin-bottom: 10px;
}
summary { cursor: pointer; font-weight: 600; font-size: 12.5px; color: var(--muted); }
.thought-body {
  margin-top: 8px; font-size: 12.8px; color: var(--muted);
  white-space: pre-wrap; border-top: 1px solid var(--border); padding-top: 8px;
}
.tool-item {
  margin-top: 8px; padding-top: 8px; border-top: 1px solid var(--border);
  font-size: 12.5px;
}
.tool-status { font-weight: 700; color: var(--success); }
.tool-status.FAILED { color: var(--danger); }
footer { text-align: center; margin-top: 28px; font-size: 12px; color: var(--muted); }
@media print { .theme-btn, .copy-btn { display: none; } }
</style>
</head>
<body>
<div class="container">
  <header>
    <div>
      <div class="brand">Agentra Standalone Transcript</div>
      <h1>$safeTitle</h1>
      <div class="meta">
        <span class="pill">Folder: $safeFolder</span>
        <span class="pill">ID: $safeConvId</span>
        <span class="pill">Messages: ${messages.size}</span>
        <span class="pill">Exported: $exportedAt</span>
      </div>
    </div>
    <button class="theme-btn" id="themeToggle" type="button">Light / Dark</button>
  </header>
  <main id="transcript"></main>
  <footer>Exported from Agentra</footer>
</div>
<script>
const MESSAGES = $messagesJson;
function esc(s) {
  return String(s).replace(/&/g,"&amp;").replace(/</g,"&lt;").replace(/>/g,"&gt;").replace(/"/g,"&quot;");
}
function renderMarkdown(md) {
  const blocks = [];
  let text = String(md || "").replace(/```([a-zA-Z0-9_+-]*)\n([\s\S]*?)```/g, (_, lang, code) => {
    const idx = blocks.length;
    blocks.push('<div class="code-wrap"><div class="code-bar"><span>' + esc(lang || 'code') +
      '</span><button class="copy-btn" type="button" onclick="copyCode(this)">Copy</button></div><pre><code>' +
      esc(code.replace(/\n$/, "")) + '</code></pre></div>');
    return '@@CODE_BLOCK_' + idx + '@@';
  });
  text = esc(text);
  text = text.replace(/^### (.+)$/gm, '<h3>$1</h3>');
  text = text.replace(/^## (.+)$/gm, '<h2>$1</h2>');
  text = text.replace(/^# (.+)$/gm, '<h1>$1</h1>');
  text = text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
  text = text.replace(/`([^`]+)`/g, '<code>$1</code>');
  text = text.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');
  text = text.split(/\n{2,}/).map(p => {
    const t = p.trim();
    if (!t) return '';
    if (t.startsWith('<h') || t.startsWith('@@CODE_BLOCK_')) return t;
    return '<p>' + t.replace(/\n/g, '<br/>') + '</p>';
  }).join('');
  blocks.forEach((html, idx) => { text = text.replace('@@CODE_BLOCK_' + idx + '@@', html); });
  return text;
}
function copyCode(btn) {
  const code = btn.closest('.code-wrap').querySelector('code').innerText;
  navigator.clipboard?.writeText(code).then(() => {
    btn.innerText = 'Copied';
    setTimeout(() => btn.innerText = 'Copy', 1400);
  });
}
const root = document.getElementById('transcript');
if (!MESSAGES.length) {
  root.innerHTML = '<div class="msg"><div class="body">No messages in this conversation.</div></div>';
} else {
  MESSAGES.forEach(m => {
    const isUser = String(m.role).toLowerCase() === 'user';
    const card = document.createElement('section');
    card.className = 'msg ' + (isUser ? 'user' : 'agent') + (m.isError ? ' error' : '');
    let inner = '<div class="msg-head"><span class="role ' + (isUser ? '' : 'agent') + '">' +
      (isUser ? 'User' : ('Agentra' + (m.model ? ' · ' + esc(m.model) : ''))) +
      '</span><span>' + esc(m.time) + '</span></div>';
    if (m.thought) {
      const secs = m.thoughtMs > 0 ? ' (' + (m.thoughtMs / 1000).toFixed(1) + 's)' : '';
      inner += '<details><summary>Thinking Process' + secs + '</summary><div class="thought-body">' +
        esc(m.thought) + '</div></details>';
    }
    if (m.tools && m.tools.length) {
      inner += '<details><summary>Tool Calls (' + m.tools.length + ')</summary>';
      m.tools.forEach(t => {
        inner += '<div class="tool-item"><strong>' + esc(t.name) + '</strong> <span class="tool-status ' +
          esc(t.status) + '">[' + esc(t.status) + ']</span> <code>' + esc(t.command) + '</code>' +
          (t.output ? '<pre><code>' + esc(t.output) + '</code></pre>' : '') + '</div>';
      });
      inner += '</details>';
    }
    inner += '<div class="body">' + renderMarkdown(m.content) + '</div>';
    card.innerHTML = inner;
    root.appendChild(card);
  });
}
document.getElementById('themeToggle').addEventListener('click', () => {
  const html = document.documentElement;
  html.dataset.theme = html.dataset.theme === 'dark' ? 'light' : 'dark';
});
</script>
</body>
</html>"""
    }

    fun exportToFileAndShare(
        context: Context,
        session: ChatSessionEntity,
        messages: List<ChatMessageEntity>,
        conversationId: String,
        format: ExportFormat
    ): ExportResult {
        val content = when (format) {
            ExportFormat.MARKDOWN -> buildMarkdown(session, messages, conversationId)
            ExportFormat.STANDALONE_HTML -> buildStandaloneHtml(session, messages, conversationId)
        }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val baseName = sanitizeFileName(session.title, session.id)
        val fileName = "${baseName}_$stamp.${format.extension}"

        val targetFile = resolveWritableExportFile(context, fileName)
        targetFile.writeText(content, Charsets.UTF_8)

        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, session.title.ifBlank { "Agentra Conversation" })
            putExtra(Intent.EXTRA_TITLE, fileName)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(shareIntent, "Export ${format.label}").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }

        val displayLocation = if (targetFile.absolutePath.contains("/Download/", ignoreCase = true)) {
            "Download/Agentra/Exports/$fileName"
        } else {
            "Exports/$fileName"
        }
        return ExportResult(file = targetFile, displayLocation = displayLocation)
    }

    fun shareConversation(
        context: Context,
        session: ChatSessionEntity,
        messages: List<ChatMessageEntity>,
        conversationId: String
    ) {
        val transcript = buildShareText(session, messages, conversationId)
        if (transcript.length > 85_000) {
            // Avoid Android Binder TransactionTooLargeException on very long chats by sharing as .md file
            exportToFileAndShare(context, session, messages, conversationId, ExportFormat.MARKDOWN)
            return
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, session.title.ifBlank { "Agentra Conversation" })
            putExtra(Intent.EXTRA_TEXT, transcript)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Conversation").apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(chooser) }
    }

    private fun resolveWritableExportFile(context: Context, fileName: String): File {
        val candidates = listOfNotNull(
            runCatching {
                File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                    "Agentra/Exports"
                )
            }.getOrNull(),
            context.getExternalFilesDir("exports"),
            File(context.cacheDir, "exports")
        )
        for (dir in candidates) {
            val ok = runCatching {
                if (!dir.exists()) dir.mkdirs()
                val probe = File(dir, fileName)
                probe.writeText("", Charsets.UTF_8)
                probe.exists() && probe.canWrite()
            }.getOrDefault(false)
            if (ok) return File(dir, fileName)
        }
        val fallbackDir = File(context.cacheDir, "exports").apply { mkdirs() }
        return File(fallbackDir, fileName)
    }
}
