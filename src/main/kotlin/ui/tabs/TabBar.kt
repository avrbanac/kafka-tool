package ui.tabs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import model.TabType
import model.displayName

@Composable
fun TabBar(
    tabs: List<TabType>,
    activeTabId: String?,
    onTabSelected: (String) -> Unit,
    onTabClosed: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tabs.isEmpty()) {
        HorizontalDivider()
        return
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .height(40.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            TabItem(
                label = tab.displayName(),
                isActive = tab.id == activeTabId,
                onSelect = { onTabSelected(tab.id) },
                onClose = { onTabClosed(tab.id) }
            )
        }
    }
    HorizontalDivider()
}

@Composable
private fun TabItem(
    label: String,
    isActive: Boolean,
    onSelect: () -> Unit,
    onClose: () -> Unit
) {
    val background = if (isActive)
        MaterialTheme.colorScheme.secondaryContainer
    else
        MaterialTheme.colorScheme.surface

    Row(
        modifier = Modifier
            .height(40.dp)
            .widthIn(max = 200.dp)
            .background(background)
            .clickable(onClick = onSelect),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.widthIn(max = 140.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        IconButton(
            onClick = onClose,
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Close tab",
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
    }
    VerticalDivider(modifier = Modifier.height(40.dp))
}
