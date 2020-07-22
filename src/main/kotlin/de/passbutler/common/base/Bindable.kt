package de.passbutler.common.base

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

typealias BindableObserver<T> = (newValue: T) -> Unit

interface Bindable<T> {
    val value: T
    val observers: Set<ObserverWrapper<T>>
    val isActive: Boolean

    fun addObserver(scope: CoroutineScope?, notifyOnRegister: Boolean, observer: BindableObserver<T>)
    fun removeObserver(observer: BindableObserver<T>)
    fun notifyChange()

    fun onActive()
    fun onInactive()

    data class ObserverWrapper<T>(val observer: BindableObserver<T>, val scope: CoroutineScope?)
}

abstract class DefaultBindable<T> : Bindable<T> {

    override val observers: Set<Bindable.ObserverWrapper<T>>
        get() = _observers

    override val isActive: Boolean
        get() = _isActive

    private val _observers = mutableSetOf<Bindable.ObserverWrapper<T>>()
    private var _isActive = false

    override fun addObserver(scope: CoroutineScope?, notifyOnRegister: Boolean, observer: BindableObserver<T>) {
        synchronized(this) {
            val observerWrapper = Bindable.ObserverWrapper(observer, scope)
            _observers.add(observerWrapper)
        }

        if (notifyOnRegister) {
            dispatchValueChange(observer, scope, value)
        }

        checkActiveStateChanged()
    }

    override fun removeObserver(observer: BindableObserver<T>) {
        synchronized(this) {
            _observers.removeAll { it.observer == observer }
        }

        checkActiveStateChanged()
    }

    override fun notifyChange() {
        synchronized(this) {
            if (isActive) {
                val currentValue = value

                _observers.forEach {
                    dispatchValueChange(it.observer, it.scope, currentValue)
                }
            }
        }
    }

    override fun onActive() {
        // Is called if at least one observer is observing the Bindable
    }

    override fun onInactive() {
        // Is called if the no observer is observing the Bindable anymore
    }

    private fun dispatchValueChange(observer: BindableObserver<T>, scope: CoroutineScope?, newValue: T) {
        if (scope != null) {
            scope.launch { observer(newValue) }
        } else {
            observer(newValue)
        }
    }

    private fun checkActiveStateChanged() {
        // TODO: Synchronize `isActive`?
        val oldIsActive = isActive
        val newIsActive = hasActiveObservers()

        if (oldIsActive != newIsActive) {
            _isActive = newIsActive

            if (newIsActive) {
                onActive()
            } else {
                onInactive()
            }
        }
    }

    private fun hasActiveObservers(): Boolean {
        return synchronized(this) {
            observers.isNotEmpty()
        }
    }
}

open class ValueGetterBindable<T>(private val valueGetter: () -> T) : DefaultBindable<T>() {
    override val value: T
        get() = valueGetter()
}

class DependentValueGetterBindable<T>(private vararg val dependencies: Bindable<out Any?>, valueGetter: () -> T) : ValueGetterBindable<T>(valueGetter) {
    private val dependenciesChangedObserver: BindableObserver<Any?> = {
        notifyChange()
    }

    override fun onActive() {
        dependencies.forEach {
            it.addObserver(null, false, dependenciesChangedObserver)
        }
    }

    override fun onInactive() {
        dependencies.forEach {
            it.removeObserver(dependenciesChangedObserver)
        }
    }
}

open class MutableBindable<T>(initialValue: T) : DefaultBindable<T>() {
    override var value: T = initialValue
        set(value) {
            if (value != field) {
                field = value
                notifyChange()
            }
        }
}

class DiscardableMutableBindable<T>(private var initialValue: T) : MutableBindable<T>(initialValue) {
    val isModified
        get() = value != initialValue

    var isTouched = false

    override var value: T = initialValue
        set(value) {
            isTouched = true

            if (value != field) {
                field = value
                notifyChange()
            }
        }

    fun commitChangeAsInitialValue() {
        initialValue = value
        isTouched = false

        // Notify observers of the bindable (which may use `isModified` or `isTouched`)
        notifyChange()
    }

    fun discard() {
        // First reset touched state to be sure, notified observers already see reset state
        isTouched = false

        value = initialValue
    }
}