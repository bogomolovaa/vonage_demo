package com.tokbox.sample.basicvideochat

import android.text.TextUtils

object OpenTokConfig {
    /*
    Fill the following variables using your own Project info from the OpenTok dashboard
    https://dashboard.tokbox.com/projects

    Note that this application will ignore credentials in the `OpenTokConfig` file when `CHAT_SERVER_URL` contains a
    valid URL.
    */
    /*
    Fill the following variables using your own Project info from the OpenTok dashboard
    https://dashboard.tokbox.com/projects

    Note that this application will ignore credentials in the `OpenTokConfig` file when `CHAT_SERVER_URL` contains a
    valid URL.
    */

    // Replace with a API key
    const val API_KEY = "47484501"

    // Replace with a generated Session ID
    const val SESSION_ID = "1_MX40NzQ4NDUwMX5-MTY0OTk0ODk1ODkyOH5haFZWNUlxWDI4WHFKMVlHQUFKdktrYnR-fg"

    // Replace with a generated token (from the dashboard or using an OpenTok server SDK)
    const val TOKEN = "T1==cGFydG5lcl9pZD00NzQ4NDUwMSZzaWc9ZTM5ODA4ZDc2MTM2OTVkNDc4ZDhmYzdhZWVlOWZiMjJkNGIwOTM5ZTpzZXNzaW9uX2lkPTFfTVg0ME56UTRORFV3TVg1LU1UWTBPVGswT0RrMU9Ea3lPSDVoYUZaV05VbHhXREk0V0hGS01WbEhRVUZLZGt0clluUi1mZyZjcmVhdGVfdGltZT0xNjQ5OTQ5MDIzJm5vbmNlPTAuNzAwMjE5MjQ1OTQxODQyMSZyb2xlPXB1Ymxpc2hlciZleHBpcmVfdGltZT0xNjUyNTQxMDAyJmluaXRpYWxfbGF5b3V0X2NsYXNzX2xpc3Q9"

    // *** The code below is to validate this configuration file. You do not need to modify it  ***
    val isValid: Boolean
        get() = !(TextUtils.isEmpty(API_KEY) || TextUtils.isEmpty(SESSION_ID) || TextUtils.isEmpty(TOKEN))

    val description: String
        get() = """
               OpenTokConfig:
               API_KEY: $API_KEY
               SESSION_ID: $SESSION_ID
               TOKEN: $TOKEN
               """.trimIndent()
}