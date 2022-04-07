package mynameisjeff.skyblockclientupdater.gui

import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiScreen
import java.awt.Color
import java.io.File

class UpdateSummaryScreen(val downloadedMods: ArrayList<File>, val failedDownloadingMods: ArrayList<File>) : GuiScreen() {

    override fun initGui() {
        buttonList.add(GuiButton(0, width / 2 - 100, height - 75, 50, 20, "Quit"))
        buttonList.add(GuiButton(1, width / 2 - 50, height - 75, 150, 20, "Continue to MC"))
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawDefaultBackground()

        drawCenteredString(fontRendererObj, "${downloadedMods.size + failedDownloadingMods.size} updates requested", width / 2, 20, 0xFFFFFF)
        drawCenteredString(fontRendererObj, "${downloadedMods.size} updates successful", width / 4, 30, Color.GREEN.rgb)
        drawCenteredString(fontRendererObj, "${failedDownloadingMods.size} updates failed", 3 * (width / 4), 30, Color.RED.rgb)

        for (i in 0 until downloadedMods.size) drawCenteredString(fontRendererObj,
            downloadedMods[i].name, width / 4, 30 + i.inc() * fontRendererObj.FONT_HEIGHT, Color.GREEN.rgb)
        for (i in 0 until failedDownloadingMods.size) drawCenteredString(fontRendererObj,
            failedDownloadingMods[i].name, 3 * (width / 4), 30 + i.inc() * fontRendererObj.FONT_HEIGHT, Color.RED.rgb)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id == 0) {
            mc.shutdown()
        } else if (button.id == 1) {
            mc.displayGuiScreen(GuiMainMenu())
        }
    }
}