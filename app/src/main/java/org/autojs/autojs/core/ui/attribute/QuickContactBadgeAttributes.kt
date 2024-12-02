package org.autojs.autojs.core.ui.attribute

import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds
import android.view.View
import android.widget.QuickContactBadge
import org.autojs.autojs.core.ui.BiMaps
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.runtime.ScriptRuntime

open class QuickContactBadgeAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, view: View) : ImageViewAttributes(scriptRuntime, resourceParser, view) {

    override val view = super.view as QuickContactBadge

    override fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        super.onRegisterAttrs(scriptRuntime)

        registerAttr("overlay") { view.setOverlay(drawables.parse(view, it)) }
        registerAttr("prioritizedMimeType") { view.setPrioritizedMimeType(PRIORITIZED_MIME_TYPES[it]) }

        registerAttr("phone") { view.assignContactFromPhone(it, true) }
        registerAttr("email") { view.assignContactFromEmail(it, true) }
    }

    companion object {

        private val PRIORITIZED_MIME_TYPES = BiMaps.newBuilder<String, String>()
            .put("aggregationExceptions", ContactsContract.AggregationExceptions.CONTENT_ITEM_TYPE)
            .put("contacts", ContactsContract.Contacts.CONTENT_ITEM_TYPE)
            .put("directory", ContactsContract.Directory.CONTENT_ITEM_TYPE)
            .put("email", CommonDataKinds.Email.CONTENT_ITEM_TYPE)
            .put("event", CommonDataKinds.Event.CONTENT_ITEM_TYPE)
            .put("groupMembership", CommonDataKinds.GroupMembership.CONTENT_ITEM_TYPE)
            .put("groups", ContactsContract.Groups.CONTENT_ITEM_TYPE)
            .put("identity", CommonDataKinds.Identity.CONTENT_ITEM_TYPE)
            .put("im", CommonDataKinds.Im.CONTENT_ITEM_TYPE)
            .put("nickname", CommonDataKinds.Nickname.CONTENT_ITEM_TYPE)
            .put("note", CommonDataKinds.Note.CONTENT_ITEM_TYPE)
            .put("organization", CommonDataKinds.Organization.CONTENT_ITEM_TYPE)
            .put("phone", CommonDataKinds.Phone.CONTENT_ITEM_TYPE)
            .put("photo", CommonDataKinds.Photo.CONTENT_ITEM_TYPE)
            .put("rawContacts", ContactsContract.RawContacts.CONTENT_ITEM_TYPE)
            .put("relation", CommonDataKinds.Relation.CONTENT_ITEM_TYPE)
            .put("settings", ContactsContract.Settings.CONTENT_ITEM_TYPE)
            .put("sipAddress", CommonDataKinds.SipAddress.CONTENT_ITEM_TYPE)
            .put("statusUpdates", ContactsContract.StatusUpdates.CONTENT_ITEM_TYPE)
            .put("structuredName", CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE)
            .put("structuredPostal", CommonDataKinds.StructuredPostal.CONTENT_ITEM_TYPE)
            .put("website", CommonDataKinds.Website.CONTENT_ITEM_TYPE)
            .build()

    }

}
