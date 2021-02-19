/*
 * Copyright (c) Kacper Ziubryniewicz 2019-10-19
 */

package pl.szczodrzynski.edziennik.data.api.edziennik.vulcan.firstlogin

import com.google.gson.JsonArray
import org.greenrobot.eventbus.EventBus
import pl.szczodrzynski.edziennik.*
import pl.szczodrzynski.edziennik.data.api.*
import pl.szczodrzynski.edziennik.data.api.edziennik.vulcan.DataVulcan
import pl.szczodrzynski.edziennik.data.api.edziennik.vulcan.data.VulcanApi
import pl.szczodrzynski.edziennik.data.api.edziennik.vulcan.data.VulcanHebe
import pl.szczodrzynski.edziennik.data.api.edziennik.vulcan.data.VulcanWebMain
import pl.szczodrzynski.edziennik.data.api.edziennik.vulcan.login.CufsCertificate
import pl.szczodrzynski.edziennik.data.api.edziennik.vulcan.login.VulcanLoginApi
import pl.szczodrzynski.edziennik.data.api.edziennik.vulcan.login.VulcanLoginHebe
import pl.szczodrzynski.edziennik.data.api.edziennik.vulcan.login.VulcanLoginWebMain
import pl.szczodrzynski.edziennik.data.api.events.FirstLoginFinishedEvent
import pl.szczodrzynski.edziennik.data.api.models.ApiError
import pl.szczodrzynski.edziennik.data.db.entity.Profile
import pl.szczodrzynski.edziennik.utils.models.Date

class VulcanFirstLogin(val data: DataVulcan, val onSuccess: () -> Unit) {
    companion object {
        const val TAG = "VulcanFirstLogin"
    }

    private val api = VulcanApi(data, null)
    private val web = VulcanWebMain(data, null)
    private val hebe = VulcanHebe(data, null)
    private val profileList = mutableListOf<Profile>()
    private val loginStoreId = data.loginStore.id
    private var firstProfileId = loginStoreId
    private val tryingSymbols = mutableListOf<String>()

    init {
        if (data.loginStore.mode == LOGIN_MODE_VULCAN_WEB) {
            VulcanLoginWebMain(data) {
                val xml = web.readCertificate() ?: run {
                    data.error(ApiError(TAG, ERROR_VULCAN_WEB_NO_CERTIFICATE))
                    return@VulcanLoginWebMain
                }
                val certificate = web.parseCertificate(xml)

                if (data.symbol != null && data.symbol != "default") {
                    tryingSymbols += data.symbol ?: "default"
                }
                else {

                    tryingSymbols += certificate.userInstances
                }

                checkSymbol(certificate)
            }
        }
        else if (data.loginStore.mode == LOGIN_MODE_VULCAN_API) {
            registerDevice {
                EventBus.getDefault().postSticky(FirstLoginFinishedEvent(profileList, data.loginStore))
                onSuccess()
            }
        }
        else {
            registerDeviceHebe {
                EventBus.getDefault().postSticky(FirstLoginFinishedEvent(profileList, data.loginStore))
                onSuccess()
            }
        }
    }

    private fun checkSymbol(certificate: CufsCertificate) {
        if (tryingSymbols.isEmpty()) {
            EventBus.getDefault().postSticky(FirstLoginFinishedEvent(profileList, data.loginStore))
            onSuccess()
            return
        }

        val result = web.postCertificate(certificate, tryingSymbols.removeAt(0)) { symbol, state ->
            when (state) {
                VulcanWebMain.STATE_NO_REGISTER -> {
                    checkSymbol(certificate)
                }
                VulcanWebMain.STATE_LOGGED_OUT -> data.error(ApiError(TAG, ERROR_VULCAN_WEB_LOGGED_OUT))
                VulcanWebMain.STATE_SUCCESS -> {
                    webRegisterDevice(symbol) {
                        checkSymbol(certificate)
                    }
                }
            }
        }

        // postCertificate returns false if the cert is not valid anymore
        if (!result) {
            data.error(ApiError(TAG, ERROR_VULCAN_WEB_CERTIFICATE_EXPIRED)
                    .withApiResponse(certificate.xml))
        }
    }

    private fun webRegisterDevice(symbol: String, onSuccess: () -> Unit) {
        web.getStartPage(symbol, postErrors = false) { _, schoolSymbols ->
            if (schoolSymbols.isEmpty()) {
                onSuccess()
                return@getStartPage
            }
            data.symbol = symbol
            val schoolSymbol = data.schoolSymbol ?: schoolSymbols.firstOrNull()
            web.webGetJson(TAG, VulcanWebMain.WEB_NEW, "$schoolSymbol/$VULCAN_WEB_ENDPOINT_REGISTER_DEVICE") { result, _ ->
                val json = result.getJsonObject("data")
                data.symbol = symbol
                data.apiToken = data.apiToken.toMutableMap().also {
                    it[symbol] = json.getString("TokenKey")
                }
                data.apiPin = data.apiPin.toMutableMap().also {
                    it[symbol] = json.getString("PIN")
                }
                registerDeviceHebe(onSuccess)
            }
        }
    }

    private fun registerDevice(onSuccess: () -> Unit) {
        VulcanLoginApi(data) {
            api.apiGet(TAG, VULCAN_API_ENDPOINT_STUDENT_LIST, baseUrl = true) { json, _ ->
                val students = json.getJsonArray("Data")

                if (students == null || students.isEmpty()) {
                    EventBus.getDefault().postSticky(FirstLoginFinishedEvent(listOf(), data.loginStore))
                    onSuccess()
                    return@apiGet
                }

                students.forEach { studentEl ->
                    val student = studentEl.asJsonObject

                    val schoolSymbol = student.getString("JednostkaSprawozdawczaSymbol") ?: return@forEach
                    val schoolShort = student.getString("JednostkaSprawozdawczaSkrot") ?: return@forEach
                    val schoolCode = "${data.symbol}_$schoolSymbol"
                    val studentId = student.getInt("Id") ?: return@forEach
                    val studentLoginId = student.getInt("UzytkownikLoginId") ?: return@forEach
                    val studentClassId = student.getInt("IdOddzial") ?: return@forEach
                    val studentClassName = student.getString("OkresPoziom").toString() + (student.getString("OddzialSymbol") ?: return@forEach)
                    val studentSemesterId = student.getInt("IdOkresKlasyfikacyjny") ?: return@forEach
                    val studentFirstName = student.getString("Imie") ?: ""
                    val studentLastName = student.getString("Nazwisko") ?: ""
                    val studentNameLong = "$studentFirstName $studentLastName".fixName()
                    val studentNameShort = "$studentFirstName ${studentLastName[0]}.".fixName()

                    val userLogin = student.getString("UzytkownikLogin") ?: ""
                    val currentSemesterStartDate = student.getLong("OkresDataOd") ?: return@forEach
                    val currentSemesterEndDate = (student.getLong("OkresDataDo") ?: return@forEach) + 86400
                    val studentSemesterNumber = student.getInt("OkresNumer") ?: return@forEach

                    val isParent = student.getString("UzytkownikRola") == "opiekun"
                    val accountName = if (isParent)
                        student.getString("UzytkownikNazwa")?.swapFirstLastName()?.fixName()
                    else null

                    var dateSemester1Start: Date? = null
                    var dateSemester2Start: Date? = null
                    var dateYearEnd: Date? = null
                    when (studentSemesterNumber) {
                        1 -> {
                            dateSemester1Start = Date.fromMillis(currentSemesterStartDate * 1000)
                            dateSemester2Start = Date.fromMillis(currentSemesterEndDate * 1000)
                        }
                        2 -> {
                            dateSemester2Start = Date.fromMillis(currentSemesterStartDate * 1000)
                            dateYearEnd = Date.fromMillis(currentSemesterEndDate * 1000)
                        }
                    }

                    val profile = Profile(
                            firstProfileId++,
                            loginStoreId,
                            LOGIN_TYPE_VULCAN,
                            studentNameLong,
                            userLogin,
                            studentNameLong,
                            studentNameShort,
                            accountName
                    ).apply {
                        this.studentClassName = studentClassName
                        studentData["symbol"] = data.symbol

                        studentData["studentId"] = studentId
                        studentData["studentLoginId"] = studentLoginId
                        studentData["studentClassId"] = studentClassId
                        studentData["studentSemesterId"] = studentSemesterId
                        studentData["studentSemesterNumber"] = studentSemesterNumber
                        studentData["semester${studentSemesterNumber}Id"] = studentSemesterId
                        studentData["schoolSymbol"] = schoolSymbol
                        studentData["schoolShort"] = schoolShort
                        studentData["schoolName"] = schoolCode
                        studentData["currentSemesterEndDate"] = currentSemesterEndDate
                    }
                    dateSemester1Start?.let {
                        profile.dateSemester1Start = it
                        profile.studentSchoolYearStart = it.year
                    }
                    dateSemester2Start?.let { profile.dateSemester2Start = it }
                    dateYearEnd?.let { profile.dateYearEnd = it }

                    profileList.add(profile)
                }

                onSuccess()
            }
        }
    }

    private fun registerDeviceHebe(onSuccess: () -> Unit) {
        VulcanLoginHebe(data) {
            hebe.apiGet(
                TAG,
                VULCAN_HEBE_ENDPOINT_MAIN,
                query = mapOf("lastSyncDate" to "null"),
                baseUrl = true
            ) { students: JsonArray, _ ->
                if (students.isEmpty()) {
                    EventBus.getDefault().postSticky(FirstLoginFinishedEvent(listOf(), data.loginStore))
                    onSuccess()
                    return@apiGet
                }

                students.forEach { studentEl ->
                    val student = studentEl.asJsonObject

                    val unit = student.getJsonObject("Unit")
                    //val constituentUnit = student.getJsonObject("ConstituentUnit")
                    val pupil = student.getJsonObject("Pupil")
                    val login = student.getJsonObject("Login")
                    val periods = student.getJsonArray("Periods")?.map {
                        it.asJsonObject
                    } ?: listOf()

                    val period = periods.firstOrNull {
                        it.getBoolean("Current", false)
                    } ?: return@forEach

                    val periodLevel = period.getInt("Level") ?: return@forEach
                    val semester1 = periods.firstOrNull {
                        it.getInt("Level") == periodLevel && it.getInt("Number") == 1
                    }
                    val semester2 = periods.firstOrNull {
                        it.getInt("Level") == periodLevel && it.getInt("Number") == 2
                    }

                    val schoolSymbol = unit.getString("Symbol") ?: return@forEach
                    val schoolShort = unit.getString("Short") ?: return@forEach
                    val schoolCode = "${data.symbol}_$schoolSymbol"
                    val studentId = pupil.getInt("Id") ?: return@forEach
                    val studentLoginId = login.getInt("Id") ?: return@forEach
                    //val studentClassId = student.getInt("IdOddzial") ?: return@forEach
                    val studentClassName = student.getString("ClassDisplay") ?: return@forEach
                    val studentFirstName = pupil.getString("FirstName") ?: ""
                    val studentLastName = pupil.getString("Surname") ?: ""
                    val studentNameLong = "$studentFirstName $studentLastName".fixName()
                    val studentNameShort = "$studentFirstName ${studentLastName[0]}.".fixName()
                    val userLogin = login.getString("Value") ?: ""

                    val studentSemesterId = period.getInt("Id") ?: return@forEach
                    val studentSemesterNumber = period.getInt("Number") ?: return@forEach

                    val isParent = login.getString("LoginRole").equals("opiekun", ignoreCase = true)
                    val accountName = if (isParent)
                        login.getString("DisplayName")?.fixName()
                    else null

                    val dateSemester1Start = semester1
                        ?.getJsonObject("Start")
                        ?.getString("Date")
                        ?.let { Date.fromY_m_d(it) }
                    val dateSemester2Start = semester2
                        ?.getJsonObject("Start")
                        ?.getString("Date")
                        ?.let { Date.fromY_m_d(it) }
                    val dateYearEnd = semester2
                        ?.getJsonObject("End")
                        ?.getString("Date")
                        ?.let { Date.fromY_m_d(it) }

                    val profile = Profile(
                        firstProfileId++,
                        loginStoreId,
                        LOGIN_TYPE_VULCAN,
                        studentNameLong,
                        userLogin,
                        studentNameLong,
                        studentNameShort,
                        accountName
                    ).apply {
                        this.studentClassName = studentClassName
                        studentData["symbol"] = data.symbol

                        studentData["studentId"] = studentId
                        studentData["studentLoginId"] = studentLoginId
                        studentData["studentSemesterId"] = studentSemesterId
                        studentData["studentSemesterNumber"] = studentSemesterNumber
                        studentData["semester1Id"] = semester1?.getInt("Id") ?: 0
                        studentData["semester2Id"] = semester2?.getInt("Id") ?: 0
                        studentData["schoolSymbol"] = schoolSymbol
                        studentData["schoolShort"] = schoolShort
                        studentData["schoolName"] = schoolCode
                    }
                    dateSemester1Start?.let {
                        profile.dateSemester1Start = it
                        profile.studentSchoolYearStart = it.year
                    }
                    dateSemester2Start?.let { profile.dateSemester2Start = it }
                    dateYearEnd?.let { profile.dateYearEnd = it }

                    profileList.add(profile)
                }

                onSuccess()
            }
        }
    }
}
