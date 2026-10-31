package fan.x0.focus.core.designsystem.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.theme.FocusMotionDefaults
import fan.x0.focus.core.designsystem.theme.FocusShapeTokens
import fan.x0.focus.core.designsystem.theme.LocalFocusTypography

@Composable
fun FocusCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    containerColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: androidx.compose.ui.graphics.Shape = FocusShapeTokens.card,
    contentPadding: Dp = 20.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier.fillMaxWidth()
    if (onClick != null) {
        Card(
            onClick = onClick,
            modifier = base,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    } else {
        Card(
            modifier = base,
            shape = shape,
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(modifier = Modifier.padding(contentPadding), content = content)
        }
    }
}

@Composable
fun FocusSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = LocalFocusTypography.current.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (action != null) {
            Spacer(Modifier.width(8.dp))
            action()
        }
    }
}

private val ButtonMinHeight = 56.dp

@Composable
fun FocusPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonMinHeight)
            .pressScale(interaction),
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = FocusShapeTokens.pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (loading) {
            FocusLoadingBlob(size = 20.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = LocalFocusTypography.current.labelLarge)
    }
}

@Composable
fun FocusTonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonMinHeight)
            .pressScale(interaction),
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = FocusShapeTokens.pill,
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (loading) {
            FocusLoadingBlob(size = 18.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = LocalFocusTypography.current.labelLarge)
    }
}

@Composable
fun FocusOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    loading: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = ButtonMinHeight)
            .pressScale(interaction),
        enabled = enabled && !loading,
        interactionSource = interaction,
        shape = FocusShapeTokens.pill,
        contentPadding = ButtonDefaults.ContentPadding,
    ) {
        if (loading) {
            FocusLoadingBlob(size = 18.dp)
            Spacer(Modifier.width(10.dp))
        } else if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, style = LocalFocusTypography.current.labelLarge)
    }
}

@Composable
fun FocusTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    TextButton(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 48.dp)
            .pressScale(interaction, pressedScale = 0.97f),
        enabled = enabled,
        interactionSource = interaction,
    ) {
        Text(text, style = LocalFocusTypography.current.labelLarge)
    }
}

@Composable
fun FocusChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leadingIcon: ImageVector? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = LocalFocusTypography.current.labelLarge,
            )
        },
        modifier = modifier.pressScale(interaction),
        leadingIcon = if (leadingIcon != null) {
            {
                Icon(
                    imageVector = leadingIcon,
                    contentDescription = null,
                    modifier = Modifier.size(FilterChipDefaults.IconSize),
                )
            }
        } else {
            null
        },
        interactionSource = interaction,
    )
}

enum class StatusTone { Neutral, Active, Success, Warning, Danger }

@Composable
fun FocusStatusPill(text: String, tone: StatusTone, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val (targetContainer, targetContent) = when (tone) {
        StatusTone.Neutral -> scheme.surfaceContainerHigh to scheme.onSurfaceVariant
        StatusTone.Active -> scheme.secondaryContainer to scheme.onSecondaryContainer
        StatusTone.Success -> scheme.primaryContainer to scheme.onPrimaryContainer
        StatusTone.Warning -> scheme.tertiaryContainer to scheme.onTertiaryContainer
        StatusTone.Danger -> scheme.errorContainer to scheme.onErrorContainer
    }
    val container by animateColorAsState(
        targetContainer,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pillContainer",
    )
    val content by animateColorAsState(
        targetContent,
        MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "pillContent",
    )
    Badge(
        containerColor = container,
        contentColor = content,
        modifier = modifier.heightIn(min = 24.dp),
    ) {
        Text(text = text)
    }
}

@Composable
fun FocusIconBadge(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary,
    container: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primaryContainer,
    size: Dp = 40.dp,
) {
    Surface(
        modifier = modifier.size(size),
        shape = CircleShape,
        color = container,
        contentColor = tint,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(size * 0.5f))
        }
    }
}

@Composable
fun FocusEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FocusIconBadge(icon = icon, size = 56.dp)
        Text(
            text = title,
            style = LocalFocusTypography.current.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            FocusTonalButton(text = actionLabel, onClick = onAction, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
fun FocusDivider(modifier: Modifier = Modifier, inset: Dp = 0.dp) {
    HorizontalDivider(
        modifier = modifier.padding(horizontal = inset),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
fun FocusTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(FocusIcons.Back, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.width(4.dp))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = LocalFocusTypography.current.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        actions()
    }
}

@Composable
fun FocusScreenTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        style = LocalFocusTypography.current.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Suppress("unused")
private val motionDefaults = FocusMotionDefaults
