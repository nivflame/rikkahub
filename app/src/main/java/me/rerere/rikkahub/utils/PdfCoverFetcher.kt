package me.rerere.rikkahub.utils

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import me.rerere.document.PdfParser
import okio.Buffer
import java.io.File

data class PdfCoverKey(val path: String, val lastModified: Long) {
    constructor(file: File) : this(file.absolutePath, file.lastModified())
}

class PdfCoverFetcher(
    private val data: PdfCoverKey,
    private val options: Options,
) : Fetcher {
    class Factory : Fetcher.Factory<PdfCoverKey> {
        override fun create(data: PdfCoverKey, options: Options, imageLoader: ImageLoader): Fetcher {
            return PdfCoverFetcher(data, options)
        }
    }

    override suspend fun fetch(): FetchResult? {
        val bytes = PdfParser.renderCover(File(data.path)) ?: return null
        return SourceFetchResult(
            source = ImageSource(
                source = Buffer().apply { write(bytes) },
                fileSystem = options.fileSystem,
            ),
            mimeType = "image/png",
            dataSource = DataSource.DISK,
        )
    }
}

class PdfCoverKeyer : Keyer<PdfCoverKey> {
    override fun key(data: PdfCoverKey, options: Options): String {
        return "${data.path}:${data.lastModified}"
    }
}
