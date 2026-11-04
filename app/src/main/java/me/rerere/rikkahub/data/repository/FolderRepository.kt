package me.rerere.rikkahub.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.room.withTransaction
import me.rerere.rikkahub.data.db.AppDatabase
import me.rerere.rikkahub.data.db.dao.ConversationDAO
import me.rerere.rikkahub.data.db.dao.FolderDAO
import me.rerere.rikkahub.data.db.entity.FolderEntity
import me.rerere.rikkahub.data.model.Folder
import java.time.Instant
import kotlin.uuid.Uuid

class FolderRepository(
    private val database: AppDatabase,
    private val folderDAO: FolderDAO,
    private val conversationDAO: ConversationDAO,
) {
    fun getFoldersOfAssistant(assistantId: Uuid): Flow<List<Folder>> {
        return folderDAO.getFoldersOfAssistant(assistantId.toString())
            .map { list -> list.map { it.toFolder() } }
    }

    suspend fun createFolder(assistantId: Uuid, name: String): Folder {
        val folder = Folder(
            assistantId = assistantId,
            name = name,
            createAt = Instant.now(),
        )
        folderDAO.insert(folder.toEntity())
        return folder
    }

    suspend fun renameFolder(id: Uuid, name: String) {
        folderDAO.rename(id.toString(), name)
    }

    /**
     * 删除文件夹，先把归属该文件夹的会话 folder_id 清空，再删除文件夹本身（不影响会话）。
     */
    suspend fun deleteFolder(id: Uuid) {
        conversationDAO.clearFolder(id.toString())
        folderDAO.deleteById(id.toString())
    }

    /**
     * 删除助手时清理其全部文件夹。会话此时已删除，清空 folder_id 仅作兜底。
     */
    suspend fun deleteFoldersOfAssistant(assistantId: Uuid) {
        database.withTransaction {
            conversationDAO.clearFoldersOfAssistant(assistantId.toString())
            folderDAO.deleteFoldersOfAssistant(assistantId.toString())
        }
    }
}

private fun FolderEntity.toFolder(): Folder = Folder(
    id = Uuid.parse(id),
    assistantId = Uuid.parse(assistantId),
    name = name,
    sortIndex = sortIndex,
    createAt = Instant.ofEpochMilli(createAt),
)

private fun Folder.toEntity(): FolderEntity = FolderEntity(
    id = id.toString(),
    assistantId = assistantId.toString(),
    name = name,
    sortIndex = sortIndex,
    createAt = createAt.toEpochMilli(),
)
