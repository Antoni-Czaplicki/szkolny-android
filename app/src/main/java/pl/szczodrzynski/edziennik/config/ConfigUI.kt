/*
 * Copyright (c) Kuba Szczodrzyński 2019-11-26.
 */

package pl.szczodrzynski.edziennik.config

import pl.szczodrzynski.edziennik.config.utils.get
import pl.szczodrzynski.edziennik.config.utils.getIntList
import pl.szczodrzynski.edziennik.config.utils.set

class ConfigUI(val config: Config) {
    private var mTheme: Int? = null
    var theme: Int
        get() { mTheme = mTheme ?: config.values.get("theme", 1); return mTheme ?: 1 }
        set(value) { config.set("theme", value); mTheme = value }

    private var mHeaderBackground: String? = null
    var headerBackground: String?
        get() { mHeaderBackground = mHeaderBackground ?: config.values.get("headerBackground", null as String?); return mHeaderBackground }
        set(value) { config.set("headerBg", value); mHeaderBackground = value }

    private var mAppBackground: String? = null
    var appBackground: String?
        get() { mAppBackground = mAppBackground ?: config.values.get("appBackground", null as String?); return mAppBackground }
        set(value) { config.set("appBg", value); mAppBackground = value }

    private var mMiniMenuVisible: Boolean? = null
    var miniMenuVisible: Boolean
        get() { mMiniMenuVisible = mMiniMenuVisible ?: config.values.get("miniMenuVisible", false); return mMiniMenuVisible ?: false }
        set(value) { config.set("miniMenuVisible", value); mMiniMenuVisible = value }

    private var mMiniMenuButtons: List<Int>? = null
    var miniMenuButtons: List<Int>
        get() { mMiniMenuButtons = mMiniMenuButtons ?: config.values.getIntList("miniMenuButtons", listOf()); return mMiniMenuButtons ?: listOf() }
        set(value) { config.set("miniMenuButtons", value); mMiniMenuButtons = value }
}