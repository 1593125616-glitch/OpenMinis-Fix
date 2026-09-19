package com.openminis.app.tools

import com.openminis.app.data.model.AgentToolDefinition
import com.openminis.app.data.model.AgentToolParam
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Phone → desktop hands. POSTs a command to a user-configured gateway
 * (Hermes / a tiny local script). Disabled until a base URL is set.
 */
object DesktopRunTool {
    const val NAME = "desktop_run"

    fun definition(): AgentToolDefinition = AgentToolDefinition(
        name = NAME,
        description = "Run a command on the paired desktop gateway (computer as hands). " +
            "Only available when Settings → Channels → Desktop gateway URL is set. " +
            "Do not send secrets. The desktop must confirm dangerous commands.",
        parameters = mapOf(
            "tool_title" to AgentToolParam("string", "5-10 word summary."),
            "command" to AgentToolParam("string", "Shell command for the desktop to run."),
            "cwd" to AgentToolParam("string", "Optional working directory on the desktop."),
        ),
        required = listOf("tool_title", "command"),
        propertyOrdering = listOf("tool_title", "command", "cwd"),
    )

    fun execute(argsJson: String, baseUrl: String, token: String): ToolExecutionResult {
        if (baseUrl.isBlank()) {
            return ToolExecutionResult(
                "Desktop gateway is not configured. Set it in Settings → Channels.",
                false,
            )
        }
        val url = (baseUrl.trimEnd('/') + "/v1/exec").toHttpUrlOrNull()
            ?: return ToolExecutionResult("Desktop gateway URL is invalid.", false)
        if (url.scheme != "http" && url.scheme != "https") {
            return ToolExecutionResult("Desktop gateway must be http or https.", false)
        }
        val args = JSONObject(argsJson)
        val command = args.optString("command").trim()
        if (command.isEmpty()) return ToolExecutionResult("Error: command is required", false)
        val cwd = args.optString("cwd")
        val payload = JSONObject()
            .put("command", command)
            .put("cwd", cwd)
            .toString()
        val client = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
            .build()
        val req = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .post(payload.toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()
        return try {
            client.newCall(req).execute().use { resp ->
                val body = resp.body?.string().orEmpty()
                if (!resp.isSuccessful) {
                    ToolExecutionResult("Desktop gateway HTTP ${resp.code}: ${body.take(500)}", false)
                } else {
                    ToolExecutionResult(body.ifBlank { "(empty desktop output)" }, true)
                }
            }
        } catch (t: Throwable) {
            ToolExecutionResult("Desktop gateway error: ${t.javaClass.simpleName}", false)
        }
    }
}
