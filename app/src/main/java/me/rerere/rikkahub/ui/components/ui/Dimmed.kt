package me.rerere.rikkahub.ui.components.ui

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

fun Modifier.dimmed(dim: Boolean): Modifier =
    if (dim) graphicsLayer { alpha = 0.38f } else this
