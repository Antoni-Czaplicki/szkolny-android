/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-5.
 */

package pl.szczodrzynski.edziennik.data.api.edziennik.template.data

import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.data.api.edziennik.template.DataTemplate
import pl.szczodrzynski.edziennik.data.api.edziennik.template.ENDPOINT_TEMPLATE_API_SAMPLE
import pl.szczodrzynski.edziennik.data.api.edziennik.template.ENDPOINT_TEMPLATE_WEB_SAMPLE
import pl.szczodrzynski.edziennik.data.api.edziennik.template.ENDPOINT_TEMPLATE_WEB_SAMPLE_2
import pl.szczodrzynski.edziennik.data.api.edziennik.template.data.api.TemplateApiSample
import pl.szczodrzynski.edziennik.data.api.edziennik.template.data.web.TemplateWebSample
import pl.szczodrzynski.edziennik.data.api.edziennik.template.data.web.TemplateWebSample2
import pl.szczodrzynski.edziennik.utils.Utils

class TemplateData(val data: DataTemplate, val onSuccess: () -> Unit) {
    companion object {
        private const val TAG = "TemplateData"
    }

    init {
        nextEndpoint(onSuccess)
    }

    private fun nextEndpoint(onSuccess: () -> Unit) {
        if (data.targetEndpointIds.isEmpty()) {
            onSuccess()
            return
        }
        if (data.cancelled) {
            onSuccess()
            return
        }
        useEndpoint(data.targetEndpointIds.removeAt(0)) {
            data.progress(data.progressStep)
            nextEndpoint(onSuccess)
        }
    }

    private fun useEndpoint(endpointId: Int, onSuccess: () -> Unit) {
        Utils.d(TAG, "Using endpoint $endpointId")
        when (endpointId) {
            ENDPOINT_TEMPLATE_WEB_SAMPLE -> {
                data.startProgress(R.string.edziennik_progress_endpoint_student_info)
                TemplateWebSample(data) { onSuccess() }
            }
            ENDPOINT_TEMPLATE_WEB_SAMPLE_2 -> {
                data.startProgress(R.string.edziennik_progress_endpoint_school_info)
                TemplateWebSample2(data) { onSuccess() }
            }
            ENDPOINT_TEMPLATE_API_SAMPLE -> {
                data.startProgress(R.string.edziennik_progress_endpoint_grades)
                TemplateApiSample(data) { onSuccess() }
            }
            else -> onSuccess()
        }
    }
}
