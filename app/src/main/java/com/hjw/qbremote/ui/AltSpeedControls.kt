package com.hjw.qbremote.ui

import androidx.compose.ui.semantics.Role
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hjw.qbremote.R

// Alternative (turtle) speed-limit chip and its one-time hint.

/** One-time explanation shown under the turtle chip the first time it appears. */
@Composable
internal fun AltSpeedHintCard(onDismiss: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(accent.copy(alpha = 0.10f), RoundedCornerShape(12.dp))
            .padding(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_turtle),
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(22.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(R.string.alt_speed_hint_title),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = stringResource(R.string.alt_speed_hint_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onDismiss) {
            Text(
                text = stringResource(R.string.alt_speed_hint_dismiss),
                color = accent,
            )
        }
    }
}

internal enum class AltSpeedState { OFF, PARTIAL, ON }

/**
 * Turtle chip for the alternative speed limits. Off: a quiet icon; on / partly on:
 * an accent-tinted pill with a label, so the slowed-down state is easy to notice.
 */
@Composable
internal fun AltSpeedToggle(
    state: AltSpeedState,
    busy: Boolean,
    onClick: () -> Unit,
) {
    val active = state != AltSpeedState.OFF
    val accent = MaterialTheme.colorScheme.primary
    val contentColor = if (active) accent else MaterialTheme.colorScheme.onSurfaceVariant
    val description = stringResource(
        if (state == AltSpeedState.ON) R.string.alt_speed_toggle_desc_on else R.string.alt_speed_toggle_desc_off,
    )
    Row(
        modifier = Modifier
            .height(32.dp)
            .clip(CircleShape)
            .background(if (active) accent.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(enabled = !busy, role = Role.Switch, onClickLabel = description, onClick = onClick)
            .semantics { contentDescription = description }
            .padding(horizontal = if (active) 10.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = contentColor,
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.ic_turtle),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
        if (active) {
            Text(
                text = stringResource(
                    if (state == AltSpeedState.ON) R.string.alt_speed_toggle_on else R.string.alt_speed_toggle_partial,
                ),
                style = MaterialTheme.typography.labelMedium,
                color = contentColor,
                maxLines = 1,
            )
        }
    }
}
