/*
 * Copyright (c) Kuba Szczodrzyński 2019-9-29.
 */

package pl.szczodrzynski.edziennik.api.v2.interfaces

interface EdziennikInterface {
    fun sync(featureIds: List<Int>, viewId: Int? = null)
    fun getMessage(messageId: Int)
    fun markAllAnnouncementsAsRead()
    fun firstLogin()
    fun cancel()
}
