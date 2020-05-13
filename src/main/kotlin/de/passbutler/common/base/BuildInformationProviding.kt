package de.passbutler.common.base

interface BuildInformationProviding {
    val buildType: BuildType
}

sealed class BuildType {
    object Debug : BuildType()
    object Release : BuildType()
    object Other : BuildType()
}