package de.passbutler.common.ui

import de.passbutler.common.base.Failure
import de.passbutler.common.base.Result
import de.passbutler.common.base.Success
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tinylog.kotlin.Logger
import kotlin.coroutines.EmptyCoroutineContext

interface RequestSending : CoroutineScope, ProgressPresenting, BannerPresenting

fun <ResultType> RequestSending.launchRequestSending(
    handleSuccess: ((ResultType) -> Unit)? = null,
    handleFailure: ((error: Throwable) -> Unit)? = null,
    handleLoadingChanged: ((isLoading: Boolean) -> Unit)? = blockingProgressScreen(),
    isCancellable: Boolean = true,
    block: suspend () -> Result<ResultType>
): Job {
    val viewClassName = javaClass.simpleName

    val coroutineContext = if (isCancellable) {
        EmptyCoroutineContext
    } else {
        NonCancellable
    }

    return launch(coroutineContext) {
        try {
            handleLoadingChanged?.invoke(true)

            val result = withContext(Dispatchers.IO) {
                block()
            }

            handleLoadingChanged?.invoke(false)

            when (result) {
                is Success -> handleSuccess?.invoke(result.result)
                is Failure -> {
                    val exception = result.throwable
                    Logger.warn(exception, "${viewClassName}: The operation failed with exception")
                    handleFailure?.invoke(exception)
                }
            }
        } catch (cancellationException: CancellationException) {
            Logger.warn(cancellationException, "The job was cancelled!")
        }
    }
}

private fun ProgressPresenting.blockingProgressScreen(): (Boolean) -> Unit {
    return { isLoading ->
        if (isLoading) {
            showProgress()
        } else {
            hideProgress()
        }
    }
}