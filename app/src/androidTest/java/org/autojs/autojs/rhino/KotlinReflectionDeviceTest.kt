package org.autojs.autojs.rhino

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.KClass

class KotlinReflectionDeviceTest {

    @Test
    fun hostReflectionFactorySupportsLibrariesUsingFullKotlinReflection() {
        val loader = InstrumentationRegistry.getInstrumentation().targetContext.classLoader
        val factory = loader.loadClass("kotlin.jvm.internal.Reflection")
        val kotlinClass = factory.getMethod("getOrCreateKotlinClass", Class::class.java)
            .invoke(null, String::class.java) as KClass<*>

        // The embedded Kotlin compiler calls this API when parsing command-line arguments.
        // Without a host reflection implementation the factory returns ClassReference, and
        // a full reflection library in a child loader fails with ClassCastException.
        val properties = loader.loadClass("kotlin.reflect.full.KClasses")
            .getMethod("getMemberProperties", KClass::class.java)
            .invoke(null, kotlinClass) as Collection<*>
        assertTrue("Kotlin built-ins metadata must remain packaged", properties.isNotEmpty())
        assertTrue(kotlinClass.members.any { it.name == "length" })
        assertEquals("String", kotlinClass.simpleName)
    }
}
