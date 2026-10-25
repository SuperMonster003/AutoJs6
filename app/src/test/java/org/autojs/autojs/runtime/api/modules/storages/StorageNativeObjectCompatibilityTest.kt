package org.autojs.autojs.runtime.api.modules.storages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mozilla.javascript.Undefined

class StorageNativeObjectCompatibilityTest {

    @Test
    fun storedJsonNullIsDifferentFromAMissingEntry() {
        val defaultValue = Any()

        assertNull(StorageNativeObject.Getter.parseStoredValue("null", defaultValue))
        assertEquals(defaultValue, StorageNativeObject.Getter.parseStoredValue(null, defaultValue))
    }

    @Test
    fun clearFunctionsKeepTheLegacyUndefinedReturnContract() {
        val methods = StorageNativeObject.Getter::class.java.declaredMethods.associateBy { it.name }

        assertEquals(Undefined::class.java, methods.getValue("clear").returnType)
        assertEquals(Undefined::class.java, methods.getValue("clearSync").returnType)
        assertEquals(Boolean::class.javaPrimitiveType, methods.getValue("contains").returnType)
    }

}
