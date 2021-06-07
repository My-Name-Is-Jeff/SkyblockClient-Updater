package mynameisjeff.skyblockclientupdater.utils

import com.google.gson.JsonParser
import mynameisjeff.skyblockclientupdater.SkyClientUpdater
import mynameisjeff.skyblockclientupdater.SkyClientUpdater.mc
import mynameisjeff.skyblockclientupdater.gui.PromptUpdateScreen
import net.minecraft.client.gui.GuiMainMenu
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.commons.lang3.StringUtils.getLevenshteinDistance
import org.apache.http.HttpVersion
import org.apache.http.client.methods.HttpGet
import java.io.*
import java.net.URL
import kotlin.concurrent.thread

/**
 * Taken from Skytils under GNU Affero General Public License v3.0
 * Modified
 * https://github.com/Skytils/SkytilsMod/blob/1.x/LICENSE
 */
object UpdateChecker {

    val installedMods: ArrayList<File> = arrayListOf()
    val latestMods = HashMap<String, String>()
    val needsUpdate = HashSet<Triple<File, String, String>>()

    val needsDelete = HashSet<Pair<File, String>>()

    var latestCommitID: String = "main"

    private var addedShutdownHook = false

    lateinit var deleteTask: File

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    fun on(event: GuiOpenEvent) {
        if (event.gui !is GuiMainMenu) return
        if (needsUpdate.isEmpty()) return

        TickTask(2) {
            SkyClientUpdater.displayScreen = PromptUpdateScreen()
        }
    }

    fun getLatestCommitID() {
        try {
            val commits = JsonParser().parse(WebUtils.fetchResponse("https://api.github.com/repos/nacrt/SkyblockClient-REPO/commits")).asJsonArray
            latestCommitID = commits[0].asJsonObject["sha"].asString
        }
        catch (ex: Throwable) {
            println("Failed to load latest commit id")
            ex.printStackTrace()
            latestCommitID = "main"
        }
    }

    fun deleteFileOnShutdown(oldFile: File, newFile: String) {
        if (!addedShutdownHook) {
            addedShutdownHook = true
            Runtime.getRuntime().addShutdownHook(Thread {
                try {
                    println("Attempting to apply SkyClient updates.")
                    println("Copying updated jars to mods.")
                    val directory = File(File(mc.mcDataDir, "skyclientupdater"), "updates")
                    val modDir = File(mc.mcDataDir, "mods")
                    for (item in needsDelete) {
                        val newJar = File(directory, item.second)
                        println("Copying ${item.second} to mod folder")
                        val newLocation = File(modDir, item.second)
                        newLocation.createNewFile()
                        copyFile(newJar, newLocation)
                        newJar.delete()
                    }
                    println("Running delete task")
                    if (deleteTask.path == "invalid") {
                        println("Task doesn't exist")
                        return@Thread
                    }
                    val runtime = getJavaRuntime()
                    println("Using runtime $runtime")
                    Runtime.getRuntime().exec("\"$runtime\" -jar \"${deleteTask.absolutePath}\" ${needsDelete.joinToString(" ") {"\"${it.first.absolutePath}\""}}")
                    println("Successfully applied SkyClient mod update.")
                } catch (ex: Throwable) {
                    println("Failed to apply SkyClient mod Update.")
                    ex.printStackTrace()
                }
            })
        }
        needsDelete.add(oldFile to newFile)
    }

    fun getValidModFiles() {
        val modDir = File(mc.mcDataDir, "mods")
        if (!modDir.isDirectory && !modDir.mkdirs()) {
            println("Mods directory not found (bug).")
            return
        }
        val modFiles = (modDir.listFiles() ?: return).toMutableList()

        val subModDir = File(modDir, "1.8.9")
        if (subModDir.isDirectory) {
            val versionModFiles = subModDir.listFiles()
            if (versionModFiles != null) modFiles.addAll(versionModFiles)
        }
        installedMods.addAll(modFiles.filter { it.isFile && it.extension == "jar" })
    }

    fun getLatestMods() {
        val mods = JsonParser().parse(WebUtils.fetchResponse("https://rawcdn.githack.com/nacrt/SkyblockClient-REPO/$latestCommitID/files/mods.json")).asJsonArray
        for (m in mods) {
            val mod = m.asJsonObject
            val name = mod.get("file").asString
            if (name == "no") continue
            val url = if (mod.has("url")) mod.get("url").asString else "https://github.com/nacrt/SkyblockClient-REPO/raw/main/files/mods/$name"
            latestMods[name] = url
        }
    }

    fun getUpdateCandidates() {
        loopMods@ for (modFile in installedMods) {
            val fileName = modFile.name
            for (modEntry in latestMods.keys) {
                val distance = getLevenshteinDistance(fileName, modEntry)
                if (distance !in 1..6) continue
                needsUpdate.add(Triple(modFile, modEntry, latestMods[modEntry]!!))
                continue@loopMods
            }
        }
    }

    fun downloadHelperTask() {
        println("Checking for SkyClientUpdater delete task...")
        val taskDir = File(File(mc.mcDataDir, "skyclientupdater"), "files")
        val url =
            "https://cdn.discordapp.com/attachments/807303259902705685/841080571731640401/SkytilsInstaller-1.0-SNAPSHOT.jar"
        val taskFileName = getJarNameFromUrl(url)
        taskDir.mkdirs()
        val existingTask = taskDir.listFiles()!!.find { it.name == taskFileName }
        if (existingTask == null) {
            thread(name = "Download SkyclientUpdater delete task") {
                println("Downloading SkyClientUpdater delete task.")
                WebUtils.builder.build().use {
                    val req = HttpGet(URL(url).toURI())
                    req.protocolVersion = HttpVersion.HTTP_1_1
                    val taskFile = File(taskDir, taskFileName)
                    taskFile.createNewFile()
                    val res = it.execute(req)
                    if (res.statusLine.statusCode != 200) {
                        println("Downloading SkyClientUpdater delete task failed!")
                        deleteTask = File("invalid")
                    } else {
                        println("Writing SkyClientUpdater delete task.")
                        res.entity.writeTo(taskFile.outputStream())
                        deleteTask = taskFile
                        println("SkyClientUpdater delete task successfully downloaded!")
                    }
                }
            }
        } else {
            deleteTask = existingTask
            println("SkyClientUpdater delete task found")
        }
    }

    fun getJarNameFromUrl(url: String): String {
        val sUrl = url.split("/".toRegex()).toTypedArray()
        return sUrl[sUrl.size - 1]
    }

    private fun copyFile(sourceFile: File, destFile: File) {
        if (!destFile.exists()) {
            sourceFile.renameTo(destFile)
            return
        }
        var source: InputStream? = null
        var dest: OutputStream? = null
        try {
            source = FileInputStream(sourceFile)
            dest = FileOutputStream(destFile)
            val buffer = ByteArray(1024)
            var length: Int
            while (source.read(buffer).also { length = it } > 0) {
                dest.write(buffer, 0, length)
            }
        } finally {
            source!!.close()
            dest!!.close()
        }
    }

    /**
     * @link https://stackoverflow.com/a/47925649
     */
    @Throws(IOException::class)
    fun getJavaRuntime(): String {
        val os = System.getProperty("os.name")
        val java = "${System.getProperty("java.home")}${File.separator}bin${File.separator}${
            if (os != null && os.lowercase().startsWith("windows")) "java.exe" else "java"
        }"
        if (!File(java).isFile) {
            throw IOException("Unable to find suitable java runtime at $java")
        }
        return java
    }
}
