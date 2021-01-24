package de.passbutler.common.base

interface EditableViewModel<EditingViewModelType : EditingViewModel> {
    fun createEditingViewModel(): EditingViewModelType
}

interface EditingViewModel
