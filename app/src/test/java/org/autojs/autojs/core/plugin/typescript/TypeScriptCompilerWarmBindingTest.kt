package org.autojs.autojs.core.plugin.typescript

import android.os.IBinder
import android.os.IInterface
import android.os.Parcel
import java.io.FileDescriptor
import org.autojs.autojs.core.plugin.ServiceBindingLease
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptCompilerWarmBindingTest {

    @Test
    fun aliveWhileLeaseIsOpenAndBinderAnswers() {
        val lease = registeredLease()
        val binding = TypeScriptCompilerWarmBinding(lease, FakeBinder(), projectSessionRevision = 1)

        assertTrue(binding.isAlive)
        assertEquals(1, binding.projectSessionRevision)
    }

    @Test
    fun deadOnceItsLeaseWasReleasedEvenIfTheProxyStillClaimsToBeAlive() {
        var unbound = 0
        val lease = ServiceBindingLease { unbound++ }.apply { markRegistered() }
        val binder = FakeBinder()
        val binding = TypeScriptCompilerWarmBinding(lease, binder, projectSessionRevision = 1)

        // The dedicated lease closes itself when the binding is reported dead; the proxy's
        // isBinderAlive flag may never follow once the death recipient was unlinked.
        lease.close()

        assertTrue(binder.isBinderAlive)
        assertFalse(binding.isAlive)
        assertEquals(1, unbound)
    }

    @Test
    fun deadWhenTheProxyNoLongerAnswersPings() {
        val binder = FakeBinder(pingAnswer = false)
        val binding = TypeScriptCompilerWarmBinding(registeredLease(), binder, null)

        assertFalse(binding.isAlive)
    }

    @Test
    fun deadWhenAPingThrows() {
        val binder = FakeBinder(pingThrows = true)
        val binding = TypeScriptCompilerWarmBinding(registeredLease(), binder, null)

        assertFalse(binding.isAlive)
    }

    @Test
    fun closeReleasesTheLeaseExactlyOnce() {
        var unbound = 0
        val lease = ServiceBindingLease { unbound++ }.apply { markRegistered() }
        val binding = TypeScriptCompilerWarmBinding(lease, FakeBinder(), null)

        binding.close()
        binding.close()

        assertFalse(binding.isAlive)
        assertTrue(lease.isClosed)
        assertEquals(1, unbound)
    }

    private fun registeredLease(): ServiceBindingLease =
        ServiceBindingLease {}.apply { markRegistered() }

    private class FakeBinder(
        private val pingAnswer: Boolean = true,
        private val pingThrows: Boolean = false,
    ) : IBinder {
        override fun getInterfaceDescriptor(): String = "fake"
        override fun pingBinder(): Boolean {
            if (pingThrows) throw IllegalStateException("ping failed")
            return pingAnswer
        }
        override fun isBinderAlive(): Boolean = true
        override fun queryLocalInterface(descriptor: String): IInterface? = null
        override fun dump(fd: FileDescriptor, args: Array<out String>?) = Unit
        override fun dumpAsync(fd: FileDescriptor, args: Array<out String>?) = Unit
        override fun transact(code: Int, data: Parcel, reply: Parcel?, flags: Int): Boolean = true
        override fun linkToDeath(recipient: IBinder.DeathRecipient, flags: Int) = Unit
        override fun unlinkToDeath(recipient: IBinder.DeathRecipient, flags: Int): Boolean = true
    }
}
