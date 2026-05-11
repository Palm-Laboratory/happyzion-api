package org.happyzion.api.site.interfaces.dto

import org.happyzion.api.site.application.MainVideoSetting

data class MainVideoSettingResponse(
    val videoUrl: String,
)

fun MainVideoSetting.toDto(): MainVideoSettingResponse =
    MainVideoSettingResponse(videoUrl = videoUrl)
