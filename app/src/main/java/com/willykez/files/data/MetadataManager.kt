package com.willykez.files.data

import android.os.Environment
import com.willykez.files.data.model.FileMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import java.io.File

class MetadataManager {

    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    private val folderName = "FileOrganizer"
    private val fileName = "metadata.json"

    fun getMetadataFile(): File {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            folderName
        )
        dir.mkdirs()
        return File(dir, fileName)
    }

    val metadataPath: String get() = getMetadataFile().absolutePath

    fun metadataExists(): Boolean = getMetadataFile().exists()

    suspend fun saveMetadata(files: List<FileMetadata>) = withContext(Dispatchers.IO) {
        runCatching {
            getMetadataFile().writeText(json.encodeToString(files))
        }
    }

    suspend fun loadMetadata(): List<FileMetadata> = withContext(Dispatchers.IO) {
        val file = getMetadataFile()
        if (!file.exists()) return@withContext emptyList()
        runCatching {
            json.decodeFromString<List<FileMetadata>>(file.readText())
        }.getOrDefault(emptyList())
    }
}
