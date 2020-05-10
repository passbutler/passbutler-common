package de.passbutler.common.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.newSingleThreadContext
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BindablesTest {

    @Test
    fun `Add and remove observer checks`() {
        val testCoroutineScope = createTestCoroutineScope("Any scope name")
        val testBindable = MutableBindable<String?>(null)

        // Initially no observers are set
        Assertions.assertTrue(testBindable.observers.isEmpty())

        val observer1: BindableObserver<String?> = {
            // Do nothing
        }

        // If observer 1 is set, check if it was added
        testBindable.addObserver(null, false, observer1)
        Assertions.assertTrue(testBindable.observers.size == 1)

        // If observer 1 is set again with the same scope, check that it was not added again
        testBindable.addObserver(null, false, observer1)
        Assertions.assertTrue(testBindable.observers.size == 1)

        // If observer 1 is set again with other scope, check if it was added
        testBindable.addObserver(testCoroutineScope, false, observer1)
        Assertions.assertTrue(testBindable.observers.size == 2)

        val observer2: BindableObserver<String?> = {
            // Do nothing
        }

        // If observer 2 is set, check if it was added
        testBindable.addObserver(null, false, observer2)
        Assertions.assertTrue(testBindable.observers.size == 3)

        // Check if the observer 2 is removed
        testBindable.removeObserver(observer2)
        Assertions.assertTrue(testBindable.observers.size == 2)

        // Check if both observer 1 are removed
        testBindable.removeObserver(observer1)
        Assertions.assertTrue(testBindable.observers.isEmpty())
    }

    private fun createTestCoroutineScope(threadName: String): CoroutineScope {
        return CoroutineScope(newSingleThreadContext(threadName))
    }
}