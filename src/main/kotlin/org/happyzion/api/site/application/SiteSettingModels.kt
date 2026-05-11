package org.happyzion.api.site.application

data class MainVideoSetting(
    val videoUrl: String,
)

data class UpdateMainVideoSettingCommand(
    val videoUrl: String,
)
