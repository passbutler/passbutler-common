package de.passbutler.common.ui

import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

interface ProgressPresenting {
    fun showProgress()
    fun hideProgress()
}

interface BannerPresenting {
    fun showInformation(message: String)
    fun showError(message: String)
}

interface DebouncedUIPresenting {
    var lastViewTransactionTime: Instant?

    fun ensureDebouncedViewTransaction(): Boolean {
        val currentTime = Instant.now()
        val ensureDebouncedViewTransaction = lastViewTransactionTime?.let { Duration.between(it, currentTime) > VIEW_TRANSACTION_DEBOUNCE_TIME } ?: true

        if (ensureDebouncedViewTransaction) {
            lastViewTransactionTime = currentTime
        }

        return ensureDebouncedViewTransaction
    }

    companion object {
        private val VIEW_TRANSACTION_DEBOUNCE_TIME = Duration.of(450, ChronoUnit.MILLIS)
    }
}

enum class TransitionType {
    MODAL,
    SLIDE,
    FADE,
    NONE
}

val FADE_TRANSITION_DURATION: Duration = Duration.of(350, ChronoUnit.MILLIS)
val SLIDE_TRANSITION_DURATION: Duration = Duration.of(500, ChronoUnit.MILLIS)