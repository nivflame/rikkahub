package me.rerere.rikkahub.data.sync

import com.github.luben.zstd.ZstdInputStream
import com.github.luben.zstd.ZstdOutputStream
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.io.SequenceInputStream
import java.util.Locale
import java.util.zip.ZipInputStream

const val BACKUP_MIME_ZSTD = "application/zstd"
const val BACKUP_MIME_ZIP = "application/zip"
const val BACKUP_EXTENSION = ".tar.zst"
const val BACKUP_LEGACY_EXTENSION = ".zip"
const val BACKUP_ZSTD_LEVEL = 3

private const val TAR_BLOCK_SIZE = 512
private val ZSTD_MAGIC = byteArrayOf(0x28, 0xB5.toByte(), 0x2F, 0xFD.toByte())
private val ZIP_MAGIC = byteArrayOf(0x50, 0x4B, 0x03, 0x04)

class BackupTarWriter(output: OutputStream) : AutoCloseable {
    private val zstd = ZstdOutputStream(BufferedOutputStream(output)).apply { setLevel(BACKUP_ZSTD_LEVEL) }

    fun addBytes(name: String, data: ByteArray) {
        writeHeader(name, data.size.toLong())
        zstd.write(data)
        writePadding(data.size.toLong())
    }

    fun addFile(name: String, file: File) {
        FileInputStream(file).use { input ->
            BufferedInputStream(input).use { buffered ->
                writeHeader(name, file.length())
                buffered.copyTo(zstd)
                writePadding(file.length())
            }
        }
    }

    override fun close() {
        zstd.write(ByteArray(TAR_BLOCK_SIZE * 2))
        zstd.close()
    }

    private fun writeHeader(name: String, size: Long) {
        require(name.toByteArray(Charsets.UTF_8).size <= 100) { "Entry name too long for tar: $name" }
        val header = ByteArray(TAR_BLOCK_SIZE)
        writeString(header, 0, 100, name)
        writeOctal(header, 100, 8, 420L)
        writeOctal(header, 108, 8, 0L)
        writeOctal(header, 116, 8, 0L)
        writeOctal(header, 124, 12, size)
        writeOctal(header, 136, 12, System.currentTimeMillis() / 1000)
        header[156] = '0'.code.toByte()
        writeString(header, 257, 6, "ustar")
        header[263] = ' '.code.toByte()
        for (i in 148..155) header[i] = ' '.code.toByte()
        var checksum = 0
        for (b in header) checksum += b.toInt() and 0xFF
        writeOctal(header, 148, 8, checksum.toLong())
        zstd.write(header)
    }

    private fun writePadding(size: Long) {
        val padding = (TAR_BLOCK_SIZE - size % TAR_BLOCK_SIZE).let {
            if (it == TAR_BLOCK_SIZE.toLong()) 0L else it
        }
        if (padding > 0) zstd.write(ByteArray(padding.toInt()))
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
        require(bytes.size <= length - 1) { "Value $value does not fit in tar octal field" }
        System.arraycopy(bytes, 0, buf, offset, bytes.size)
        buf[offset + length - 1] = 0
    }
}

class BackupEntryReader internal constructor(
    private val next: () -> BackupEntryStream?,
    private val onClose: () -> Unit = {},
) : AutoCloseable {
    fun nextEntry(): BackupEntryStream? = next()
    override fun close() = onClose()
}

interface BackupEntryStream {
    val name: String
    val size: Long
    fun readBytes(): ByteArray
    fun copyTo(output: OutputStream)
}

fun openBackupReader(file: File): BackupEntryReader {
    val fis = FileInputStream(file)
    try {
        val magic = ByteArray(4)
        var read = 0
        while (read < 4) {
            val n = fis.read(magic, read, 4 - read)
            if (n < 0) break
            read += n
        }
        val head = magic.copyOf(read)
        val stream = SequenceInputStream(head.inputStream(), fis)
        return when {
            head.contentEquals(ZIP_MAGIC) -> {
                val source = ZipEntrySource(ZipInputStream(stream))
                BackupEntryReader(source::nextZipEntry, source::close)
            }
            head.contentEquals(ZSTD_MAGIC) -> {
                val source = TarEntrySource(ZstdInputStream(stream))
                BackupEntryReader(source::nextTarEntry, source::close)
            }
            else -> {
                stream.close()
                error("Unsupported backup format")
            }
        }
    } catch (e: Exception) {
        fis.close()
        throw e
    }
}

private class ZipEntrySource(private val zipIn: ZipInputStream) : AutoCloseable {
    fun nextZipEntry(): BackupEntryStream? {
        val entry = zipIn.nextEntry ?: return null
        return object : BackupEntryStream {
            override val name = entry.name
            override val size = entry.size.takeIf { it >= 0 } ?: Long.MAX_VALUE
            override fun readBytes(): ByteArray = zipIn.readBytes().also { zipIn.closeEntry() }
            override fun copyTo(output: OutputStream) {
                zipIn.copyTo(output)
                zipIn.closeEntry()
            }
        }
    }

    override fun close() = zipIn.close()
}

private class TarEntrySource(private val tarIn: InputStream) : AutoCloseable {
    fun nextTarEntry(): BackupEntryStream? {
        while (true) {
            val header = tarIn.readTarHeader() ?: return null
            if (header.type != '0' && header.type != 0.toChar()) {
                tarIn.skipFully(header.size + header.size.paddingSize())
                continue
            }
            return object : BackupEntryStream {
                override val name = header.name
                override val size = header.size
                override fun readBytes(): ByteArray = tarIn.readExactly(header.size).also {
                    tarIn.skipFully(header.size.paddingSize())
                }
                override fun copyTo(output: OutputStream) {
                    tarIn.copyExactly(output, header.size)
                    tarIn.skipFully(header.size.paddingSize())
                }
            }
        }
    }

    override fun close() = tarIn.close()
}

private data class TarHeader(val name: String, val size: Long, val type: Char)

private fun InputStream.readTarHeader(): TarHeader? {
    val header = ByteArray(TAR_BLOCK_SIZE)
    var offset = 0
    while (offset < TAR_BLOCK_SIZE) {
        val n = read(header, offset, TAR_BLOCK_SIZE - offset)
        if (n < 0) break
        offset += n
    }
    if (offset == 0) return null
    if (offset < TAR_BLOCK_SIZE) error("Unexpected EOF while reading tar header")
    if (header.all { it == 0.toByte() }) return null
    val name = header.tarString(0, 100)
    val prefix = header.tarString(345, 155)
    val fullName = listOf(prefix, name).filter { it.isNotBlank() }.joinToString("/")
    val size = header.tarOctal(124, 12)
    require(size in 0..Int.MAX_VALUE) { "Invalid tar entry size: $size" }
    return TarHeader(fullName, size, header[156].toInt().toChar())
}

private fun InputStream.readExactly(bytes: Long): ByteArray {
    require(bytes in 0..Int.MAX_VALUE) { "Tar entry is too large to buffer: $bytes" }
    val buffer = ByteArray(bytes.toInt())
    var offset = 0
    while (offset < buffer.size) {
        val n = read(buffer, offset, buffer.size - offset)
        if (n < 0) error("Unexpected EOF while reading tar entry")
        offset += n
    }
    return buffer
}

private fun InputStream.copyExactly(output: OutputStream, bytes: Long) {
    require(bytes >= 0) { "Invalid tar entry size: $bytes" }
    val buffer = ByteArray(64 * 1024)
    var remaining = bytes
    while (remaining > 0) {
        val n = read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
        if (n < 0) error("Unexpected EOF while reading tar entry")
        output.write(buffer, 0, n)
        remaining -= n
    }
}

private fun InputStream.skipFully(bytes: Long) {
    var remaining = bytes
    while (remaining > 0) {
        val skipped = skip(remaining)
        if (skipped > 0) {
            remaining -= skipped
        } else if (read() >= 0) {
            remaining--
        } else {
            error("Unexpected EOF while skipping tar data")
        }
    }
}

private fun Long.paddingSize(): Long {
    val remainder = this % TAR_BLOCK_SIZE
    return if (remainder == 0L) 0L else TAR_BLOCK_SIZE - remainder
}

private fun ByteArray.tarString(offset: Int, length: Int): String {
    var end = offset
    while (end < offset + length && this[end] != 0.toByte()) end++
    return String(this, offset, end - offset, Charsets.UTF_8)
}

private fun ByteArray.tarOctal(offset: Int, length: Int): Long {
    val text = String(this, offset, length, Charsets.US_ASCII).trim().trimEnd(0.toChar()).trim()
    if (text.isEmpty()) return 0L
    return text.toLongOrNull(8) ?: error("Invalid tar octal field: '$text'")
}

fun writeBackupTarZst(file: File, block: BackupTarWriter.() -> Unit) {
    FileOutputStream(file).use { fos ->
        BackupTarWriter(fos).use { writer ->
            writer.block()
        }
    }
}
