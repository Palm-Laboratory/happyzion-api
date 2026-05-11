package org.happyzion.api.site.interfaces.dto

import org.happyzion.api.site.application.MainVideoSetting
import org.happyzion.api.site.application.UpdateMainVideoSettingCommand

data class MainVideoSettingResponse(
    val videoUrl: String,
)

data class UpdateMainVideoSettingRequest(
    val videoUrl: String,
)

fun MainVideoSetting.toDto(): MainVideoSettingResponse =
    MainVideoSettingResponse(videoUrl = videoUrl)

fun UpdateMainVideoSettingRequest.toCommand(): UpdateMainVideoSettingCommand =
    UpdateMainVideoSettingCommand(videoUrl = videoUrl)
