package me.rerere.rikkahub.data.sync

import android.content.Context
import android.util.Log
import io.ktor.client.HttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import me.rerere.rikkahub.data.files.FileFolders
import me.rerere.rikkahub.data.files.SkillPaths
import me.rerere.rikkahub.data.sync.BACKUP_EXTENSION
import me.rerere.rikkahub.data.sync.BACKUP_LEGACY_EXTENSION
import me.rerere.rikkahub.data.sync.BACKUP_MIME_ZSTD
import me.rerere.rikkahub.data.sync.BackupEntryStream
import me.rerere.rikkahub.data.sync.BackupTarWriter
import me.rerere.rikkahub.data.sync.openBackupReader
import me.rerere.rikkahub.data.sync.writeBackupTarZst
import me.rerere.rikkahub.data.datastore.Settings
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.datastore.migration.SettingsJsonMigrator
import me.rerere.rikkahub.data.ai.tools.local.loadDefaultSubagentPrompts
import me.rerere.rikkahub.data.ai.tools.local.mergeSubagentPrompts
import me.rerere.rikkahub.data.codex.CodexAccountRepository
import me.rerere.rikkahub.data.codex.CodexAccountState
import me.rerere.rikkahub.data.sync.s3.S3Client
import me.rerere.rikkahub.data.sync.s3.S3Config
import me.rerere.rikkahub.utils.fileSizeToString
import java.io.File
import java.io.FileOutputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private const val TAG = "S3Sync"

class S3Sync(
    private val settingsStore: SettingsStore,
    private val json: Json,
    private val context: Context,
    private val httpClient: HttpClient,
    private val codexAccountRepository: CodexAccountRepository,
) {
    private fun getS3Client(config: S3Config): S3Client {
        return S3Client(config, httpClient)
    }

    suspend fun testS3(config: S3Config) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        // Test by listing objects with max 1 result
        client.listObjects(maxKeys = 1).getOrThrow()
        Log.i(TAG, "testS3: Connection successful")
    }

    suspend fun backupToS3(config: S3Config) = withContext(Dispatchers.IO) {
        val file = prepareBackupFile(config)
        val client = getS3Client(config)
        val key = "rikkahub_backups/${file.name}"

        client.putObject(
            key = key,
            file = file,
            contentType = BACKUP_MIME_ZSTD
        ).getOrThrow()

        Log.i(TAG, "backupToS3: Uploaded ${file.name} (${file.length().fileSizeToString()})")

        // Clean up temp file
        file.delete()
    }

    suspend fun listBackupFiles(config: S3Config): List<S3BackupItem> = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val result = client.listObjects(
            prefix = "rikkahub_backups/",
            maxKeys = 1000
        ).getOrThrow()

        result.objects
            .filter { it.key.startsWith("rikkahub_backups/backup_") &&
                (it.key.endsWith(BACKUP_EXTENSION) || it.key.endsWith(BACKUP_LEGACY_EXTENSION)) }
            .map { obj ->
                S3BackupItem(
                    key = obj.key,
                    displayName = obj.key.substringAfterLast("/"),
                    size = obj.size,
                    lastModified = obj.lastModified ?: Instant.EPOCH
                )
            }
            .sortedByDescending { it.lastModified }
    }

    suspend fun restoreFromS3(config: S3Config, item: S3BackupItem) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        val backupFile = File(context.cacheDir, item.displayName)

        try {
            // Download backup file directly to file to avoid OOM
            Log.i(TAG, "restoreFromS3: Downloading ${item.displayName}")
            client.downloadObjectToFile(item.key, backupFile).getOrThrow()

            Log.i(TAG, "restoreFromS3: Downloaded ${backupFile.length().fileSizeToString()}")

            // Restore from backup file
            restoreFromBackupFile(backupFile, config)
        } finally {
            // Clean up temp file
            if (backupFile.exists()) {
                backupFile.delete()
                Log.i(TAG, "restoreFromS3: Cleaned up temporary backup file")
            }
        }
    }

    suspend fun deleteS3BackupFile(config: S3Config, item: S3BackupItem) = withContext(Dispatchers.IO) {
        val client = getS3Client(config)
        client.deleteObject(item.key).getOrThrow()
        Log.i(TAG, "deleteS3BackupFile: Deleted ${item.key}")
    }

    suspend fun prepareBackupFile(config: S3Config): File = withContext(Dispatchers.IO) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
        val backupFile = File(context.cacheDir, "backup_$timestamp$BACKUP_EXTENSION")

        if (backupFile.exists()) {
            backupFile.delete()
        }

        val settingsJson = json.encodeToString(settingsStore.settingsFlow.value)
        val codexAccountsJson = json.encodeToString(codexAccountRepository.exportState())

        // Create tar.zst file and backup data
        writeBackupTarZst(backupFile) {
            addBytes(
                name = "settings.json",
                data = settingsJson.toByteArray()
            )

            addBytes(
                name = "codex_accounts.json",
                data = codexAccountsJson.toByteArray()
            )

            // Backup database files
            if (config.items.contains(S3Config.BackupItem.DATABASE)) {
                val dbFile = context.getDatabasePath("rikka_hub")
                if (dbFile.exists()) {
                    addFile(this, dbFile, "rikka_hub.db")
                }

                val walFile = File(dbFile.parentFile, "rikka_hub-wal")
                if (walFile.exists()) {
                    addFile(this, walFile, "rikka_hub-wal")
                }

                val shmFile = File(dbFile.parentFile, "rikka_hub-shm")
                if (shmFile.exists()) {
                    addFile(this, shmFile, "rikka_hub-shm")
                }
            }

            // Backup app files
            if (config.items.contains(S3Config.BackupItem.FILES)) {
                val uploadFolder = File(context.filesDir, FileFolders.UPLOAD)
                if (uploadFolder.exists() && uploadFolder.isDirectory) {
                    Log.i(TAG, "prepareBackupFile: Backing up files from ${uploadFolder.absolutePath}")
                    uploadFolder.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            addFile(this, file, "${FileFolders.UPLOAD}/${file.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "prepareBackupFile: Upload folder does not exist or is not a directory")
                }

                val skillsFolder = File(context.filesDir, FileFolders.SKILLS)
                if (skillsFolder.exists() && skillsFolder.isDirectory) {
                    Log.i(TAG, "prepareBackupFile: Backing up skills from ${skillsFolder.absolutePath}")
                    addDirectory(
                        writer = this,
                        rootDir = skillsFolder,
                        currentDir = skillsFolder,
                        entryPrefix = "${FileFolders.SKILLS}/"
                    )
                } else {
                    Log.w(TAG, "prepareBackupFile: Skills folder does not exist or is not a directory")
                }

                val fontsFolder = File(context.filesDir, FileFolders.FONTS)
                if (fontsFolder.exists() && fontsFolder.isDirectory) {
                    Log.i(TAG, "prepareBackupFile: Backing up fonts from ${fontsFolder.absolutePath}")
                    fontsFolder.listFiles()?.forEach { file ->
                        if (file.isFile) {
                            addFile(this, file, "${FileFolders.FONTS}/${file.name}")
                        }
                    }
                } else {
                    Log.w(TAG, "prepareBackupFile: Fonts folder does not exist or is not a directory")
                }
            }
        }

        Log.i(
            TAG,
            "prepareBackupFile: Created backup file ${backupFile.name} (${backupFile.length().fileSizeToString()})"
        )
        backupFile
    }

    private fun addFile(writer: BackupTarWriter, file: File, entryName: String) {
        writer.addFile(entryName, file)
        Log.d(TAG, "addFile: Added $entryName (${file.length()} bytes) to backup")
    }

    private fun addDirectory(
        writer: BackupTarWriter,
        rootDir: File,
        currentDir: File,
        entryPrefix: String,
    ) {
        currentDir.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                addDirectory(
                    writer = writer,
                    rootDir = rootDir,
                    currentDir = file,
                    entryPrefix = entryPrefix,
                )
            } else if (file.isFile) {
                val relativePath = file.relativeTo(rootDir).invariantSeparatorsPath
                addFile(writer, file, "$entryPrefix$relativePath")
            }
        }
    }

    private suspend fun restoreFromBackupFile(backupFile: File, config: S3Config) = withContext(Dispatchers.IO) {
        Log.i(TAG, "restoreFromBackupFile: Starting restore from ${backupFile.absolutePath}")

        openBackupReader(backupFile).use { reader ->
            while (true) {
                val entry = reader.nextEntry() ?: break
                Log.i(TAG, "restoreFromBackupFile: Processing entry ${entry.name}")

                when (entry.name) {
                    "settings.json" -> {
                        val settingsJson = entry.readBytes().toString(Charsets.UTF_8)
                        Log.i(TAG, "restoreFromBackupFile: Restoring settings")
                        try {
                            val migratedJson = SettingsJsonMigrator.migrate(settingsJson)
                            val settings = json.decodeFromString<Settings>(migratedJson)
                            val currentSettings = settingsStore.settingsFlow.value
                            val defaults = loadDefaultSubagentPrompts(context.assets)
                            val mergedSubagentPrompts = mergeSubagentPrompts(
                                backup = settings.subagentPrompts,
                                current = currentSettings.subagentPrompts,
                                defaults = defaults,
                            )
                            settingsStore.update(settings.copy(subagentPrompts = mergedSubagentPrompts))
                            Log.i(TAG, "restoreFromBackupFile: Settings restored successfully")
                        } catch (e: Exception) {
                            Log.e(TAG, "restoreFromBackupFile: Failed to restore settings", e)
                            throw Exception("Failed to restore settings: ${e.message}")
                        }
                    }

                    "codex_accounts.json" -> {
                        val accountsJson = entry.readBytes().toString(Charsets.UTF_8)
                        try {
                            val imported = json.decodeFromString<CodexAccountState>(accountsJson)
                            codexAccountRepository.importState(imported)
                            Log.i(TAG, "restoreFromBackupFile: Codex accounts restored (${imported.accounts.size})")
                        } catch (e: Exception) {
                            Log.e(TAG, "restoreFromBackupFile: Failed to restore codex accounts", e)
                        }
                    }

                    "rikka_hub.db", "rikka_hub-wal", "rikka_hub-shm" -> {
                        if (config.items.contains(S3Config.BackupItem.DATABASE)) {
                            val dbFile = when (entry.name) {
                                "rikka_hub.db" -> context.getDatabasePath("rikka_hub")
                                "rikka_hub-wal" -> File(
                                    context.getDatabasePath("rikka_hub").parentFile,
                                    "rikka_hub-wal"
                                )

                                "rikka_hub-shm" -> File(
                                    context.getDatabasePath("rikka_hub").parentFile,
                                    "rikka_hub-shm"
                                )

                                else -> null
                            }

                            dbFile?.let { targetFile ->
                                Log.i(
                                    TAG,
                                    "restoreFromBackupFile: Restoring ${entry.name} to ${targetFile.absolutePath}"
                                )
                                targetFile.parentFile?.mkdirs()
                                FileOutputStream(targetFile).use { outputStream ->
                                    entry.copyTo(outputStream)
                                }
                                Log.i(
                                    TAG,
                                    "restoreFromBackupFile: Restored ${entry.name} (${targetFile.length()} bytes)"
                                )
                            }
                        }
                    }

                    else -> {
                        if (config.items.contains(S3Config.BackupItem.FILES) &&
                            entry.name.startsWith("${FileFolders.UPLOAD}/")
                        ) {
                            val fileName = entry.name.substringAfter("${FileFolders.UPLOAD}/")
                            if (fileName.isNotEmpty()) {
                                val uploadFolder = File(context.filesDir, FileFolders.UPLOAD)
                                if (!uploadFolder.exists()) {
                                    uploadFolder.mkdirs()
                                    Log.i(TAG, "restoreFromBackupFile: Created upload directory")
                                }

                                val targetFile = File(uploadFolder, fileName)
                                Log.i(
                                    TAG,
                                    "restoreFromBackupFile: Restoring file ${entry.name} to ${targetFile.absolutePath}"
                                )

                                try {
                                    FileOutputStream(targetFile).use { outputStream ->
                                        entry.copyTo(outputStream)
                                    }
                                    Log.i(
                                        TAG,
                                        "restoreFromBackupFile: Restored ${entry.name} (${targetFile.length()} bytes)"
                                    )
                                } catch (e: Exception) {
                                    Log.e(TAG, "restoreFromBackupFile: Failed to restore file ${entry.name}", e)
                                    throw Exception("Failed to restore file ${entry.name}: ${e.message}")
                                }
                            }
                        } else if (config.items.contains(S3Config.BackupItem.FILES) &&
                            entry.name.startsWith("${FileFolders.SKILLS}/")
                        ) {
                            restoreSkillEntry(entry, entry.name)
                        } else if (config.items.contains(S3Config.BackupItem.FILES) &&
                            entry.name.startsWith("${FileFolders.FONTS}/")
                        ) {
                            val fileName = entry.name.substringAfter("${FileFolders.FONTS}/")
                            if (fileName.isNotEmpty() && !fileName.contains('/')) {
                                val fontsFolder = File(context.filesDir, FileFolders.FONTS).apply { mkdirs() }
                                val targetFile = File(fontsFolder, fileName)
                                FileOutputStream(targetFile).use { outputStream ->
                                    entry.copyTo(outputStream)
                                }
                                Log.i(
                                    TAG,
                                    "restoreFromBackupFile: Restored ${entry.name} (${targetFile.length()} bytes)"
                                )
                            }
                        } else {
                            Log.i(TAG, "restoreFromBackupFile: Skipping entry ${entry.name}")
                        }
                    }
                }
            }
        }

        Log.i(TAG, "restoreFromBackupFile: Restore completed successfully")
    }

    private fun restoreSkillEntry(entry: BackupEntryStream, entryName: String) {
        val relativePath = entryName.substringAfter("${FileFolders.SKILLS}/")
        val skillName = relativePath.substringBefore('/', missingDelimiterValue = "")
        val skillRelativePath = relativePath.substringAfter('/', missingDelimiterValue = "")

        if (skillName.isBlank() || skillRelativePath.isBlank()) {
            Log.w(TAG, "restoreFromBackupFile: Invalid skill entry $entryName")
            return
        }

        val skillsRoot = File(context.filesDir, FileFolders.SKILLS).apply { mkdirs() }
        val skillDir = SkillPaths.resolveSkillDir(skillsRoot, skillName)
            ?: throw Exception("Invalid skill directory: $entryName")
        val targetFile = SkillPaths.resolveSkillFile(skillDir, skillRelativePath)
            ?: throw Exception("Invalid skill file path: $entryName")

        skillDir.mkdirs()
        targetFile.parentFile?.mkdirs()

        try {
            FileOutputStream(targetFile).use { outputStream ->
                entry.copyTo(outputStream)
            }
            Log.i(TAG, "restoreFromBackupFile: Restored skill file $entryName (${targetFile.length()} bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "restoreFromBackupFile: Failed to restore skill file $entryName", e)
            throw Exception("Failed to restore skill file $entryName: ${e.message}")
        }
    }
}

data class S3BackupItem(
    val key: String,
    val displayName: String,
    val size: Long,
    val lastModified: Instant,
)
