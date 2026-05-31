package com.accu.data.repositories

import com.accu.data.db.dao.SavedScriptDao
import com.accu.data.db.dao.ShellCommandDao
import com.accu.data.db.entities.SavedScriptEntity
import com.accu.data.db.entities.ShellCommandEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ShellRepository @Inject constructor(
    private val shellCommandDao: ShellCommandDao,
    private val savedScriptDao: SavedScriptDao,
) {
    fun observeCommands(): Flow<List<ShellCommandEntity>> = shellCommandDao.observeAll()
    fun observeFavorites(): Flow<List<ShellCommandEntity>> = shellCommandDao.observeFavorites()
    fun observePinned(): Flow<List<ShellCommandEntity>> = shellCommandDao.observePinned()
    fun observeTopUsed(): Flow<List<ShellCommandEntity>> = shellCommandDao.topUsed()
    fun searchCommands(q: String): Flow<List<ShellCommandEntity>> = shellCommandDao.search(q)
    suspend fun saveCommand(cmd: ShellCommandEntity): Long = shellCommandDao.insert(cmd)
    suspend fun updateCommand(cmd: ShellCommandEntity) = shellCommandDao.update(cmd)
    suspend fun deleteCommand(cmd: ShellCommandEntity) = shellCommandDao.delete(cmd)
    suspend fun getCommandCount(): Int = shellCommandDao.count()

    fun observeScripts(): Flow<List<SavedScriptEntity>> = savedScriptDao.observeAll()
    fun searchScripts(q: String): Flow<List<SavedScriptEntity>> = savedScriptDao.search(q)
    suspend fun saveScript(script: SavedScriptEntity): Long = savedScriptDao.insert(script)
    suspend fun updateScript(script: SavedScriptEntity) = savedScriptDao.update(script)
    suspend fun deleteScript(script: SavedScriptEntity) = savedScriptDao.delete(script)
    suspend fun incrementScriptRunCount(id: Long) = savedScriptDao.incrementRunCount(id)

    suspend fun execute(cmd: String): String = withContext(Dispatchers.IO) {
        try {
            val p = ProcessBuilder("sh", "-c", cmd).redirectErrorStream(true).start()
            val output = p.inputStream.bufferedReader().readText()
            p.waitFor()
            output
        } catch (_: Exception) { "" }
    }
}
