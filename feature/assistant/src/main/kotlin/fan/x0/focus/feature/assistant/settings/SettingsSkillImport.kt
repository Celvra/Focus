package fan.x0.focus.feature.assistant.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import fan.x0.focus.core.designsystem.components.FocusOutlinedButton
import fan.x0.focus.core.designsystem.components.FocusPrimaryButton
import fan.x0.focus.core.designsystem.components.FocusTextButton
import fan.x0.focus.core.designsystem.components.hapticTap
import fan.x0.focus.data.assistant.SkillCandidate
import fan.x0.focus.feature.assistant.R
import fan.x0.focus.feature.assistant.SkillsAction

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SkillImportSheet(
    offers: List<SkillCandidate>,
    selected: Set<String>,
    busy: Boolean,
    onAction: (SkillsAction) -> Unit,
) {
    val sheetState = rememberBottomSheetState(
        initialValue = SheetValue.Hidden,
        enabledValues = setOf(SheetValue.Hidden, SheetValue.Expanded),
    )
    val allSelected = offers.isNotEmpty() && selected.size == offers.size
    ModalBottomSheet(
        onDismissRequest = { if (!busy) onAction(SkillsAction.CancelImport) },
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_skills_choose_title),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_skills_choose_summary,
                            selected.size,
                            offers.size,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FocusTextButton(
                    text = stringResource(
                        if (allSelected) {
                            R.string.settings_skills_clear_all
                        } else {
                            R.string.settings_skills_select_all
                        },
                    ),
                    onClick = {
                        onAction(
                            if (allSelected) {
                                SkillsAction.ClearImports
                            } else {
                                SkillsAction.SelectAllImports
                            },
                        )
                    },
                    enabled = !busy,
                )
            }
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(
                    items = offers,
                    key = { it.key },
                ) { offer ->
                    SkillOfferRow(
                        offer = offer,
                        checked = offer.key in selected,
                        enabled = !busy,
                        onToggle = { onAction(SkillsAction.ToggleImport(offer.key)) },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FocusOutlinedButton(
                    text = stringResource(R.string.assistant_cancel),
                    onClick = { onAction(SkillsAction.CancelImport) },
                    enabled = !busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
                FocusPrimaryButton(
                    text = stringResource(R.string.settings_skills_install),
                    onClick = { onAction(SkillsAction.ConfirmImport) },
                    enabled = selected.isNotEmpty(),
                    loading = busy,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                )
            }
        }
    }
}

@Composable
private fun SkillOfferRow(
    offer: SkillCandidate,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled, onClick = hapticTap(onToggle))
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = offer.name,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    text = offer.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (offer.installed) {
                    Text(
                        text = stringResource(R.string.settings_skills_installed_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Checkbox(
                checked = checked,
                onCheckedChange = { onToggle() },
                enabled = enabled,
            )
        }
    }
}
