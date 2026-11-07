package dev.proxboss.aoe.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.proxboss.aoe.model.SessionStatus

@Composable
fun statusColor(status: SessionStatus): Color = when (status) {
    SessionStatus.RUNNING -> MaterialTheme.colorScheme.primary
    SessionStatus.WAITING -> Color(0xFFF0A43A)
    SessionStatus.ERROR -> MaterialTheme.colorScheme.error
    SessionStatus.IDLE -> Color(0xFF5FBF83)
    SessionStatus.STARTING, SessionStatus.CREATING -> Color(0xFF8BB8FF)
    SessionStatus.DELETING -> MaterialTheme.colorScheme.error
    SessionStatus.STOPPED, SessionStatus.UNKNOWN -> MaterialTheme.colorScheme.outline
}

@Composable
fun StatusDot(status: SessionStatus, modifier: Modifier = Modifier, size: Int = 9) {
    Box(
        modifier = modifier
            .size(size.dp)
            .background(statusColor(status), CircleShape),
    )
}

@Composable
fun StatusPill(status: SessionStatus) {
    Row(
        modifier = Modifier
            .background(statusColor(status).copy(alpha = 0.14f), RoundedCornerShape(99.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusDot(status, size = 7)
        Text(
            text = status.labelFr,
            style = MaterialTheme.typography.labelSmall,
            color = statusColor(status),
            modifier = Modifier.padding(start = 5.dp),
        )
    }
}
