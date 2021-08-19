package mynameisjeff.skyblockclientupdater.utils

import com.google.gson.JsonArray
import com.google.gson.JsonParser
import mynameisjeff.skyblockclientupdater.SkyClientUpdater
import mynameisjeff.skyblockclientupdater.SkyClientUpdater.mc
import mynameisjeff.skyblockclientupdater.gui.PromptUpdateScreen
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.util.Util
import net.minecraftforge.client.event.GuiOpenEvent
import net.minecraftforge.fml.common.eventhandler.EventPriority
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import org.apache.commons.lang3.StringUtils
import org.apache.http.HttpVersion
import org.apache.http.client.methods.HttpGet
import java.awt.Desktop
import java.io.File
import java.io.IOException
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

        val subModDir = File(modDir, "1.8.9")
        if (subModDir.isDirectory) {
            val versionModFiles = subModDir.listFiles()
            if (versionModFiles != null) modFiles.addAll(versionModFiles)
        }
        installedMods.addAll(modFiles.filter { it.isFile && it.extension == "jar" })
    }

    fun getLatestMods() {
        var mods = JsonArray()
        try {
            mods = JsonParser().parse(WebUtils.fetchResponse("https://cdn.jsdelivr.net/gh/nacrt/SkyblockClient-REPO@$latestCommitID/files/mods.json")).asJsonArray
        }
        catch (ex: Throwable) {
            println("Failed to load mod files")
            ex.printStackTrace()
        }
        
        for (m in mods) {
            val mod = m.asJsonObject
            val name = mod.get("file").asString
            if (name == "no") continue
            val url = if (mod.has("url")) mod.get("url").asString else "https://github.com/nacrt/SkyblockClient-REPO/raw/main/files/mods/$name"
            latestMods[name] = url
        }
    }

    fun getUpdateCandidates() {
        val needsChecking = installedMods.filter { !latestMods.keys.contains(it.name) }
        val allowedRemoteChecks = latestMods.keys.filter { installedMods.none { m -> m.name == it } }
        loopMods@ for (modFile in needsChecking) {
            val fileName = modFile.name
            for (modEntry in allowedRemoteChecks) {
                if (!checkMatch(modEntry, fileName)) continue
                needsUpdate.add(Triple(modFile, modEntry, latestMods[modEntry]!!))
                continue@loopMods
            }
        }
    }

    private fun checkMatch(expected: String, received: String): Boolean {
        val exempt = charArrayOf('_', '-', '+', ' ', '.')
        val whitespace = charArrayOf('_', ' ', '.', '+')

        val e = expected.toCharArray().dropWhile { it == '!' }.filter { !exempt.contains(it) }
        val r = received.toCharArray().dropWhile { it == '!' }.filter { !exempt.contains(it) }
        val distance = StringUtils.getLevenshteinDistance(e.joinToString(), r.joinToString())
        if (distance !in 1..7) return false

        val ec = e.filterIndexed { index, c -> c != r.getOrNull(index) }
        val rc = r.filterIndexed { index, c -> c != e.getOrNull(index) }

        if (listOf(ec, rc).flatten().none { !it.isDigit() && !whitespace.contains(it) }) {
            return (ec.firstOrNull { it.isDigit() }?.digitToInt() ?: 0) > (rc.firstOrNull { it.isDigit() }?.digitToInt() ?: 0)
        }
        return true
    }

    fun downloadHelperTask() {
        println("Checking for SkyClientUpdater delete task...")
        val taskDir = File(File(mc.mcDataDir, "skyclientupdater"), "files")
        val url =
            "https://cdn.discordapp.com/attachments/807303259902705685/864882597342740511/SkytilsInstaller-1.1-SNAPSHOT.jar"
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
