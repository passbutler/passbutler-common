package de.passbutler.common.base

fun <T> MutableList<T>.addIfNotNull(element: T?) {
    if (element != null) {
        add(element)
    }
}

fun <T> MutableList<T>.addAllIfNotNull(elements: Collection<T>?) {
    if (elements != null) {
        addAll(elements)
    }
}

fun <T> Collection<T>.contains(block: (T) -> Boolean): Boolean {
    return find(block) != null
}

fun <T> Collection<T>.containsNot(block: (T) -> Boolean): Boolean {
    return !contains(block)
}
