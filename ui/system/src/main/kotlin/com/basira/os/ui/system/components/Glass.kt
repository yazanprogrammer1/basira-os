package com.basira.os.ui.system.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.basira.os.ui.system.theme.BasiraColors
import com.basira.os.ui.system.theme.BasiraShapes
import com.basira.os.ui.system.theme.MinTarget

/**
 * Glass panel: translucent fill + 1 dp hairline border.
 * No real-time blur on purpose: blur per panel per eye is too expensive for the thermal budget.
 */
@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = BasiraShapes.Panel,
    contentPadding: PaddingValues = PaddingValues(20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .background(BasiraColors.Glass, shape)
            .border(1.dp, BasiraColors.GlassBorder, shape)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = BasiraShapes.Pill,
        colors = ButtonDefaults.buttonColors(
            containerColor = BasiraColors.Emerald,
            contentColor = BasiraColors.Night,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
        modifier = modifier.defaultMinSize(minHeight = MinTarget),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}

/** Small status pill with a coloured dot, e.g. «38° وضع التبريد». */
@Composable
fun StatusPill(
    text: String,
    dotColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .background(BasiraColors.Glass, BasiraShapes.Pill)
            .border(1.dp, BasiraColors.GlassBorder, BasiraShapes.Pill)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            Modifier
                .size(10.dp)
                .background(dotColor, CircleShape),
        )
        Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = BasiraColors.Text)
    }
}
