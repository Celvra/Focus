package fan.x0.focus.core.designsystem.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Work
import androidx.compose.ui.graphics.vector.ImageVector

object FocusCategoryIcons {
    fun resolve(key: String?): ImageVector = when (key) {
        "news" -> Icons.Rounded.Newspaper
        "code" -> Icons.Rounded.Code
        "book" -> Icons.Rounded.MenuBook
        "money" -> Icons.Rounded.Payments
        "health" -> Icons.Rounded.Favorite
        "people" -> Icons.Rounded.Groups
        "work" -> Icons.Rounded.Work
        "home" -> Icons.Rounded.Home
        "travel" -> Icons.Rounded.Flight
        "time" -> Icons.Rounded.Schedule
        "goal" -> Icons.Rounded.Flag
        else -> Icons.Rounded.AutoAwesome
    }
}
