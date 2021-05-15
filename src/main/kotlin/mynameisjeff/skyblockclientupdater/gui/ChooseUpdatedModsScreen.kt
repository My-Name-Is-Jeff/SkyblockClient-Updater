package mynameisjeff.skyblockclientupdater.gui

import mynameisjeff.skyblockclientupdater.gui.elements.CleanButton
import mynameisjeff.skyblockclientupdater.utils.UpdateChecker
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.GuiButton
import net.minecraft.client.gui.GuiMainMenu
import net.minecraft.client.gui.GuiScreen
import net.minecraft.client.renderer.Tessellator
import net.minecraftforge.fml.client.GuiScrollingList
import java.awt.Color

/**
 * Adopted from ChatShortcuts under MIT License
 * https://github.com/P0keDev/ChatShortcuts/blob/master/LICENSE
 * @author P0keDev
 */
class ChooseUpdatedModsScreen : GuiScreen() {
    private lateinit var list: ChooseModList
    private var id = 0
    override fun initGui() {
        id = 0
        list = ChooseModList(mc, width, height - 80, 20, height - 60, 0, 25, width, height)
        buttonList.clear()
        for (item in UpdateChecker.needsUpdate) {
            addEntry(item.first.name, item.second)
        }
        buttonList.add(CleanButton(9000, width / 2 - 200, height - 40, "Update Mods"))
        buttonList.add(CleanButton(9001, width / 2 + 20, height - 40, "Main Menu"))
    }

    override fun actionPerformed(button: GuiButton) {
        if (button.id < 1000) {
            list.entries[button.id].needsUpdate = !list.entries[button.id].needsUpdate
        }
        if (button.id == 9000) {
            val list = list.entries.filter { it.needsUpdate }.map { UpdateChecker.needsUpdate.find { e -> e.second == it.newFile }!! }
            if (list.isNotEmpty()) mc.displayGuiScreen(UpdateScreen(list))
        }
        if (button.id == 9001) {
            mc.displayGuiScreen(GuiMainMenu())
        }
    }

    override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
        drawGradientRect(0, 0, width, height, Color(117, 115, 115, 25).rgb, Color(0, 0, 0, 200).rgb)
        list.drawScreen(mouseX, mouseY, partialTicks)
        super.drawScreen(mouseX, mouseY, partialTicks)
    }

    override fun updateScreen() {
        super.updateScreen()
        list.updateScreen()
    }

    override fun doesGuiPauseGame(): Boolean {
        return false
    }

    private fun addEntry(oldFile: String, newFile: String) {
        val toggleButton: GuiButton = CleanButton(id, width / 2 - 220, 0, 50, 20, "Update")
        buttonList.add(toggleButton)
        list.addEntry(id, oldFile, newFile, toggleButton)
        id++
    }


    class ChooseModList(
        private val mc: Minecraft, width: Int, height: Int, top: Int, bottom: Int, left: Int, entryHeight: Int,
        screenWidth: Int, screenHeight: Int
    ) : GuiScrollingList(mc, width, height, top, bottom, left, entryHeight, screenWidth, screenHeight) {
        val entries: ArrayList<ListEntry> = ArrayList()
        fun addEntry(id: Int, oldFile: String, newFile: String, toggleButton: GuiButton) {
            entries.add(ListEntry(id, oldFile, newFile, toggleButton))
        }

        fun updateScreen() {
            for (e in entries) {
                e.toggleButton.displayString = if (e.needsUpdate) "Updating" else "Ignoring"
            }
        }

         private fun resetButtons() {
            for (e in entries) {
                e.toggleButton.visible = false
            }
        }

        override fun getSize(): Int {
            return entries.size
        }

        override fun elementClicked(index: Int, doubleClick: Boolean) {}
        override fun isSelected(index: Int): Boolean {
            return false
        }

        override fun drawBackground() {}
        override fun drawScreen(mouseX: Int, mouseY: Int, partialTicks: Float) {
            resetButtons()
            super.drawScreen(mouseX, mouseY, partialTicks)
        }

        override fun drawSlot(slotIdx: Int, entryRight: Int, slotTop: Int, slotBuffer: Int, tess: Tessellator) {
            val ks = entries[slotIdx]
            val visible = slotTop >= top && slotTop + slotHeight <= bottom
            ks.toggleButton.visible = visible
            if (visible) {
                ks.toggleButton.yPosition = slotTop
                ks.toggleButton.displayString = if (ks.needsUpdate) "Updating" else "Ignoring"

                mc.fontRendererObj.drawString(
                    "§c${ks.oldFile} §r§l➜ §a${ks.newFile}",
                    ks.toggleButton.xPosition + ks.toggleButton.width + 6,
                    slotTop + 5,
                    0xFFFFFF
                )
            }
        }

        override fun drawGradientRect(left: Int, top: Int, right: Int, bottom: Int, color1: Int, color2: Int) {}
        class ListEntry(
            val id: Int,
            val oldFile: String,
            val newFile: String,
            val toggleButton: GuiButton,
            var needsUpdate: Boolean = true
        )
    }
}
