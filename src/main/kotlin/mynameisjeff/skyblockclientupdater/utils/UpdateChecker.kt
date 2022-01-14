package mynameisjeff.skyblockclientupdater.utils

import com.google.gson.JsonParser
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.decodeFromStream
import mynameisjeff.skyblockclientupdater.SkyClientUpdater
import mynameisjeff.skyblockclientupdater.SkyClientUpdater.json
import mynameisjeff.skyblockclientupdater.SkyClientUpdater.mc
import mynameisjeff.skyblockclientupdater.data.LocalMod
import mynameisjeff.skyblockclientupdater.data.MCMod
import mynameisjeff.skyblockclientupdater.data.RepoMod
import mynameisjeff.skyblockclientupdater.gui.PromptUpdateScreen
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.util.Util
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.fml.client.FMLClientHandler
import net.minecraftforge.fml.common.Loader
import net.minecraftforge.fml.common.ModContainer
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpVersion
import org.apache.http.client.methods.HttpGet
import java.awt.Desktop
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.jar.JarFile
import kotlin.concurrent.thread

/**
 * Taken from Skytils under GNU Affero General Public License v3.0
 * Modified
 * https://github.com/Skytils/SkytilsMod/blob/1.x/LICENSE
 */
object UpdateChecker {

    val installedMods = arrayListOf<LocalMod>()
    val latestMods = hashSetOf<RepoMod>()
    val needsUpdate = hashSetOf<Triple<File, String, String>>()

    val needsDelete = hashSetOf<Pair<File, String>>()

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
        latestCommitID = try {
            val commits = JsonParser().parse(WebUtils.fetchResponse("https://api.github.com/repos/nacrt/SkyblockClient-REPO/commits")).asJsonArray
            commits[0].asJsonObject["sha"].asString
        } catch (ex: Throwable) {
            println("Failed to load latest commit id")
            ex.printStackTrace()
            "main"
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
                        newJar.copyTo(newLocation, true)
                        newJar.delete()
                    }
                    val os = Util.getOSType()
                    if ((os == Util.EnumOS.OSX || os == Util.EnumOS.LINUX) && needsDelete.removeAll { it.first.delete() } && needsDelete.isEmpty()) {
                        println("Successfully deleted all files normally.")
                        return@Thread
                    }
                    println("Running delete task")
                    if (deleteTask.path == "invalid") {
                        println("Task doesn't exist")
                        Desktop.getDesktop().open(File(mc.mcDataDir, "mods"))
                        return@Thread
                    }
                    val runtime = getJavaRuntime()
                    println("Using runtime $runtime")
                    if (os == Util.EnumOS.OSX) {
                        val sipStatus = Runtime.getRuntime().exec("csrutil status")
                        sipStatus.waitFor()
                        if (!sipStatus.inputStream.readTextAndClose().contains("System Integrity Protection status: disabled.")) {
                            println("SIP is NOT disabled, opening Finder.")
                            Desktop.getDesktop().open(File(mc.mcDataDir, "mods"))
                            return@Thread
                        }
                    }
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

        val subModDir = File(modDir, Loader.MC_VERSION)
        if (subModDir.isDirectory) {
            val versionModFiles = subModDir.listFiles()
            if (versionModFiles != null) modFiles.addAll(versionModFiles)
        }
        val modList = ArrayList(Loader.instance().modList)
        FMLClientHandler.instance().addSpecialModEntries(modList)
        installedMods.addAll(modFiles.filter { it.isFile && it.extension == "jar" }.map {
            LocalMod(it, getModIds(modList, it))
        })
    }

    @OptIn(ExperimentalSerializationApi::class)
    private fun getModIds(modList: List<ModContainer>, file: File): Set<String> {
        val list = hashSetOf<String>()
        runCatching {
            modList.filter { it.source == file }.mapTo(list) { it.modId }
            JarFile(file).use { jarFile ->
                val mcModInfo = json.decodeFromStream<List<MCMod>>(jarFile.getInputStream(jarFile.getJarEntry("mcmod.info") ?: return@runCatching null) ?: return@runCatching null)
                mcModInfo.mapTo(list) { it.modId }
            }
        }.onFailure { it.printStackTrace() }
        return list
    }

    fun getLatestMods() {
        try {
            latestMods.addAll(json.decodeFromString<List<RepoMod>>(WebUtils.fetchResponse("https://cdn.jsdelivr.net/gh/nacrt/SkyblockClient-REPO@$latestCommitID/files/mods.json")).filter { !it.ignored })
        } catch (ex: Throwable) {
            println("Failed to load mod files")
            ex.printStackTrace()
        }
    }

    fun getUpdateCandidates() {
        val needsChecking = installedMods.filter { latestMods.none { m -> m.fileName == it.file.name } }
        val allowedRemoteChecks = latestMods.filter { installedMods.none { m -> m.file.name == it.fileName } }
        loopMods@ for (localMod in needsChecking) {
            val fileName = localMod.file.name
            for (repoMod in allowedRemoteChecks) {
                if (!checkModId(localMod, repoMod) || !checkMatch(repoMod.fileName, fileName)) continue
                needsUpdate.add(Triple(localMod.file, repoMod.fileName, repoMod.updateURL))
                continue@loopMods
            }
        }
    }

    private fun checkModId(localMod: LocalMod, repoMod: RepoMod): Boolean {
        if (repoMod.alwaysConsider) return true
        val localEmpty = localMod.modIds.isEmpty()
        if (localEmpty && repoMod.modId == null) return true
        if (!localEmpty && repoMod.modId == null) return false

        // some mods have invalid mcmod files
        if (localEmpty && repoMod.hasBrokenMCModInfo) return true

        return localMod.modIds.contains(repoMod.modId)
    }

    private fun checkMatch(expected: String, received: String): Boolean {
        val exempt = charArrayOf('_', '-', '+', ' ', '.')
        val whitespace = charArrayOf('_', ' ', '.', '+')

        val e = expected.lowercase().toCharArray().dropWhile { it == '!' }.filter { !exempt.contains(it) }
        val r = received.lowercase().toCharArray().dropWhile { it == '!' }.filter { !exempt.contains(it) }
        if (e.joinToString().take(4) != r.joinToString().take(4)) return false
        val distance = StringUtils.getLevenshteinDistance(e.joinToString(""), r.joinToString(""))
        if (distance !in 1..7) return false

        val ec = e.filterIndexed { index, c -> c != r.getOrNull(index) }
        val rc = r.filterIndexed { index, c -> c != e.getOrNull(index) }

        if (listOf(ec, rc).flatten().all { it.isDigit() || whitespace.contains(it) }) {
            val ed = ec.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }.joinToString("").toIntOrNull() ?: 0
            val rd = rc.dropWhile { !it.isDigit() }.takeWhile { it.isDigit() }.joinToString("").toIntOrNull() ?: 0
            return ed > rd
        }
        return true
    }

    fun downloadHelperTask() {
        println("Checking for SkyClientUpdater delete task...")
        val taskDir = File(File(mc.mcDataDir, "skyclientupdater"), "files")
        val url =
            "https://github.com/Skytils/SkytilsMod-Data/releases/download/files/SkytilsInstaller-1.1.1.jar"
        taskDir.mkdirs()
        val taskFile = File(taskDir, url.substringAfterLast("/"))
        if (!taskFile.exists()) {
            thread(name = "Download SkyclientUpdater delete task") {
                println("Downloading SkyClientUpdater delete task.")
                WebUtils.builder.build().use {
                    val req = HttpGet(URL(url).toURI())
                    req.protocolVersion = HttpVersion.HTTP_1_1
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
            deleteTask = taskFile
            println("SkyClientUpdater delete task found")
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
