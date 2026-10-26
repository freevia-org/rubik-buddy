package org.freevia.rubikbuddy.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import org.freevia.rubikbuddy.platformAppVersion
import org.freevia.rubikbuddy.platformBuildNumber
import org.freevia.rubikbuddy.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun PrivacyDialog(onDismiss: () -> Unit) {
    val uriHandler = LocalUriHandler.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.privacy_title)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text(stringResource(Res.string.privacy_body))
                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(12.dp))
                Text(stringResource(Res.string.about_section_title), style = MaterialTheme.typography.titleSmall)
                Text(
                    stringResource(Res.string.about_version, platformAppVersion, platformBuildNumber),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = { uriHandler.openUri("https://github.com/freevia-org/rubik-buddy/blob/main/docs/privacy-policy.md") }) {
                    Text(stringResource(Res.string.privacy_policy_link))
                }
                TextButton(onClick = { uriHandler.openUri("https://github.com/freevia-org/rubik-buddy/issues") }) {
                    Text(stringResource(Res.string.support_link))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.privacy_close)) }
        }
    )
}
