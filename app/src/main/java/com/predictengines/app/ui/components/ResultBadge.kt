package com.predictengines.app.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.predictengines.app.ui.theme.CardSurfaceAlt
import com.predictengines.app.ui.theme.WinGreen

/**
 * The green circle that says how many played games still need a result. While the count is still being worked out
 * ([checking] with nothing known yet) it shows a tiny spinning ring; the circle pops in when it appears, the number
 * rolls when it changes, and the circle shrinks away at 0.
 */
@Composable
fun ResultBadge(count: Int, checking: Boolean, modifier: Modifier = Modifier) {
    val spinning = checking && count == 0
    AnimatedVisibility(
        visible = spinning || count > 0,
        modifier = modifier,
        enter = scaleIn(spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium)) + fadeIn(),
        exit = scaleOut() + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
                .clip(CircleShape)
                .background(if (spinning) CardSurfaceAlt else WinGreen)
                .padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            if (spinning) {
                CircularProgressIndicator(modifier = Modifier.size(11.dp), strokeWidth = 1.5.dp, color = WinGreen)
            } else {
                AnimatedContent(
                    targetState = count,
                    transitionSpec = { (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut()) },
                    label = "resultCount"
                ) { c ->
                    Text(
                        if (c > 99) "99+" else c.toString(),
                        style = MaterialTheme.typography.labelSmall, color = Color.Black, maxLines = 1
                    )
                }
            }
        }
    }
}
