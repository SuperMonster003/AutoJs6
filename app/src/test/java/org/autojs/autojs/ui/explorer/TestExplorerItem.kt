package org.autojs.autojs.ui.explorer

import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.model.explorer.ExplorerPage
import org.autojs.autojs.model.script.ScriptFile
import org.autojs.autojs.util.FileUtils

/** JVM-only Explorer item fixture that never reaches Android filesystem APIs. */
internal class TestExplorerItem(
    private val itemPath: String,
    private val itemType: FileUtils.TYPE = FileUtils.TYPE.JAVASCRIPT,
) : ExplorerItem {

    override fun getName(): String = itemPath.substringAfterLast('/')

    override fun getParent(): ExplorerPage? = null

    override fun getPath(): String = itemPath

    override fun lastModified(): Long = 0L

    override fun canMove(): Boolean = true

    override fun canRename(): Boolean = true

    override fun canDelete(): Boolean = true

    override fun getType(): FileUtils.TYPE = itemType

    override fun getSize(): Long = 0L

    override fun toScriptFile(): ScriptFile = error("Not required by this JVM fixture")
}
