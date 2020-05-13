package de.passbutler.common.base

import org.tinylog.kotlin.Logger
import java.net.URI

val URI.isHttpsScheme
    get() = scheme == "https"

fun String.toURI(): URI {
    return URI.create(this)
}

fun String.toURIOrNull(): URI? {
    return try {
        this.toURI()
    } catch (exception: Exception) {
        Logger.warn(exception, "The URI '$this' could not be parsed!")
        null
    }
}