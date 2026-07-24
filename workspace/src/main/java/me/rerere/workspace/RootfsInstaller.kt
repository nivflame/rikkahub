package me.rerere.workspace

import java.io.BufferedInputStream
import java.io.EOFException
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import org.tukaani.xz.XZInputStream

class RootfsInstaller(
    private val manager: WorkspaceManager,
    private val patcher: RootfsPatcher = RootfsPatcher(),
) {
    fun install(
        root: String,
        url: String,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ) {
        require(url.isNotBlank()) { "Rootfs download url is required" }
        manager.ensureWorkspace(root)
        val format = ArchiveFormat.fromUrl(url)
        val tempDir = manager.tempDir(root)
        val archive = File(tempDir, "rootfs.${format.extension}")
        val stagingDir = File(tempDir, "rootfs-staging")
        val linuxDir = manager.linuxDir(root)

        try {
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            download(url, archive, onProgress)
            extractTar(archive, stagingDir, format, onProgress)
            linuxDir.deleteRecursively()
            require(stagingDir.renameTo(linuxDir)) {
                "Failed to move rootfs into workspace"
            }
            patcher.patch(linuxDir)
            onProgress(RootfsInstallProgress(stage = RootfsInstallStage.INSTALLED))
        } finally {
            archive.delete()
            stagingDir.deleteRecursively()
        }
    }

    fun export(
        root: String,
        outputStream: OutputStream,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ) {
        val linuxDir = manager.linuxDir(root)
        require(linuxDir.exists()) { "Rootfs not found: $root" }
        GZIPOutputStream(outputStream).use { gzip ->
            val tarWriter = TarWriter(gzip)
            val basePath = linuxDir.canonicalFile
            val totalEntries = countEntries(basePath)
            var entries = 0
            basePath.walkTopDown().forEach { file ->
                checkInterrupted()
                val relativePath = basePath.toPath().relativize(file.toPath()).joinToString("/")
                if (relativePath.isBlank()) return@forEach

                val isSymlink = Files.isSymbolicLink(file.toPath())
                val isDir = file.isDirectory && !isSymlink

                if (isSymlink) {
                    val linkTarget = Files.readSymbolicLink(file.toPath()).toString()
                    tarWriter.writeEntry(
                        name = relativePath,
                        size = 0L,
                        mode = 0b111_101_101,
                        type = TarEntryType.SYMLINK,
                        isSymlink = true,
                        linkName = linkTarget,
                    ) { }
                } else if (isDir) {
                    tarWriter.writeEntry(
                        name = "$relativePath/",
                        size = 0L,
                        mode = 0b111_101_101,
                        type = TarEntryType.DIRECTORY,
                        isSymlink = false,
                        linkName = "",
                    ) { }
                } else {
                    val mode = getFileMode(file)
                    tarWriter.writeEntry(
                        name = relativePath,
                        size = file.length(),
                        mode = mode,
                        type = TarEntryType.FILE,
                        isSymlink = false,
                        linkName = "",
                    ) { output ->
                        file.inputStream().use { input ->
                            val buffer = ByteArray(BUFFER_SIZE)
                            while (true) {
                                checkInterrupted()
                                val read = input.read(buffer)
                                if (read < 0) break
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                entries++
                onProgress(
                    RootfsInstallProgress(
                        stage = RootfsInstallStage.EXTRACTING,
                        entriesExtracted = totalEntries,
                        currentEntry = "$entries/$totalEntries",
                    )
                )
            }
            tarWriter.finish()
        }
    }

    fun importFromStream(
        root: String,
        inputStream: InputStream,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ) {
        manager.ensureWorkspace(root)
        val tempDir = manager.tempDir(root)
        val stagingDir = File(tempDir, "rootfs-import-staging")
        val linuxDir = manager.linuxDir(root)

        try {
            stagingDir.deleteRecursively()
            stagingDir.mkdirs()
            extractTarFromStream(inputStream, stagingDir, onProgress)
            linuxDir.deleteRecursively()
            require(stagingDir.renameTo(linuxDir)) {
                "Failed to move imported rootfs into workspace"
            }
            patcher.patch(linuxDir)
            onProgress(RootfsInstallProgress(stage = RootfsInstallStage.INSTALLED))
        } finally {
            stagingDir.deleteRecursively()
        }
    }

    private fun countEntries(dir: File): Int {
        var count = 0
        dir.walkTopDown().forEach { count++ }
        return count
    }

    private fun getFileMode(file: File): Int {
        return try {
            val perms = Files.getPosixFilePermissions(file.toPath())
            var mode = 0
            if (perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_READ)) mode = mode or 0b100_000_000
            if (perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE)) mode = mode or 0b010_000_000
            if (perms.contains(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)) mode = mode or 0b001_000_000
            if (perms.contains(java.nio.file.attribute.PosixFilePermission.GROUP_READ)) mode = mode or 0b000_100_000
            if (perms.contains(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)) mode = mode or 0b000_010_000
            if (perms.contains(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE)) mode = mode or 0b000_001_000
            if (perms.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_READ)) mode = mode or 0b000_000_100
            if (perms.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)) mode = mode or 0b000_000_010
            if (perms.contains(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE)) mode = mode or 0b000_000_001
            mode
        } catch (_: Throwable) {
            0b100_100_100
        }
    }

    private fun extractTarFromStream(
        inputStream: InputStream,
        targetDir: File,
        onProgress: (RootfsInstallProgress) -> Unit,
    ) {
        GZIPInputStream(BufferedInputStream(inputStream)).use { input ->
            var entries = 0
            var pendingName: String? = null
            var pendingLinkName: String? = null
            while (true) {
                checkInterrupted()
                val rawHeader = input.readTarHeader() ?: break
                val header = rawHeader.copy(
                    name = pendingName ?: rawHeader.name,
                    linkName = pendingLinkName ?: rawHeader.linkName,
                )
                pendingName = null
                pendingLinkName = null
                if (header.name.isBlank()) {
                    input.skipFully(header.size.paddedTarSize())
                    continue
                }
                if (header.type == TarEntryType.LONG_NAME) {
                    pendingName = input.readExactly(header.size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                if (header.type == TarEntryType.LONG_LINK) {
                    pendingLinkName = input.readExactly(header.size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                if (header.type == TarEntryType.PAX) {
                    val pax = parsePax(input.readExactly(header.size).toString(Charsets.UTF_8))
                    pendingName = pax["path"]
                    pendingLinkName = pax["linkpath"]
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                val target = targetDir.safeResolve(header.name)
                target.parentFile?.mkdirs()
                when (header.type) {
                    TarEntryType.DIRECTORY -> target.mkdirs()
                    TarEntryType.SYMLINK -> createSymlink(targetDir, target, header.linkName)
                    TarEntryType.HARDLINK -> createHardLink(targetDir, target, header.linkName)
                    TarEntryType.FILE -> {
                        target.outputStream().use { output ->
                            input.copyExactly(output, header.size)
                        }
                        target.applyMode(header.mode)
                    }
                    TarEntryType.LONG_NAME,
                    TarEntryType.LONG_LINK,
                    TarEntryType.PAX,
                    TarEntryType.OTHER -> Unit
                }
                if (header.type != TarEntryType.FILE) {
                    input.skipFully(header.size)
                }
                input.skipFully(header.size.paddingSize())
                if (header.modTime > 0 && header.type != TarEntryType.SYMLINK) {
                    target.setLastModified(header.modTime * 1000)
                }
                entries++
                onProgress(
                    RootfsInstallProgress(
                        stage = RootfsInstallStage.EXTRACTING,
                        entriesExtracted = entries,
                        currentEntry = header.name,
                    )
                )
            }
        }
    }

    private class TarWriter(private val output: OutputStream) {
        fun writeEntry(
            name: String,
            size: Long,
            mode: Int,
            type: TarEntryType,
            isSymlink: Boolean,
            linkName: String,
            data: (OutputStream) -> Unit,
        ) {
            val header = ByteArray(TAR_BLOCK_SIZE)
            writeString(header, 0, 100, name)
            writeOctal(header, 100, 8, mode.toLong())
            writeOctal(header, 108, 8, 0L) // uid
            writeOctal(header, 116, 8, 0L) // gid
            writeOctal(header, 124, 12, size)
            writeOctal(header, 136, 12, System.currentTimeMillis() / 1000) // mtime
            header[156] = when (type) {
                TarEntryType.FILE -> '0'.code.toByte()
                TarEntryType.DIRECTORY -> '5'.code.toByte()
                TarEntryType.SYMLINK -> '2'.code.toByte()
                TarEntryType.HARDLINK -> '1'.code.toByte()
                else -> '0'.code.toByte()
            }
            writeString(header, 157, 100, linkName)
            // ustar magic
            writeString(header, 257, 6, "ustar")
            header[263] = ' '.code.toByte() // version
            // type flag already set
            // zero out remaining fields (uid/gid names etc)
            // checksum: fill spaces first, compute later
            for (i in 148..155) header[i] = ' '.code.toByte()
            var checksum = 0
            for (b in header) checksum += (b.toInt() and 0xFF)
            writeOctal(header, 148, 8, checksum.toLong())
            output.write(header)
            data(output)
            // pad to block size
            val padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)).let { if (it == TAR_BLOCK_SIZE.toLong()) 0L else it }
            if (padding > 0) {
                output.write(ByteArray(padding.toInt()))
            }
        }

        fun finish() {
            // Two zero blocks mark end of archive
            output.write(ByteArray(TAR_BLOCK_SIZE * 2))
        }

        private fun writeString(buf: ByteArray, offset: Int, maxLen: Int, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val len = minOf(bytes.size, maxLen - 1)
            System.arraycopy(bytes, 0, buf, offset, len)
            buf[offset + len] = 0
        }

        private fun writeOctal(buf: ByteArray, offset: Int, length: Int, value: Long) {
            val str = String.format(Locale.US, "%0${length - 1}o", value)
            val bytes = str.toByteArray(Charsets.US_ASCII)
            System.arraycopy(bytes, 0, buf, offset, minOf(bytes.size, length - 1))
            buf[offset + length - 1] = 0
        }

        companion object {
            private const val TAR_BLOCK_SIZE = 512
        }
    }

    private fun download(
        url: String,
        target: File,
        onProgress: (RootfsInstallProgress) -> Unit,
    ) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS
        connection.instanceFollowRedirects = true
        try {
            val code = connection.responseCode
            require(code in 200..299) { "Rootfs download failed: HTTP $code" }
            val totalBytes = connection.contentLengthLong.takeIf { it > 0 }
            target.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    var bytesRead = 0L
                    var lastReportBytes = 0L
                    while (true) {
                        checkInterrupted()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (bytesRead - lastReportBytes >= PROGRESS_STEP_BYTES || bytesRead == totalBytes) {
                            lastReportBytes = bytesRead
                            onProgress(
                                RootfsInstallProgress(
                                    stage = RootfsInstallStage.DOWNLOADING,
                                    bytesRead = bytesRead,
                                    totalBytes = totalBytes,
                                )
                            )
                        }
                    }
                    if (bytesRead == 0L) {
                        onProgress(
                            RootfsInstallProgress(
                                stage = RootfsInstallStage.DOWNLOADING,
                                bytesRead = 0,
                                totalBytes = totalBytes,
                            )
                        )
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    internal fun extractTar(
        archive: File,
        targetDir: File,
        format: ArchiveFormat = ArchiveFormat.fromFile(archive),
        onProgress: (RootfsInstallProgress) -> Unit,
    ) {
        format.wrapStream(BufferedInputStream(archive.inputStream())).use { input ->
            var entries = 0
            var pendingName: String? = null
            var pendingLinkName: String? = null
            while (true) {
                checkInterrupted()
                val rawHeader = input.readTarHeader() ?: break
                val header = rawHeader.copy(
                    name = pendingName ?: rawHeader.name,
                    linkName = pendingLinkName ?: rawHeader.linkName,
                )
                pendingName = null
                pendingLinkName = null
                if (header.name.isBlank()) {
                    input.skipFully(header.size.paddedTarSize())
                    continue
                }
                if (header.type == TarEntryType.LONG_NAME) {
                    pendingName = input.readExactly(header.size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                if (header.type == TarEntryType.LONG_LINK) {
                    pendingLinkName = input.readExactly(header.size).toString(Charsets.UTF_8).trimEnd('\u0000', '\n')
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                if (header.type == TarEntryType.PAX) {
                    val pax = parsePax(input.readExactly(header.size).toString(Charsets.UTF_8))
                    pendingName = pax["path"]
                    pendingLinkName = pax["linkpath"]
                    input.skipFully(header.size.paddingSize())
                    continue
                }
                val target = targetDir.safeResolve(header.name)
                target.parentFile?.mkdirs()
                when (header.type) {
                    TarEntryType.DIRECTORY -> target.mkdirs()
                    TarEntryType.SYMLINK -> createSymlink(targetDir, target, header.linkName)
                    TarEntryType.HARDLINK -> createHardLink(targetDir, target, header.linkName)
                    TarEntryType.FILE -> {
                        target.outputStream().use { output ->
                            input.copyExactly(output, header.size)
                        }
                        target.applyMode(header.mode)
                    }

                    // LONG_NAME/LONG_LINK/PAX 已在上方 continue, 这里只有 OTHER 可达;
                    // 数据区统一由下方的非 FILE skip 跳过, 这里再 skip 会双重跳过导致后续 header 错位
                    TarEntryType.LONG_NAME,
                    TarEntryType.LONG_LINK,
                    TarEntryType.PAX,
                    TarEntryType.OTHER -> Unit
                }
                if (header.type != TarEntryType.FILE) {
                    input.skipFully(header.size)
                }
                input.skipFully(header.size.paddingSize())
                if (header.modTime > 0 && header.type != TarEntryType.SYMLINK) {
                    target.setLastModified(header.modTime * 1000)
                }
                entries++
                onProgress(
                    RootfsInstallProgress(
                        stage = RootfsInstallStage.EXTRACTING,
                        entriesExtracted = entries,
                        currentEntry = header.name,
                    )
                )
            }
        }
    }

    private fun createSymlink(root: File, target: File, linkName: String) {
        if (linkName.isBlank()) return
        val linkTarget = if (File(linkName).isAbsolute) {
            File(linkName)
        } else {
            val resolved = File(target.parentFile ?: root, linkName).canonicalFile
            val rootFile = root.canonicalFile
            require(resolved.path == rootFile.path || resolved.path.startsWith(rootFile.path + File.separator)) {
                "Symlink escapes rootfs: ${target.name}"
            }
            (target.parentFile ?: root).toPath().relativize(resolved.toPath()).toFile()
        }
        target.delete()
        Files.createSymbolicLink(target.toPath(), linkTarget.toPath())
    }

    private fun createHardLink(root: File, target: File, linkName: String) {
        if (linkName.isBlank()) return
        val source = root.safeResolve(linkName)
        if (!source.exists()) return
        target.delete()
        runCatching {
            Files.createLink(target.toPath(), source.toPath())
        }.recoverCatching { error ->
            if (error !is IOException &&
                error !is UnsupportedOperationException &&
                error !is SecurityException
            ) {
                throw error
            }
            source.copyTo(target, overwrite = true)
            target.setReadable(source.canRead(), false)
            target.setWritable(source.canWrite(), true)
            target.setExecutable(source.canExecute(), false)
        }.getOrThrow()
    }

    private fun InputStream.readTarHeader(): TarHeader? {
        val header = ByteArray(TAR_BLOCK_SIZE)
        val read = readFullyOrEnd(header)
        if (read == 0) return null
        if (read < TAR_BLOCK_SIZE) throw EOFException("Unexpected EOF while reading tar header")
        if (header.all { it == 0.toByte() }) return null

        val name = header.string(0, 100)
        val prefix = header.string(345, 155)
        val fullName = listOf(prefix, name)
            .filter { it.isNotBlank() }
            .joinToString("/")
        return TarHeader(
            name = normalizeTarPath(fullName),
            mode = header.octal(100, 8).toInt(),
            size = header.octal(124, 12),
            modTime = header.octal(136, 12),
            type = when (header[156].toInt().toChar()) {
                '0', '\u0000' -> TarEntryType.FILE
                '5' -> TarEntryType.DIRECTORY
                '2' -> TarEntryType.SYMLINK
                '1' -> TarEntryType.HARDLINK
                'L' -> TarEntryType.LONG_NAME
                'K' -> TarEntryType.LONG_LINK
                'x' -> TarEntryType.PAX
                else -> TarEntryType.OTHER
            },
            linkName = header.string(157, 100),
        )
    }

    private fun parsePax(text: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var index = 0
        while (index < text.length) {
            val space = text.indexOf(' ', index)
            if (space < 0) break
            val length = text.substring(index, space).toIntOrNull() ?: break
            val end = (index + length).coerceAtMost(text.length)
            val record = text.substring(space + 1, end).trimEnd('\n')
            val equals = record.indexOf('=')
            if (equals > 0) {
                result[record.substring(0, equals)] = record.substring(equals + 1)
            }
            index += length
        }
        return result
    }

    // 协程取消时调用方通过 runInterruptible 将取消转成线程中断, 这里在阻塞循环中检测并尽早退出,
    // 避免离开页面后仍继续下载/解压并向已清空的 StateFlow 推送进度
    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) {
            throw InterruptedException("Rootfs install cancelled")
        }
    }

    private fun InputStream.copyExactly(output: java.io.OutputStream, bytes: Long) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = bytes
        while (remaining > 0) {
            checkInterrupted()
            val read = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
            if (read < 0) throw EOFException("Unexpected EOF while extracting tar entry")
            output.write(buffer, 0, read)
            remaining -= read
        }
    }

    private fun InputStream.readExactly(bytes: Long): ByteArray {
        require(bytes <= Int.MAX_VALUE) { "Tar entry is too large to buffer: $bytes" }
        val buffer = ByteArray(bytes.toInt())
        val read = readFullyOrEnd(buffer)
        if (read != buffer.size) throw EOFException("Unexpected EOF while reading tar entry")
        return buffer
    }

    private fun InputStream.skipFully(bytes: Long) {
        var remaining = bytes
        while (remaining > 0) {
            checkInterrupted()
            val skipped = skip(remaining)
            if (skipped > 0) {
                remaining -= skipped
            } else if (read() >= 0) {
                remaining--
            } else {
                throw EOFException("Unexpected EOF while skipping tar data")
            }
        }
    }

    private fun InputStream.readFullyOrEnd(buffer: ByteArray): Int {
        var offset = 0
        while (offset < buffer.size) {
            val read = read(buffer, offset, buffer.size - offset)
            if (read < 0) break
            offset += read
        }
        return offset
    }

    private fun File.safeResolve(path: String): File {
        val normalized = normalizeTarPath(path)
        val root = canonicalFile
        val target = File(root, normalized).canonicalFile
        require(target.path == root.path || target.path.startsWith(root.path + File.separator)) {
            "Rootfs entry escapes target directory: $path"
        }
        return target
    }

    private fun File.applyMode(mode: Int) {
        setReadable(mode and 0b100_000_000 != 0, false)
        setWritable(mode and 0b010_000_000 != 0, true)
        setExecutable(mode and 0b001_000_000 != 0, false)
    }

    private fun normalizeTarPath(path: String): String {
        val normalized = path
            .replace('\\', '/')
            .trim()
            .trimStart('/')
            .removePrefix("./")
        if (normalized.isBlank()) return ""
        require(!normalized.contains('\u0000')) { "Rootfs entry path contains invalid character" }
        require(normalized.split('/').none { it == ".." }) { "Rootfs entry escapes target directory: $path" }
        return normalized
    }

    private fun ByteArray.string(offset: Int, length: Int): String {
        val end = (offset until offset + length)
            .firstOrNull { this[it] == 0.toByte() }
            ?: (offset + length)
        return copyOfRange(offset, end).toString(Charsets.UTF_8).trim()
    }

    private fun ByteArray.octal(offset: Int, length: Int): Long {
        val value = string(offset, length)
            .trim()
            .lowercase(Locale.US)
            .trimEnd('\u0000')
        return if (value.isBlank()) 0L else value.toLong(8)
    }

    private fun Long.paddingSize(): Long = (TAR_BLOCK_SIZE - (this % TAR_BLOCK_SIZE)).let {
        if (it == TAR_BLOCK_SIZE.toLong()) 0L else it
    }

    private fun Long.paddedTarSize(): Long = this + paddingSize()

    private data class TarHeader(
        val name: String,
        val mode: Int,
        val size: Long,
        val modTime: Long,
        val type: TarEntryType,
        val linkName: String,
    )

    private enum class TarEntryType {
        FILE,
        DIRECTORY,
        SYMLINK,
        HARDLINK,
        LONG_NAME,
        LONG_LINK,
        PAX,
        OTHER,
    }

    enum class ArchiveFormat(val extension: String) {
        TAR_GZ("tar.gz") {
            override fun wrapStream(input: InputStream): InputStream = GZIPInputStream(input)
        },
        TAR_XZ("tar.xz") {
            override fun wrapStream(input: InputStream): InputStream = XZInputStream(input)
        };

        abstract fun wrapStream(input: InputStream): InputStream

        companion object {
            fun fromUrl(url: String): ArchiveFormat {
                val path = url.substringBefore('?').substringBefore('#')
                return when {
                    path.endsWith(".tar.xz") || path.endsWith(".txz") -> TAR_XZ
                    else -> TAR_GZ
                }
            }

            fun fromFile(file: File): ArchiveFormat = fromUrl(file.name)
        }
    }

    companion object {
        private const val TAR_BLOCK_SIZE = 512
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_STEP_BYTES = 512 * 1024
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 60_000
    }
}
