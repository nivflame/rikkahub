package me.rerere.rikkahub.editor

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun useImageEditorLauncher(
    onResult: (Uri) -> Unit,
    onCleanup: (() -> Unit)? = null,
): Pair<ActivityResultLauncher<Intent>, (Uri) -> Unit> {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val outputUri = result.data?.getParcelableExtra<Uri>(ImageEditorActivity.EXTRA_OUTPUT_URI)
            outputUri?.let { onResult(it) }
        }
        onCleanup?.invoke()
    }

    val launch: (Uri) -> Unit = { sourceUri ->
        val intent = Intent(context, ImageEditorActivity::class.java).apply {
            putExtra(ImageEditorActivity.EXTRA_SOURCE_URI, sourceUri)
        }
        launcher.launch(intent)
    }

    return Pair(launcher, launch)
}
