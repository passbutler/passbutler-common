package de.passbutler.common.base

import org.tinylog.kotlin.Logger
import java.net.URI

val URI.isHttpsScheme
    get() = scheme == "https"

fun String.toURIOrNull(): URI? {
    return try {
        URI.create(this)
    } catch (exception: Exception) {
        Logger.warn(exception, "The URI '$this' could not be parsed!")
        null
    }
}