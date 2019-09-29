/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-28.
 */

package pl.szczodrzynski.edziennik.api.v2.events

class SyncProgressEvent(val profileId: Int, val profileName: String?, val progress: Int, val progressRes: Int?)