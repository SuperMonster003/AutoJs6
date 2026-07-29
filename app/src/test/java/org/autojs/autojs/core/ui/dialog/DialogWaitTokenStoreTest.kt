package org.autojs.autojs.core.ui.dialog

import org.autojs.autojs.core.looper.Loopers
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap

class DialogWaitTokenStoreTest {

    @Test
    fun detachedTokenReleaseDoesNotReleaseTokenInstalledDuringDismissCallback() {
        val first = token(1)
        val second = token(2)
        val store = DialogWaitTokenStore()
        store.replace(first.token)

        val detached = store.detach()
        store.replace(second.token)
        detached?.release()

        assertSame(first.token, detached)
        assertFalse(first.waitIds.contains(1))
        assertTrue(second.waitIds.contains(2))

        val current = store.detach()
        assertSame(second.token, current)
        current?.release()
        assertFalse(second.waitIds.contains(2))
    }

    @Test
    fun reentrantShowCannotReleaseDismissTokenBeforeCallbacksFinish() {
        val first = token(1)
        val second = token(2)
        val store = DialogWaitTokenStore()
        store.replace(first.token)
        val callbackOrder = mutableListOf<String>()

        store.detach(first.token)
        DialogDismissDispatcher.dispatch(
            {
                callbackOrder += "internal"
                assertTrue(first.waitIds.contains(1))
                store.replace(second.token)
                assertTrue(first.waitIds.contains(1))
            },
            {
                callbackOrder += "user"
                assertTrue(first.waitIds.contains(1))
            },
            {
                callbackOrder += "release"
                first.token.release()
            },
        )

        assertFalse(first.waitIds.contains(1))
        assertTrue(second.waitIds.contains(2))
        assertSame(second.token, store.detach())
        assertTrue(callbackOrder == listOf("internal", "user", "release"))

        second.token.release()
        assertFalse(second.waitIds.contains(2))
    }

    @Test
    fun dismissOfCurrentShowDoesNotReleasePendingShowToken() {
        val current = token(1)
        val pending = token(2)
        val store = DialogWaitTokenStore()
        store.replace(current.token)

        store.detach()?.release()

        assertFalse(current.waitIds.contains(1))
        assertTrue(pending.waitIds.contains(2))

        store.replace(pending.token)
        assertSame(pending.token, store.detach())
        pending.token.release()
        assertFalse(pending.waitIds.contains(2))
    }

    @Test
    fun replacingCurrentTokenReleasesPreviousShow() {
        val first = token(1)
        val second = token(2)
        val store = DialogWaitTokenStore()
        store.replace(first.token)

        store.replace(second.token)

        assertFalse(first.waitIds.contains(1))
        assertTrue(second.waitIds.contains(2))

        store.detach()?.release()
    }

    @Test
    fun staleDismissCallbackDoesNotReleaseNewerToken() {
        val first = token(1)
        val second = token(2)
        val store = DialogWaitTokenStore()
        store.replace(first.token)
        val firstDismissCallback = { store.release(first.token) }
        val secondDismissCallback = { store.release(second.token) }

        store.replace(second.token)
        firstDismissCallback()

        assertFalse(first.waitIds.contains(1))
        assertTrue(second.waitIds.contains(2))

        secondDismissCallback()
        assertFalse(second.waitIds.contains(2))
        assertNull(store.detach())
    }

    @Test
    fun staleShowFailureDoesNotReleaseNewerToken() {
        val first = token(1)
        val second = token(2)
        val store = DialogWaitTokenStore()
        store.replace(first.token)
        store.replace(second.token)

        store.release(first.token)

        assertFalse(first.waitIds.contains(1))
        assertTrue(second.waitIds.contains(2))

        store.detach()?.release()
    }

    @Test
    fun showFailureReleasesCurrentToken() {
        val current = token(1)
        val store = DialogWaitTokenStore()
        store.replace(current.token)

        store.release(current.token)

        assertFalse(current.waitIds.contains(1))
        assertNull(store.detach())
    }

    private fun token(id: Int): TokenFixture {
        val waitIds = ConcurrentHashMap.newKeySet<Int>()
        assertTrue(waitIds.add(id))
        return TokenFixture(Loopers.WaitToken(id, waitIds), waitIds)
    }

    private data class TokenFixture(
        val token: Loopers.WaitToken,
        val waitIds: Set<Int>,
    )

}
