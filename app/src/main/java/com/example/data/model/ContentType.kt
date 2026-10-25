package com.example.data.model

import androidx.annotation.StringRes
import com.example.R

enum class ContentType(@StringRes val labelRes: Int) {
    TEXT(R.string.content_type_text),
    URL(R.string.content_type_url),
    EMAIL(R.string.content_type_email),
    PHONE(R.string.content_type_phone),
    HEX_COLOR(R.string.content_type_color),
    CODE(R.string.content_type_code),
    JSON(R.string.content_type_json)
}
