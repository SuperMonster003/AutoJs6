package org.autojs.autojs.pluginclient

import io.reactivex.Observable
import io.reactivex.subjects.PublishSubject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class RemoteServerStarterTest {
    @Test fun concurrentRequestsShareStartupAndTheAcceptLoopOutlivesTheirReplies() {
        var running = false
        var starts = 0
        var enabled = 0
        val server = PublishSubject.create<Unit>()
        val starter = RemoteServerStarter({ running }, { starts++; server }, { enabled++ }, { throw AssertionError(it) })
        val first = starter.start().test()
        val second = starter.start().test()
        assertEquals(1, starts)
        first.assertNoValues()
        second.assertNoValues()
        running = true
        server.onNext(Unit)
        first.assertResult(false)
        second.assertResult(false)
        assertEquals(1, enabled)
        assertTrue(server.hasObservers())
        server.onNext(Unit) // A newly connected VS Code client is not a second startup reply.
        first.assertValueCount(1)
        starter.start().test().assertResult(true)
        assertEquals(1, starts)
    }

    @Test fun aBindFailureIsReportedAndTheNextRequestCanRetry() {
        var running = false
        var starts = 0
        var failures = 0
        val starter = RemoteServerStarter({ running }, {
            starts++
            if (starts == 1) Observable.error<Unit>(IOException("port occupied"))
            else Observable.just(Unit).doOnNext { running = true }
        }, {}, { failures++ })
        starter.start().test().assertError(IOException::class.java)
        assertEquals(1, failures)
        starter.start().test().assertResult(false)
        assertEquals(2, starts)
    }

    @Test fun aSourceThatCompletesWithoutListeningNeverReportsSuccess() {
        val starter = RemoteServerStarter({ false }, { Observable.empty<Unit>() }, { fail() }, { fail() })
        starter.start().test().assertError(IOException::class.java)
    }

    @Test fun anErrorAfterStartupCleansUpWithoutSendingAnotherReply() {
        var running = false
        var failures = 0
        val server = PublishSubject.create<Unit>()
        val starter = RemoteServerStarter({ running }, { server }, {}, { running = false; failures++ })
        val response = starter.start().test()
        running = true
        server.onNext(Unit)
        server.onError(IOException("accept failed"))
        response.assertResult(false)
        assertEquals(1, failures)
        assertFalse(running)
    }
}
