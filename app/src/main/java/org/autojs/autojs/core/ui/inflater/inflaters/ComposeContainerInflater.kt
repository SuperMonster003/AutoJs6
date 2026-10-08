package org.autojs.autojs.core.ui.inflater.inflaters

import android.content.Context
import android.view.ViewGroup
import org.autojs.autojs.core.ui.inflater.DynamicLayoutInflater
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.ViewCreator
import org.autojs.autojs.core.ui.widget.ComposeContainerView
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.w3c.dom.Node

/** Uses the same creator/attribute pipeline as the other built-in XML widgets. */
class ComposeContainerInflater(private val runtime: ScriptRuntime, resourceParser: ResourceParser) :
    FrameLayoutInflater<ComposeContainerView>(runtime, resourceParser) {
    override fun getCreator(): ViewCreator<in ComposeContainerView> = object : ViewCreator<ComposeContainerView> {
        override fun create(context: Context, attrs: HashMap<String, String>, parent: ViewGroup?) = ComposeContainerView(context, runtime)
    }

    override fun inflateChildren(inflater: DynamicLayoutInflater?, node: Node?, parent: ComposeContainerView?): Boolean {
        if (node != null && (0 until node.childNodes.length).any { node.childNodes.item(it).nodeType == Node.ELEMENT_NODE }) {
            throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT, "A compose container receives content through compose.attach")
        }
        return true
    }
}
