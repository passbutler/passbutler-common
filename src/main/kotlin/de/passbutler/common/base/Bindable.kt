package de.passbutler.common.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias BindableObserver<T> = (newValue: T) -> Unit

interface Bindable<T> {
    val value: T

    fun addObserver(scope: CoroutineScope?, notifyOnRegister: Boolean, observer: BindableObserver<T>)
    fun removeObserver(observer: BindableObserver<T>)
    fun notifyChange()

    data class ObserverWrapper<T>(val observer: BindableObserver<T>, val scope: CoroutineScope?)
}

class MutableBindable<T>(initialValue: T) : Bindable<T> {

    val observers: Set<Bindable.ObserverWrapper<T>>
        get() = _observers

    private val _observers = mutableSetOf<Bindable.ObserverWrapper<T>>()

    override var value: T = initialValue
        set(value) {
            if (value != field) {
                field = value
                notifyChange()
            }
        }

    override fun addObserver(scope: CoroutineScope?, notifyOnRegister: Boolean, observer: BindableObserver<T>) {
        synchronized(this) {
            val observerWrapper = Bindable.ObserverWrapper(observer, scope)
            _observers.add(observerWrapper)
        }

        if (notifyOnRegister) {
            dispatchValueChange(observer, scope, value)
        }
    }

    override fun removeObserver(observer: BindableObserver<T>) {
        synchronized(this) {
            _observers.removeAll { it.observer == observer }
        }
    }

    override fun notifyChange() {
        synchronized(this) {
            val currentValue = value

            _observers.forEach {
                dispatchValueChange(it.observer, it.scope, currentValue)
            }
        }
    }

    private fun dispatchValueChange(observer: BindableObserver<T>, scope: CoroutineScope?, newValue: T) {
        if (scope != null) {
            scope.launch { observer(newValue) }
        } else {
            observer(newValue)
        }
    }
}