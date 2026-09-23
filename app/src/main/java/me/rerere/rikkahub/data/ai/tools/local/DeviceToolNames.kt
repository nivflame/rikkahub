package me.rerere.rikkahub.data.ai.tools.local

internal val ALL_DEVICE_TOOL_NAMES: List<String> = listOf(
    "device_state",
    "device_tap",
    "device_swipe",
    "device_drag",
    "device_type",
    "device_key",
)

val DEFAULT_ENABLED_DEVICE_TOOLS: Set<String> = ALL_DEVICE_TOOL_NAMES.toSet()
