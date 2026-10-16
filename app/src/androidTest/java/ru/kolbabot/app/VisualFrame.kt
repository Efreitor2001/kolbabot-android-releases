package ru.kolbabot.app

import android.app.Instrumentation
import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import android.view.accessibility.AccessibilityNodeInfo
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

/** Compose semantics can be ready before SurfaceFlinger presents the corresponding pixels. */
internal fun awaitPresentedUiFrame(instrumentation: Instrumentation) {
    assertNoSystemAnrDialog(instrumentation)
    val presented = CountDownLatch(1)
    Handler(Looper.getMainLooper()).post {
        val choreographer = Choreographer.getInstance()
        var remaining = 3
        val callback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                if (--remaining == 0) presented.countDown() else choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(callback)
    }
    val synchronized = CountDownLatch(1)
    val failure = AtomicReference<Throwable?>(null)
    Thread({
        try {
            assertTrue("The Android renderer did not present three frames", presented.await(1500, TimeUnit.MILLISECONDS))
        } catch (error: Throwable) { failure.set(error) }
        finally { synchronized.countDown() }
    }, "visual-frame-barrier").apply { isDaemon = true }.start()
    assertTrue("The visual frame barrier exceeded two seconds", synchronized.await(2, TimeUnit.SECONDS))
    failure.get()?.let { throw it }
    assertNoSystemAnrDialog(instrumentation)
}

internal fun assertNoSystemAnrDialog(instrumentation: Instrumentation) {
    var remaining = 2_000
    fun containsAnr(node: AccessibilityNodeInfo?, depth: Int): Boolean {
        if (node == null || depth > 30 || remaining-- <= 0) return false
        val text = node.text?.toString().orEmpty()
        if (text.contains("isn't responding", true) || text.contains("is not responding", true) || text.contains("не отвечает", true)) return true
        return (0 until node.childCount).any { containsAnr(node.getChild(it), depth + 1) }
    }
    assertFalse("An ANR dialog makes this screenshot invalid", containsAnr(instrumentation.uiAutomation.rootInActiveWindow, 0))
}
