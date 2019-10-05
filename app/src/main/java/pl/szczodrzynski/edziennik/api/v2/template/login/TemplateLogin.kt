/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-5.
 */

package pl.szczodrzynski.edziennik.api.v2.template.login

import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.api.v2.*
import pl.szczodrzynski.edziennik.api.v2.librus.login.LibrusLoginApi
import pl.szczodrzynski.edziennik.api.v2.librus.login.LibrusLoginMessages
import pl.szczodrzynski.edziennik.api.v2.librus.login.LibrusLoginPortal
import pl.szczodrzynski.edziennik.api.v2.librus.login.LibrusLoginSynergia
import pl.szczodrzynski.edziennik.api.v2.template.data.DataTemplate
import pl.szczodrzynski.edziennik.utils.Utils

class TemplateLogin(val data: DataTemplate, val onSuccess: () -> Unit) {
    companion object {
        private const val TAG = "TemplateLogin"
    }

    private var cancelled = false

    init {
        nextLoginMethod(onSuccess)
    }

    private fun nextLoginMethod(onSuccess: () -> Unit) {
        if (data.targetLoginMethodIds.isEmpty()) {
            onSuccess()
            return
        }
        useLoginMethod(data.targetLoginMethodIds.removeAt(0)) { usedMethodId ->
            if (usedMethodId != -1)
                data.loginMethods.add(usedMethodId)
            if (cancelled) {
                onSuccess()
                return@useLoginMethod
            }
            nextLoginMethod(onSuccess)
        }
    }

    private fun useLoginMethod(loginMethodId: Int, onSuccess: (usedMethodId: Int) -> Unit) {
        // this should never be true
        if (data.loginMethods.contains(loginMethodId)) {
            onSuccess(-1)
            return
        }
        Utils.d(TAG, "Using login method $loginMethodId")
        when (loginMethodId) {
            LOGIN_METHOD_TEMPLATE_WEB -> {
                data.startProgress(R.string.edziennik_progress_login_template_web)
                TemplateLoginWeb(data) { onSuccess(loginMethodId) }
            }
            LOGIN_METHOD_TEMPLATE_API -> {
                data.startProgress(R.string.edziennik_progress_login_template_api)
                TemplateLoginApi(data) { onSuccess(loginMethodId) }
            }
        }
    }
}