package org.autojs.autojs.compat

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.BadParcelableException
import android.os.Bundle
import android.os.IBinder
import android.os.Parcel
import android.os.Process
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.core.plugin.center.InstalledPluginRepository
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.external.ScriptIntents
import org.autojs.autojs.external.shortcut.ScriptShortcutIntentFactory
import org.autojs.plugin.common.api.PluginInfo
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Semantic negative controls plus real host Parcelable/Intent shapes, with no assumed passes. */
@Suppress("DEPRECATION")
class ParcelCompatibilityDeviceTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val loader get() = ExecutionConfig::class.java.classLoader
    private val hardening: Boolean
        get() = requireNotNull(InstrumentationRegistry.getArguments().getString("sdk37.parcelHardening")) {
            "Pass sdk37.parcelHardening=true or false for the recorded compatibility state"
        }.toBooleanStrict()

    @Test fun underReadingParcelableMatchesTheLocalCompatPolicy() {
        val parcel = Parcel.obtain()
        try {
            parcel.writeValue(UnderReadingParcelable())
            parcel.setDataPosition(0)
            val error = runCatching { parcel.readValue(loader) }.exceptionOrNull()
            checkMismatch(error?.javaClass?.name, error?.message, "local")
        } finally {
            parcel.recycle()
        }
    }

    @Test fun underReadingParcelableMatchesTheRemoteCompatPolicy() {
        val result = remote(ParcelCompatibilityProbeService.CHECK_VALUE) { writeValue(UnderReadingParcelable()) }
        checkMismatch(result.getString("errorClass"), result.getString("errorMessage"), "remote")
        if (!hardening) assertEquals(UnderReadingParcelable::class.java.name, result.getString("valueClass"))
    }

    @Test fun executionConfigPreservesItsExistingWireFieldsAndBoundary() {
        val config = ExecutionConfig("/sdk37/工作目录", arrayOf("/a", "/b"), 0x10000000, 17, 37, 3)
        val parcel = Parcel.obtain()
        try {
            config.writeToParcel(parcel, 0)
            val boundary = parcel.dataPosition()
            parcel.writeInt(0x12345678)
            parcel.setDataPosition(0)
            assertEquals(config, ExecutionConfig.CREATOR.createFromParcel(parcel))
            assertEquals("Reader must consume exactly the legacy six-field layout", boundary, parcel.dataPosition())
            assertEquals(0x12345678, parcel.readInt())
        } finally {
            parcel.recycle()
        }
        val result = echo(Bundle().apply { putParcelable("config", config) })
        assertEquals(config, result.getParcelable<ExecutionConfig>("config"))
        // scriptConfig and arguments are intentionally absent from the existing wire contract.
    }

    @Test fun pluginInfoPreservesNullableArraysLongsAndNestedCapabilities() {
        for (populated in listOf(false, true)) {
            val info = PluginInfo().apply {
                name = "SDK37 插件"
                author = "fixture"
                versionCode = 0x100000001L
                if (populated) {
                    description = "description"; instruction = "instruction"; versionName = "1.0.0"
                    versionDate = "2026/09/13"; id = "fixture"; engine = "test"; variant = "v1"
                    collaborators = arrayOf("a", "b")
                    supportedAbis = arrayOf("x86_64", "arm64-v8a")
                    capabilities = Bundle().apply {
                        putBoolean("ready", true)
                        putBundle("nested", Bundle().apply { putIntArray("versions", intArrayOf(36, 37)) })
                    }
                }
            }
            val copy = requireNotNull(echo(Bundle().apply { putParcelable("info", info) }).getParcelable<PluginInfo>("info"))
            assertEquals(info.name, copy.name); assertEquals(info.author, copy.author)
            assertEquals(info.versionCode, copy.versionCode); assertEquals(info.versionName, copy.versionName)
            assertEquals(info.description, copy.description); assertEquals(info.instruction, copy.instruction)
            assertEquals(info.versionDate, copy.versionDate); assertEquals(info.id, copy.id)
            assertEquals(info.engine, copy.engine); assertEquals(info.variant, copy.variant)
            assertArrayEquals(info.collaborators, copy.collaborators); assertArrayEquals(info.supportedAbis, copy.supportedAbis)
            if (populated) {
                assertTrue(copy.capabilities!!.getBoolean("ready"))
                assertArrayEquals(intArrayOf(36, 37), copy.capabilities!!.getBundle("nested")!!.getIntArray("versions"))
            } else assertNull(copy.capabilities)
        }
    }

    @Test fun taskerJsonAndShortcutExtrasSurviveRemoteUnparcelling() {
        val path = "/sdcard/脚本/sdk37.lua"
        val json = JSONObject().put(ScriptIntents.EXTRA_KEY_PATH, path)
            .put(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT, "var before = 37;")
        assertTrue(ScriptIntents.isTaskerJsonObjectValid(json))
        val tasker = Intent().putExtra(ScriptIntents.EXTRA_KEY_JSON, json.toString())
        val shortcut = ScriptShortcutIntentFactory.create(path)
        val result = echo(Bundle().apply { putParcelable("tasker", tasker); putParcelable("shortcut", shortcut) })
        val taskerCopy = requireNotNull(result.getParcelable<Intent>("tasker"))
        val jsonCopy = JSONObject(taskerCopy.getStringExtra(ScriptIntents.EXTRA_KEY_JSON)!!)
        assertTrue(ScriptIntents.isTaskerJsonObjectValid(jsonCopy))
        assertEquals(path, jsonCopy.getString(ScriptIntents.EXTRA_KEY_PATH))
        assertEquals("var before = 37;", jsonCopy.getString(ScriptIntents.EXTRA_KEY_PRE_EXECUTE_SCRIPT))
        val shortcutCopy = requireNotNull(result.getParcelable<Intent>("shortcut"))
        assertEquals(path, shortcutCopy.getStringExtra(ScriptIntents.EXTRA_KEY_PATH))
        assertTrue(ScriptShortcutIntentFactory.isCompatible(shortcutCopy, path, "lua"))
        assertEquals(shortcut.extras!!.keySet(), shortcutCopy.extras!!.keySet())
        for (key in shortcut.extras!!.keySet()) assertEquals(shortcut.extras!!.get(key), shortcutCopy.extras!!.get(key))
    }

    @Test fun installedPluginMetadataIsReadThroughTheProductionRepository() = runBlocking {
        val expected = requireNotNull(InstrumentationRegistry.getArguments().getString("sdk37.pluginPackages"))
            .split(',').filter(String::isNotBlank).toSet()
        require(expected.isNotEmpty()) { "Select installed plugin packages explicitly" }
        val repository = InstalledPluginRepository()
        val snapshot = repository.discoverInstalledSnapshot(context).associateBy { it.packageName }
        val bound = repository.discoverInstalled(context).associateBy { it.packageName }
        for (packageName in expected) {
            assertNotNull("Package-manager snapshot omitted $packageName", snapshot[packageName])
            val row = requireNotNull(bound[packageName]) { "Bound discovery omitted $packageName" }
            assertNull("INFO binding failed for $packageName", row.bindError)
            assertTrue("Missing title for $packageName", row.title.isNotBlank())
            assertTrue("Missing version for $packageName", row.versionName.isNotBlank())
            Log.i(TAG, "metadata package=$packageName version=${row.versionName} info=${row.pluginInfo != null}")
        }
    }

    private fun checkMismatch(errorClass: String?, message: String?, side: String) {
        Log.i(TAG, "$side hardening=$hardening error=$errorClass message=$message")
        if (hardening) {
            assertEquals(BadParcelableException::class.java.name, errorClass)
            assertTrue("Expected the length mismatch diagnostic: $message", message.orEmpty().contains("consumed"))
            assertTrue(message.orEmpty().contains("[throwing]"))
        } else assertNull("Legacy control must accept the deliberately under-read value: $message", errorClass)
    }

    private fun echo(payload: Bundle): Bundle {
        val result = remote(ParcelCompatibilityProbeService.ECHO) { writeBundle(payload) }
        assertNull(result.getString("errorMessage"), result.getString("errorClass"))
        return requireNotNull(result.getBundle("payload")).apply { classLoader = loader }
    }

    private fun remote(code: Int, write: Parcel.() -> Unit): Bundle {
        val connected = CountDownLatch(1)
        val binder = AtomicReference<IBinder>()
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName, service: IBinder) { binder.set(service); connected.countDown() }
            override fun onServiceDisconnected(name: ComponentName) = Unit
        }
        assertTrue(context.bindService(Intent(context, ParcelCompatibilityProbeService::class.java), connection, Context.BIND_AUTO_CREATE))
        try {
            assertTrue("Separate-process fixture did not bind", connected.await(20, TimeUnit.SECONDS))
            val data = Parcel.obtain()
            val reply = Parcel.obtain()
            try {
                data.writeInterfaceToken(ParcelCompatibilityProbeService.DESCRIPTOR)
                data.write()
                assertTrue(binder.get().transact(code, data, reply, 0))
                reply.readException()
                val result = requireNotNull(reply.readBundle(loader))
                assertTrue("Fixture must use a distinct process", result.getInt("receiverPid") != Process.myPid())
                assertEquals(context.applicationInfo.targetSdkVersion, result.getInt("receiverTarget"))
                Log.i(TAG, "binder hostPid=${Process.myPid()} receiverPid=${result.getInt("receiverPid")} target=${result.getInt("receiverTarget")}")
                return result
            } finally {
                reply.recycle(); data.recycle()
            }
        } finally {
            context.unbindService(connection)
        }
    }

    companion object { private const val TAG = "SDK37-Parcel" }
}
