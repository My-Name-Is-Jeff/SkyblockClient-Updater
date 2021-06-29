package mynameisjeff.skyblockclientupdater

import mynameisjeff.skyblockclientupdater.utils.UpdateChecker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiScreen
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.Mod
import net.minecraftforge.fml.common.ProgressManager
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent

@Mod(modid = "skyblockclientupdater", name = "SkyClient Updater", version = SkyClientUpdater.VERSION, clientSideOnly = true, modLanguage = "kotlin", modLanguageAdapter = "mynameisjeff.skyblockclientupdater.utils.kotlin.KotlinAdapter")
object SkyClientUpdater {

    const val VERSION = "1.0.6"

    @JvmField
    val mc = Minecraft.getMinecraft()

    var displayScreen: GuiScreen? = null

    @Mod.EventHandler
    fun on(event: FMLPreInitializationEvent) {
        MinecraftForge.EVENT_BUS.register(this)
        MinecraftForge.EVENT_BUS.register(UpdateChecker)

        val progress = ProgressManager.push("SkyClient Updater", 5)
        progress.step("Downloading helper utility")
        UpdateChecker.downloadHelperTask()
        progress.step("Discovering mods")
        UpdateChecker.getValidModFiles()
        progress.step("Fetching latest commit ID")
        UpdateChecker.getLatestCommitID()
        progress.step("Fetching latest versions")
        UpdateChecker.getLatestMods()
        progress.step("Comparing versions")
        UpdateChecker.getUpdateCandidates()
        ProgressManager.pop(progress)
    }

    @SubscribeEvent
    fun on(event: TickEvent.ClientTickEvent) {
        if (event.phase != TickEvent.Phase.START) return
        if (displayScreen != null) {
            mc.displayGuiScreen(displayScreen)
            displayScreen = null
        }
    }

}
