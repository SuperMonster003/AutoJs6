package org.autojs.autojs.ui.explorer

import org.autojs.autojs.ui.explorer.ExplorerSelectionActionGroup.HOST
import org.autojs.autojs.ui.explorer.ExplorerSelectionActionGroup.PLUGIN
import org.autojs.autojs.ui.explorer.ExplorerSelectionActionLevel.PRIMARY
import org.autojs.autojs.ui.explorer.ExplorerSelectionActionLevel.SECONDARY
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerSelectionActionLayoutTest {

    @Test
    fun secondaryActionsStayUnderMoreEvenWithFreeButtonSlots() {
        val layout = ExplorerSelectionActionLayout.of(
            listOf(
                action("copy", PRIMARY),
                action("copy-path", SECONDARY),
                action("delete", PRIMARY),
            ),
            maxButtons = 4,
        )

        assertEquals(listOf("copy", "delete"), layout.buttons.keys())
        assertEquals(listOf("copy-path"), layout.overflow.keys())
    }

    @Test
    fun primaryActionsBeyondTheButtonSlotsLeadTheMoreList() {
        val layout = ExplorerSelectionActionLayout.of(
            listOf(
                action("copy", PRIMARY),
                action("move", PRIMARY),
                action("plugin:a", PRIMARY, PLUGIN),
                action("delete", PRIMARY),
                action("rename", PRIMARY),
                action("copy-path", SECONDARY),
            ),
            maxButtons = 4,
        )

        assertEquals(listOf("copy", "move", "plugin:a", "delete"), layout.buttons.keys())
        assertEquals(listOf("rename", "copy-path"), layout.overflow.keys())
    }

    @Test
    fun moreListsHostActionsBeforePluginActionsKeepingTheirOrder() {
        val layout = ExplorerSelectionActionLayout.of(
            listOf(
                action("plugin:b", SECONDARY, PLUGIN),
                action("copy-path", SECONDARY),
                action("plugin:c", SECONDARY, PLUGIN),
                action("rename", SECONDARY),
            ),
            maxButtons = 4,
        )

        assertTrue(layout.buttons.isEmpty())
        assertEquals(listOf("copy-path", "rename", "plugin:b", "plugin:c"), layout.overflow.keys())
    }

    @Test(expected = IllegalArgumentException::class)
    fun primaryActionsNeedAnIcon() {
        ExplorerSelectionAction(key = "copy", label = "Copy to", iconRes = null, level = PRIMARY) {}
    }

    private fun action(
        key: String,
        level: ExplorerSelectionActionLevel,
        group: ExplorerSelectionActionGroup = HOST,
    ) = ExplorerSelectionAction(
        key = key,
        label = key,
        iconRes = if (level == PRIMARY) 1 else null,
        level = level,
        group = group,
    ) {}

    private fun List<ExplorerSelectionAction>.keys() = map { it.key }
}
