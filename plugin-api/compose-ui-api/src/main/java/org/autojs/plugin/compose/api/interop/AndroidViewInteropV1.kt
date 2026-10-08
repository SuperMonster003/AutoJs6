package org.autojs.plugin.compose.api.interop

import android.view.View
import org.autojs.plugin.compose.api.ComposeUiComponents
import org.autojs.plugin.compose.api.ComposeUiContractException
import org.autojs.plugin.compose.api.ComposeUiErrorCodes
import org.autojs.plugin.compose.api.catalog.ChildrenPolicy
import org.autojs.plugin.compose.api.catalog.ComponentCatalog
import org.autojs.plugin.compose.api.catalog.ComponentSpec
import org.autojs.plugin.compose.api.catalog.PropSpec
import org.autojs.plugin.compose.api.catalog.ValueType
import org.autojs.plugin.compose.api.loading.ComposeUiHostEnvironment
import org.autojs.plugin.compose.api.loading.ComposeUiRenderer
import org.autojs.plugin.compose.api.model.ScopeKind
import org.autojs.plugin.compose.api.model.UiPatchBatch
import org.autojs.plugin.compose.api.model.ValueKind

/**
 * Optional, in-process interoperability protocol. The frozen V1 catalog, value kinds and Parcel
 * codec are unchanged. The base factory advertises only its V1 component subset and offers this
 * extension through FEATURES plus a string factory class name; old hosts never load these types.
 */
object AndroidViewInteropV1 {
    const val FEATURE = "android-view-interop-v1"
    const val FACTORY_CLASS_KEY = "androidViewFactoryV1"
    const val COMPONENT = "AndroidView"
    const val PROP_VIEW = "view"
    const val VERSION = 1

    /** The script accepts a View or (Context) -> View; the wire carries its prepared positive lease. */
    @JvmField val component = ComponentSpec(
        COMPONENT,
        props = listOf(PropSpec(PROP_VIEW, ValueType(setOf(ValueKind.NUMBER), minimum = 1.0,
            maximum = Int.MAX_VALUE.toDouble(), integral = true), required = true)),
        events = ComponentCatalog.V1.requireComponent(ComposeUiComponents.BOX).events,
        childrenPolicy = ChildrenPolicy.NONE,
        scope = ScopeKind.ANY,
    )

    /** Select explicitly after negotiation; never substitute this object for ComponentCatalog.V1. */
    @JvmField val catalog = ComponentCatalog(
        ComponentCatalog.V1.components + component, ComponentCatalog.V1.modifiers,
        ComponentCatalog.V1.shortcuts, ComponentCatalog.V1.systemEvents,
    )
}

/** Loaded by its advertised class-name string only after the feature and this interface agree. */
interface AndroidViewRendererFactoryV1 {
    fun extensionVersion(): Int
    fun create(environment: ComposeUiHostEnvironment): AndroidViewRendererV1
}

interface AndroidViewRendererV1 : ComposeUiRenderer {
    /**
     * Main thread. [bindings] is the complete target tree's AndroidView binding snapshot, not a
     * delta. Validate the candidate tree, tokens, identities, parents and ownership before any
     * publication. A failed call retains the old tree, generation, bindings and borrowed children.
     * Views and factories are never transported in UiValue/Parcel; factories remain in the host.
     */
    fun applyWithViews(batch: UiPatchBatch, bindings: List<AndroidViewBindingV1>)
}

/**
 * Immutable, process-local binding. Tokens are allocated monotonically by the host and never
 * reassigned to another View. The renderer borrows [view], never destroys it or its user listeners.
 */
class AndroidViewBindingV1(val nodeId: Int, val leaseId: Int, val view: View) {
    init {
        if (nodeId <= 0 || leaseId <= 0) throw ComposeUiContractException(ComposeUiErrorCodes.INVALID_ARGUMENT,
            "AndroidView.view: Invalid binding identity", nodeId.takeIf { it > 0 }, AndroidViewInteropV1.PROP_VIEW)
    }
}
