/*
 * Copyright (c) Kacper Ziubryniewicz 2020-1-6
 */
package pl.szczodrzynski.edziennik.data.db.dao

import androidx.lifecycle.LiveData
import androidx.room.Dao
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import pl.szczodrzynski.edziennik.data.db.entity.Event
import pl.szczodrzynski.edziennik.data.db.entity.Metadata
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.utils.models.Date
import pl.szczodrzynski.edziennik.utils.models.Time

@Dao
abstract class EventDao : BaseDao<Event, EventFull> {
    companion object {
        private const val QUERY = """
            SELECT 
            *, 
            teachers.teacherName ||" "|| teachers.teacherSurname AS teacherName,
            eventTypes.eventTypeName AS typeName,
            eventTypes.eventTypeColor AS typeColor
            FROM events
            LEFT JOIN subjects USING(profileId, subjectId)
            LEFT JOIN teachers USING(profileId, teacherId)
            LEFT JOIN teams USING(profileId, teamId)
            LEFT JOIN eventTypes USING(profileId, eventType)
            LEFT JOIN metadata ON eventId = thingId AND (thingType = ${Metadata.TYPE_EVENT} OR thingType = ${Metadata.TYPE_HOMEWORK}) AND metadata.profileId = events.profileId
        """

        private const val ORDER_BY = """GROUP BY eventId ORDER BY eventDate, eventTime, addedDate ASC"""
        private const val NOT_BLACKLISTED = """events.eventBlacklisted = 0"""
    }

    //abstract fun queryRaw(query: SupportSQLiteQuery)
    //private fun queryRaw(query: String) = queryRaw(SimpleSQLiteQuery(query))

    @Query("DELETE FROM events WHERE profileId = :profileId")
    abstract override fun clear(profileId: Int)

    /*fun update(event: Event) =
            queryRaw("""UPDATE events SET 
                eventDate = '${event.date.stringY_m_d}',
                eventTime = ${event.time?.stringValue},
                eventTopic = '${event.topic}'""")*/

    @Query("DELETE FROM events WHERE profileId = :profileId AND eventId = :id")
    abstract fun remove(profileId: Int, id: Long)

    @Query("DELETE FROM metadata WHERE profileId = :profileId AND thingType = :thingType AND thingId = :thingId")
    abstract fun removeMetadata(profileId: Int, thingType: Int, thingId: Long)

    @Transaction
    open fun remove(profileId: Int, type: Long, id: Long) {
        remove(profileId, id)
        removeMetadata(profileId, if (type == Event.TYPE_HOMEWORK) Metadata.TYPE_HOMEWORK else Metadata.TYPE_EVENT, id)
    }

    @Transaction
    open fun remove(event: Event) {
        remove(event.profileId, event.type, event.id)
    }

    @Transaction
    open fun remove(profileId: Int, event: Event) {
        remove(profileId, event.type, event.id)
    }

    @RawQuery(observedEntities = [Event::class])
    abstract override fun getRaw(query: SupportSQLiteQuery): LiveData<List<EventFull>>



    fun getAll(profileId: Int) =
            getRaw("$QUERY WHERE $NOT_BLACKLISTED AND events.profileId = $profileId $ORDER_BY")
    fun getAllByType(profileId: Int, type: Long, filter: String = "1") =
            getRaw("$QUERY WHERE $NOT_BLACKLISTED AND events.profileId = $profileId AND eventType = $type AND $filter $ORDER_BY")
    fun getAllByDate(profileId: Int, date: Date) =
            getRaw("$QUERY WHERE $NOT_BLACKLISTED AND events.profileId = $profileId AND eventDate = '${date.stringY_m_d}' $ORDER_BY")
    fun getAllByDateTime(profileId: Int, date: Date, time: Time) =
            getRaw("$QUERY WHERE $NOT_BLACKLISTED AND events.profileId = $profileId AND eventDate = '${date.stringY_m_d}' AND eventTime = ${time.stringValue}")
    fun getAllNearest(profileId: Int, today: Date, limit: Int) =
            getRaw("$QUERY WHERE $NOT_BLACKLISTED AND events.profileId = $profileId AND eventDate >= '${today.stringY_m_d}' $ORDER_BY LIMIT $limit")



    fun getAllNow(profileId: Int) =
            getRawNow("$QUERY WHERE $NOT_BLACKLISTED AND events.profileId = $profileId $ORDER_BY")
    fun getNotNotifiedNow() =
            getRawNow("$QUERY WHERE $NOT_BLACKLISTED AND notified = 0 $ORDER_BY")
    fun getNotNotifiedNow(profileId: Int) =
            getRawNow("$QUERY WHERE $NOT_BLACKLISTED AND events.profileId = $profileId AND notified = 0 $ORDER_BY")
    fun getAllByDateNow(profileId: Int, date: Date) =
            getRawNow("$QUERY WHERE $NOT_BLACKLISTED AND events.profileId = $profileId AND eventDate = '${date.stringY_m_d}' $ORDER_BY")



    fun getByIdNow(profileId: Int, id: Long) =
            getOneNow("$QUERY WHERE events.profileId = $profileId AND eventId = $id")



    @Query("SELECT eventId FROM events WHERE profileId = :profileId AND eventBlacklisted = 1")
    abstract fun getBlacklistedIds(profileId: Int): List<Long>

    @get:Query("SELECT eventId FROM events WHERE eventBlacklisted = 1")
    abstract val blacklistedIds: List<Long>

    @Query("UPDATE events SET eventAddedManually = 1 WHERE profileId = :profileId AND eventDate < :date")
    abstract fun convertOlderToManual(profileId: Int, date: Date?)

    @Query("DELETE FROM events WHERE teamId = :teamId AND eventId = :id")
    abstract fun removeByTeamId(teamId: Long, id: Long)

    @Query("DELETE FROM events WHERE profileId = :profileId AND eventAddedManually = 0")
    abstract fun removeNotManual(profileId: Int)

    @RawQuery
    abstract fun removeFuture(query: SupportSQLiteQuery?): Long

    @Transaction
    open fun removeFuture(profileId: Int, todayDate: Date, filter: String) {
        removeFuture(SimpleSQLiteQuery("DELETE FROM events WHERE profileId = " + profileId
                + " AND eventAddedManually = 0 AND eventDate >= '" + todayDate.stringY_m_d + "'" +
                " AND " + filter))
    }

    @Query("DELETE FROM events WHERE profileId = :profileId AND eventAddedManually = 0 AND eventDate >= :todayDate AND eventType = :type")
    abstract fun removeFutureWithType(profileId: Int, todayDate: Date, type: Long)

    @Query("DELETE FROM events WHERE profileId = :profileId AND eventAddedManually = 0 AND eventDate >= :todayDate AND eventType != :exceptType")
    abstract fun removeFutureExceptType(profileId: Int, todayDate: Date, exceptType: Long)

    @Transaction
    open fun removeFutureExceptTypes(profileId: Int, todayDate: Date, exceptTypes: List<Long>) {
        removeFuture(profileId, todayDate, "eventType NOT IN " + exceptTypes.toString().replace('[', '(').replace(']', ')'))
    }

    @Query("UPDATE metadata SET seen = :seen WHERE profileId = :profileId AND (thingType = " + Metadata.TYPE_EVENT + " OR thingType = " + Metadata.TYPE_LESSON_CHANGE + " OR thingType = " + Metadata.TYPE_HOMEWORK + ") AND thingId IN (SELECT eventId FROM events WHERE profileId = :profileId AND eventDate = :date)")
    abstract fun setSeenByDate(profileId: Int, date: Date, seen: Boolean)

    @Query("UPDATE events SET eventBlacklisted = :blacklisted WHERE profileId = :profileId AND eventId = :eventId")
    abstract fun setBlacklisted(profileId: Int, eventId: Long, blacklisted: Boolean)

}