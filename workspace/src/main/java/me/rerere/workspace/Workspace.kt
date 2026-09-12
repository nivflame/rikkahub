package me.rerere.workspace

import java.io.File
import java.nio.file.Files

internal fun File.walkNoFollow(): Sequence<File> = sequence {
    val stack = ArrayDeque<File>()
    stack.add(this@walkNoFollow)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        yield(current)
        if (current.isDirectory && !Files.isSymbolicLink(current.toPath())) {
            val children = runCatching { current.listFiles().orEmpty() }.getOrDefault(emptyArray())
            for (index in children.indices.reversed()) {
                stack.add(children[index])
            }
        }
    }
}

internal fun File.deleteTreeNoFollow(): Boolean {
    var ok = true
    val entries = walkNoFollow().toList()
    for (index in entries.indices.reversed()) {
        if (!entries[index].delete()) ok = false
    }
    return ok
}

data class Workspace(
    val id: String,
    val name: String,
    val root: String,
    val shellStatus: WorkspaceShellStatus = WorkspaceShellStatus.DISABLED,
    val createdAt: Long,
    val updatedAt: Long,
    val lastAccessAt: Long? = null,
)

enum class WorkspaceShellStatus {
    DISABLED,
    INSTALLING,
    READY,
    BROKEN,
}

enum class WorkspaceStorageArea {
    FILES,
    LINUX,
}

enum class RootfsInstallStage {
    DOWNLOADING,
    EXTRACTING,
    ARCHIVING,
    INSTALLED,
}

data class RootfsInstallProgress(
    val stage: RootfsInstallStage,
    val bytesRead: Long = 0,
    val totalBytes: Long? = null,
    val entriesExtracted: Int = 0,
    val currentEntry: String? = null,
)

data class WorkspaceConfig(
    val maxReadBytes: Long = 512 * 1024,
    val maxWriteBytes: Long = 2 * 1024 * 1024,
    val maxListEntries: Int = 500,
    val maxSearchResults: Int = 100,
)

data class WorkspaceFileEntry(
    val path: String,
    val name: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val updatedAt: Long,
    val file: File? = null,
)

data class WorkspaceSearchMatch(
    val path: String,
    val line: Int,
    val text: String,
)

data class WorkspaceCommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean = false,
    val truncated: Boolean = false,
)
