package com.openminis.app.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.openminis.app.R
import com.openminis.app.channel.ChannelPrefs
import com.openminis.app.channel.ChannelService
import com.openminis.app.ui.components.MinisOutlinedButton
import com.openminis.app.ui.components.MinisTextButton

@Composable
fun ChannelSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { ChannelPrefs(context) }

    var enabled by remember { mutableStateOf(prefs.isTelegramEnabled()) }
    var token by remember { mutableStateOf(prefs.botToken()) }
    var showToken by remember { mutableStateOf(false) }
    var allowlist by remember { mutableStateOf(prefs.allowedUserIdsRaw()) }
    var pairing by remember { mutableStateOf(prefs.pairingCode()) }
    var tokenError by remember { mutableStateOf<String?>(null) }
    val lastError = prefs.lastError()

    SettingsScaffold(title = stringResource(R.string.channel_title), onBack = onBack) {
        SettingsSection(
            header = stringResource(R.string.channel_section_telegram),
            footer = stringResource(R.string.channel_section_footer),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.channel_telegram_enable),
                subtitle = stringResource(R.string.channel_telegram_enable_subtitle),
                icon = Icons.Outlined.Forum,
                iconColor = MaterialTheme.colorScheme.primary,
                checked = enabled,
                onCheckedChange = { on ->
                    if (on && !ChannelPrefs.isValidBotToken(token)) {
                        tokenError = context.getString(R.string.channel_token_invalid)
                        return@SettingsSwitchRow
                    }
                    tokenError = null
                    enabled = on
                    if (on) prefs.setBotToken(token)
                    prefs.setTelegramEnabled(on)
                    ChannelService.startIfEnabled(context)
                },
                showDivider = true,
            )
            Text(
                text = stringResource(R.string.channel_bot_token),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = token,
                onValueChange = {
                    token = it
                    tokenError = null
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                singleLine = true,
                visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                placeholder = { Text(stringResource(R.string.channel_bot_token_placeholder)) },
                isError = tokenError != null,
                supportingText = tokenError?.let { { Text(it) } },
            )
            MinisTextButton(
                onClick = { showToken = !showToken },
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Text(
                    if (showToken) stringResource(R.string.channel_hide_token)
                    else stringResource(R.string.channel_show_token),
                )
            }
            MinisOutlinedButton(
                onClick = {
                    if (!ChannelPrefs.isValidBotToken(token)) {
                        tokenError = context.getString(R.string.channel_token_invalid)
                        return@MinisOutlinedButton
                    }
                    prefs.setBotToken(token)
                    tokenError = null
                    ChannelService.restart(context)
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.channel_save_token))
            }
        }

        SettingsSection(
            header = stringResource(R.string.channel_section_access),
            footer = stringResource(R.string.channel_access_footer),
        ) {
            Text(
                text = stringResource(R.string.channel_pairing_code),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
            )
            Text(
                text = pairing,
                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            MinisTextButton(
                onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("pairing", pairing))
                },
                modifier = Modifier.padding(horizontal = 6.dp),
            ) {
                Text(stringResource(R.string.channel_copy_pairing))
            }
            MinisOutlinedButton(
                onClick = {
                    pairing = prefs.rotatePairingCode()
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            ) {
                Text(stringResource(R.string.channel_rotate_pairing))
            }
            Text(
                text = stringResource(R.string.channel_allowlist),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
            )
            OutlinedTextField(
                value = allowlist,
                onValueChange = { allowlist = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                placeholder = { Text(stringResource(R.string.channel_allowlist_placeholder)) },
            )
            MinisOutlinedButton(
                onClick = {
                    prefs.setAllowedUserIdsRaw(allowlist)
                    allowlist = prefs.allowedUserIdsRaw()
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.channel_save_allowlist))
            }
        }

        var localHttp by remember { mutableStateOf(prefs.isLocalHttpEnabled()) }
        var lanBind by remember { mutableStateOf(prefs.isLanBind()) }
        var desktopUrl by remember { mutableStateOf(prefs.desktopBaseUrl()) }
        var desktopToken by remember { mutableStateOf(prefs.desktopToken()) }
        var titleModel by remember {
            mutableStateOf(
                com.openminis.app.agent.AgentRoleModels.getEntryId(
                    context,
                    com.openminis.app.agent.AgentRoleModels.ROLE_TITLE,
                ),
            )
        }
        SettingsSection(
            header = stringResource(R.string.channel_section_local_http),
        ) {
            SettingsSwitchRow(
                title = stringResource(R.string.channel_local_http_enable),
                subtitle = stringResource(R.string.channel_local_http_subtitle),
                checked = localHttp,
                onCheckedChange = { on ->
                    localHttp = on
                    prefs.setLocalHttpEnabled(on)
                    ChannelService.restart(context)
                },
                showDivider = true,
            )
            SettingsSwitchRow(
                title = stringResource(R.string.channel_local_http_lan),
                subtitle = stringResource(R.string.channel_local_http_lan_subtitle),
                checked = lanBind,
                onCheckedChange = { on ->
                    lanBind = on
                    prefs.setLanBind(on)
                    if (localHttp) ChannelService.restart(context)
                },
                showDivider = true,
            )
            Text(
                text = stringResource(R.string.channel_desktop_url),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = desktopUrl,
                onValueChange = { desktopUrl = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                placeholder = { Text(stringResource(R.string.channel_desktop_url_placeholder)) },
                singleLine = true,
            )
            Text(
                text = stringResource(R.string.channel_desktop_token),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            )
            OutlinedTextField(
                value = desktopToken,
                onValueChange = { desktopToken = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
            )
            MinisOutlinedButton(
                onClick = {
                    prefs.setDesktopBaseUrl(desktopUrl)
                    prefs.setDesktopToken(desktopToken)
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.channel_save_desktop))
            }
            Text(
                text = stringResource(R.string.channel_title_model),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp),
            )
            OutlinedTextField(
                value = titleModel,
                onValueChange = { titleModel = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                placeholder = { Text(stringResource(R.string.channel_title_model_placeholder)) },
                singleLine = true,
            )
            MinisOutlinedButton(
                onClick = {
                    com.openminis.app.agent.AgentRoleModels.setEntryId(
                        context,
                        com.openminis.app.agent.AgentRoleModels.ROLE_TITLE,
                        titleModel,
                    )
                },
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(stringResource(R.string.channel_save_title_model))
            }
        }

        if (lastError.isNotBlank()) {
            SettingsSection(header = stringResource(R.string.channel_section_status)) {
                Text(
                    text = lastError,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(14.dp),
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
