package fan.x0.focus.core.designsystem.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

data class FocusPickerItem(
    val id: String,
    val label: String = id,
    val detail: FocusPickerDetail? = null,
)

data class FocusPickerGroup(
    val id: String,
    val title: String,
    val icon: String = "",
    val items: List<FocusPickerItem> = emptyList(),
)

data class FocusPickerSelection(
    val groupId: String,
    val itemId: String,
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FocusGroupedPickerSheet(
    title: String,
    searchHint: String,
    emptyText: String,
    groups: List<FocusPickerGroup>,
    query: String,
    onQueryChange: (String) -> Unit,
    onSelect: (FocusPickerSelection) -> Unit,
    onDismiss: () -> Unit,
    selected: FocusPickerSelection? = null,
    loading: Boolean = false,
    onRefresh: (() -> Unit)? = null,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val filtered = remember(groups, query) {
        val keyword = query.trim()
        if (keyword.isBlank()) {
            groups
        } else {
            groups.mapNotNull { group ->
                val items = if (group.title.contains(keyword, ignoreCase = true)) {
                    group.items
                } else {
                    group.items.filter {
                        it.label.contains(keyword, ignoreCase = true) ||
                            it.id.contains(keyword, ignoreCase = true)
                    }
                }
                if (items.isEmpty()) null else group.copy(items = items)
            }
        }.filter { it.items.isNotEmpty() }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                when {
                    loading -> FocusLoadingBlob(size = 18.dp)
                    onRefresh != null -> IconButton(onClick = hapticTap(onRefresh)) {
                        Icon(
                            imageVector = FocusIcons.Refresh,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(searchHint) },
                leadingIcon = {
                    Icon(
                        imageVector = FocusIcons.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                singleLine = true,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth(),
            )
            if (filtered.isEmpty()) {
                Text(
                    text = emptyText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 520.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    filtered.forEach { group ->
                        stickyHeader(
                            key = "group:${group.id}",
                            contentType = "group",
                        ) {
                            FocusPickerGroupHeader(group)
                        }
                        items(
                            items = group.items,
                            key = { "${group.id}:${it.id}" },
                            contentType = { "item" },
                        ) { item ->
                            FocusPickerItemRow(
                                item = item,
                                selected = selected?.groupId == group.id &&
                                    selected.itemId == item.id,
                                onClick = {
                                    onSelect(FocusPickerSelection(group.id, item.id))
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FocusPickerGroupHeader(group: FocusPickerGroup) {
    val image = rememberProviderImage(group.icon)
    val brand = BrandIcons.forKey(group.icon)
        ?: BrandIcons.forProvider(group.id, group.title)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            image != null -> Surface(
                modifier = Modifier.size(20.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
            ) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            brand != null -> BrandIcon(
                icon = brand,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            else -> Icon(
                imageVector = FocusIcons.Provider,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = group.title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = group.items.size.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FocusPickerItemRow(
    item: FocusPickerItem,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val brand = BrandIcons.forModel(item.id)
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = hapticSelect(onClick)),
        leadingContent = if (brand != null) {
            {
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    contentColor = if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    },
                ) {
                    Box(
                        modifier = Modifier.size(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        BrandIcon(
                            icon = brand,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        } else {
            null
        },
        trailingContent = if (selected) {
            {
                Icon(
                    imageVector = FocusIcons.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
        } else {
            null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (selected) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            headlineColor = if (selected) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            trailingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        content = {
            val detail = item.detail
            Column {
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (detail != null) {
                    FocusPickerDetailContent(detail)
                }
            }
        },
    )
}

@Composable
private fun FocusPickerDetailContent(detail: FocusPickerDetail) {
    val meta = detail.meta
    val capabilities = detail.capabilities
    val description = detail.description
    if (meta != null || capabilities.isNotEmpty()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 2.dp),
        ) {
            if (meta != null) {
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            capabilities.forEach { capability ->
                Icon(
                    imageVector = capability.icon,
                    contentDescription = capability.label,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(13.dp),
                )
            }
        }
    }
    if (description != null) {
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}
