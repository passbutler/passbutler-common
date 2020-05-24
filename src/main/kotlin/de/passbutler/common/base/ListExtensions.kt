package de.passbutler.common.base

fun <T> MutableList<T>.addAllIfNotNull(elements: Collection<T>?) {
    if (elements != null) {
        addAll(elements)
    }
}