package me.rerere.rikkahub.editor

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import me.rerere.rikkahub.ui.theme.RikkahubTheme

class ImageEditorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceUri = intent.getParcelableExtra<Uri>(EXTRA_SOURCE_URI)
        if (sourceUri == null) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        setContent {
            RikkahubTheme {
                ImageEditorScreen(
                    sourceUri = sourceUri,
                    onResult = { outputUri ->
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_OUTPUT_URI, outputUri)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    },
                    onCancel = {
                        setResult(Activity.RESULT_CANCELED)
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_SOURCE_URI = "source_uri"
        const val EXTRA_OUTPUT_URI = "output_uri"
    }
}
