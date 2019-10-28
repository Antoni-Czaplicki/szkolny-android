/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-25. 
 */

package pl.szczodrzynski.edziennik.api.v2.idziennik.data

import pl.szczodrzynski.edziennik.R
import pl.szczodrzynski.edziennik.api.v2.idziennik.*
import pl.szczodrzynski.edziennik.api.v2.idziennik.data.web.*
import pl.szczodrzynski.edziennik.utils.Utils

class IdziennikData(val data: DataIdziennik, val onSuccess: () -> Unit) {
    companion object {
        private const val TAG = "IdziennikData"
    }

    private var cancelled = false

    init {
        nextEndpoint(onSuccess)
    }

    private fun nextEndpoint(onSuccess: () -> Unit) {
        if (data.targetEndpointIds.isEmpty()) {
            onSuccess()
            return
        }
        if (cancelled) {
            onSuccess()
            return
        }
        useEndpoint(data.targetEndpointIds.removeAt(0)) {
            nextEndpoint(onSuccess)
        }
    }

    private fun useEndpoint(endpointId: Int, onSuccess: () -> Unit) {
        Utils.d(TAG, "Using endpoint $endpointId")
        when (endpointId) {
            ENDPOINT_IDZIENNIK_WEB_TIMETABLE -> {
                data.startProgress(R.string.edziennik_progress_endpoint_timetable)
                IdziennikWebTimetable(data) { onSuccess() }
            }
            ENDPOINT_IDZIENNIK_WEB_GRADES -> {
                data.startProgress(R.string.edziennik_progress_endpoint_grades)
                IdziennikWebGrades(data) { onSuccess() }
            }
            ENDPOINT_IDZIENNIK_WEB_PROPOSED_GRADES -> {
                data.startProgress(R.string.edziennik_progress_endpoint_proposed_grades)
                IdziennikWebProposedGrades(data) { onSuccess() }
            }
            ENDPOINT_IDZIENNIK_WEB_EXAMS -> {
                data.startProgress(R.string.edziennik_progress_endpoint_exams)
                IdziennikWebExams(data) { onSuccess() }
            }
            ENDPOINT_IDZIENNIK_WEB_NOTICES -> {
                data.startProgress(R.string.edziennik_progress_endpoint_notices)
                IdziennikWebNotices(data) { onSuccess() }
            }
            ENDPOINT_IDZIENNIK_WEB_ANNOUNCEMENTS -> {
                data.startProgress(R.string.edziennik_progress_endpoint_announcements)
                IdziennikWebAnnouncements(data) { onSuccess() }
            }
            ENDPOINT_IDZIENNIK_WEB_ATTENDANCE -> {
                data.startProgress(R.string.edziennik_progress_endpoint_attendance)
                IdziennikWebAttendance(data) { onSuccess() }
            }
            else -> onSuccess()
        }
    }
}
