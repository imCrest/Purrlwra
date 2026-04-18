package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import android.content.Context
import cock.crest.purrfectsnap.lite.common.logger.AbstractLogger
import org.json.JSONArray
import org.json.JSONObject
import java.net.InetAddress
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class RandomizedDeviceProfile(
    val schemaVersion: Int,
    val profileId: String,
    val deviceInfo: DeviceInfo,
    val androidRelease: String,
    val buildIncremental: String,
    val buildDisplayId: String,
    val buildFingerprint: String,
    val buildHost: String,
    val buildTime: Long,
    val supportedAbis: List<String>,
    val supported32BitAbis: List<String>,
    val supported64BitAbis: List<String>,
    val androidId: String,
    val gsfId: String,
    val advertisingId: String,
    val wifiMacAddress: String,
    val bluetoothMacAddress: String,
    val ipAddress: String,
    val wifiSsid: String,
    val wifiRssi: Int,
    val localeTag: String,
    val countryIso: String,
    val timeZoneId: String,
    val timeZoneDisplayName: String,
    val networkType: Int,
    val networkOperator: String,
    val networkOperatorName: String,
    val networkCountryIso: String,
    val simCountryIso: String,
    val simOperator: String,
    val simOperatorName: String,
    val simState: Int,
    val hasIccCard: Boolean,
    val phoneCount: Int,
    val isHearingAidCompatibilitySupported: Boolean,
    val isTtySupported: Boolean,
    val isWorldPhone: Boolean,
    val isNetworkRoaming: Boolean,
    val isSmsCapable: Boolean,
    val isVoiceCapable: Boolean,
    val phoneType: Int,
    val phoneTypeString: String,
    val mmsUaProfUrl: String,
    val mmsUserAgent: String,
    val dnsServers: List<String>,
    val dnsSearchDomains: String,
    val privateDnsServerName: String,
    val privateDnsActive: Boolean,
    val hasCaptivePortal: Boolean,
    val secureStringSettings: Map<String, String>,
    val secureIntSettings: Map<String, Int>,
    val systemStringSettings: Map<String, String>,
    val systemIntSettings: Map<String, Int>,
    val globalStringSettings: Map<String, String>,
    val globalIntSettings: Map<String, Int>
) {
    fun locale(): Locale = Locale.forLanguageTag(localeTag)

    fun timeZone(): TimeZone = TimeZone.getTimeZone(timeZoneId)

    fun toJson(): JSONObject = JSONObject().apply {
        put("schemaVersion", schemaVersion)
        put("profileId", profileId)
        put("deviceInfo", JSONObject().apply {
            put("manufacturer", deviceInfo.manufacturer)
            put("model", deviceInfo.model)
            put("brand", deviceInfo.brand)
            put("device", deviceInfo.device)
            put("product", deviceInfo.product)
            put("hardware", deviceInfo.hardware)
            put("board", deviceInfo.board)
            put("bootloader", deviceInfo.bootloader)
            put("display", deviceInfo.display)
            put("host", deviceInfo.host)
        })
        put("androidRelease", androidRelease)
        put("buildIncremental", buildIncremental)
        put("buildDisplayId", buildDisplayId)
        put("buildFingerprint", buildFingerprint)
        put("buildHost", buildHost)
        put("buildTime", buildTime)
        put("supportedAbis", JSONArray(supportedAbis))
        put("supported32BitAbis", JSONArray(supported32BitAbis))
        put("supported64BitAbis", JSONArray(supported64BitAbis))
        put("androidId", androidId)
        put("gsfId", gsfId)
        put("advertisingId", advertisingId)
        put("wifiMacAddress", wifiMacAddress)
        put("bluetoothMacAddress", bluetoothMacAddress)
        put("ipAddress", ipAddress)
        put("wifiSsid", wifiSsid)
        put("wifiRssi", wifiRssi)
        put("localeTag", localeTag)
        put("countryIso", countryIso)
        put("timeZoneId", timeZoneId)
        put("timeZoneDisplayName", timeZoneDisplayName)
        put("networkType", networkType)
        put("networkOperator", networkOperator)
        put("networkOperatorName", networkOperatorName)
        put("networkCountryIso", networkCountryIso)
        put("simCountryIso", simCountryIso)
        put("simOperator", simOperator)
        put("simOperatorName", simOperatorName)
        put("simState", simState)
        put("hasIccCard", hasIccCard)
        put("phoneCount", phoneCount)
        put("isHearingAidCompatibilitySupported", isHearingAidCompatibilitySupported)
        put("isTtySupported", isTtySupported)
        put("isWorldPhone", isWorldPhone)
        put("isNetworkRoaming", isNetworkRoaming)
        put("isSmsCapable", isSmsCapable)
        put("isVoiceCapable", isVoiceCapable)
        put("phoneType", phoneType)
        put("phoneTypeString", phoneTypeString)
        put("mmsUaProfUrl", mmsUaProfUrl)
        put("mmsUserAgent", mmsUserAgent)
        put("dnsServers", JSONArray(dnsServers))
        put("dnsSearchDomains", dnsSearchDomains)
        put("privateDnsServerName", privateDnsServerName)
        put("privateDnsActive", privateDnsActive)
        put("hasCaptivePortal", hasCaptivePortal)
        put("secureStringSettings", JSONObject(secureStringSettings))
        put("secureIntSettings", JSONObject(secureIntSettings))
        put("systemStringSettings", JSONObject(systemStringSettings))
        put("systemIntSettings", JSONObject(systemIntSettings))
        put("globalStringSettings", JSONObject(globalStringSettings))
        put("globalIntSettings", JSONObject(globalIntSettings))
    }

    companion object {
        fun fromJson(json: String): RandomizedDeviceProfile {
            val root = JSONObject(json)
            val deviceInfoJson = root.getJSONObject("deviceInfo")
            return RandomizedDeviceProfile(
                schemaVersion = root.getInt("schemaVersion"),
                profileId = root.getString("profileId"),
                deviceInfo = DeviceInfo(
                    manufacturer = deviceInfoJson.getString("manufacturer"),
                    model = deviceInfoJson.getString("model"),
                    brand = deviceInfoJson.getString("brand"),
                    device = deviceInfoJson.getString("device"),
                    product = deviceInfoJson.getString("product"),
                    hardware = deviceInfoJson.getString("hardware"),
                    board = deviceInfoJson.getString("board"),
                    bootloader = deviceInfoJson.getString("bootloader"),
                    display = deviceInfoJson.getString("display"),
                    host = deviceInfoJson.getString("host")
                ),
                androidRelease = root.getString("androidRelease"),
                buildIncremental = root.getString("buildIncremental"),
                buildDisplayId = root.getString("buildDisplayId"),
                buildFingerprint = root.getString("buildFingerprint"),
                buildHost = root.getString("buildHost"),
                buildTime = root.getLong("buildTime"),
                supportedAbis = jsonArrayToStringList(root.getJSONArray("supportedAbis")),
                supported32BitAbis = jsonArrayToStringList(root.getJSONArray("supported32BitAbis")),
                supported64BitAbis = jsonArrayToStringList(root.getJSONArray("supported64BitAbis")),
                androidId = root.getString("androidId"),
                gsfId = root.getString("gsfId"),
                advertisingId = root.getString("advertisingId"),
                wifiMacAddress = root.getString("wifiMacAddress"),
                bluetoothMacAddress = root.getString("bluetoothMacAddress"),
                ipAddress = root.optString("ipAddress").ifBlank { defaultFallbackIpAddress() },
                wifiSsid = root.getString("wifiSsid"),
                wifiRssi = root.getInt("wifiRssi"),
                localeTag = root.getString("localeTag"),
                countryIso = root.getString("countryIso"),
                timeZoneId = root.getString("timeZoneId"),
                timeZoneDisplayName = root.getString("timeZoneDisplayName"),
                networkType = root.getInt("networkType"),
                networkOperator = root.getString("networkOperator"),
                networkOperatorName = root.getString("networkOperatorName"),
                networkCountryIso = root.getString("networkCountryIso"),
                simCountryIso = root.getString("simCountryIso"),
                simOperator = root.getString("simOperator"),
                simOperatorName = root.getString("simOperatorName"),
                simState = root.getInt("simState"),
                hasIccCard = root.getBoolean("hasIccCard"),
                phoneCount = root.getInt("phoneCount"),
                isHearingAidCompatibilitySupported = root.getBoolean("isHearingAidCompatibilitySupported"),
                isTtySupported = root.getBoolean("isTtySupported"),
                isWorldPhone = root.getBoolean("isWorldPhone"),
                isNetworkRoaming = root.getBoolean("isNetworkRoaming"),
                isSmsCapable = root.getBoolean("isSmsCapable"),
                isVoiceCapable = root.getBoolean("isVoiceCapable"),
                phoneType = root.getInt("phoneType"),
                phoneTypeString = root.getString("phoneTypeString"),
                mmsUaProfUrl = root.getString("mmsUaProfUrl"),
                mmsUserAgent = root.getString("mmsUserAgent"),
                dnsServers = jsonArrayToStringList(root.getJSONArray("dnsServers")),
                dnsSearchDomains = root.getString("dnsSearchDomains"),
                privateDnsServerName = root.getString("privateDnsServerName"),
                privateDnsActive = root.getBoolean("privateDnsActive"),
                hasCaptivePortal = root.getBoolean("hasCaptivePortal"),
                secureStringSettings = jsonObjectToStringMap(root.getJSONObject("secureStringSettings")),
                secureIntSettings = jsonObjectToIntMap(root.getJSONObject("secureIntSettings")),
                systemStringSettings = jsonObjectToStringMap(root.getJSONObject("systemStringSettings")),
                systemIntSettings = jsonObjectToIntMap(root.getJSONObject("systemIntSettings")),
                globalStringSettings = jsonObjectToStringMap(root.getJSONObject("globalStringSettings")),
                globalIntSettings = jsonObjectToIntMap(root.getJSONObject("globalIntSettings"))
            )
        }

        private fun jsonArrayToStringList(array: JSONArray): List<String> = buildList {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }

        private fun jsonObjectToStringMap(jsonObject: JSONObject): Map<String, String> {
            return jsonObject.keys().asSequence().associateWith { jsonObject.getString(it) }
        }

        private fun jsonObjectToIntMap(jsonObject: JSONObject): Map<String, Int> {
            return jsonObject.keys().asSequence().associateWith { jsonObject.getInt(it) }
        }

        private fun defaultFallbackIpAddress(): String = "23.42.18.101"
    }
}

object RandomizedDeviceProfileStore {
    private const val prefsName = "purrfectsnap_spoof"
    private const val schemaVersion = 5
    private const val profileKey = "randomized_device_profile"
    private val random = SecureRandom()

    fun getOrCreate(context: Context, logger: AbstractLogger, generationToken: String?): RandomizedDeviceProfile {
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val requestedToken = generationToken.orEmpty()
        prefs.getString(profileKey, null)?.let { raw ->
            runCatching {
                RandomizedDeviceProfile.fromJson(raw)
            }.onSuccess { profile ->
                val storedToken = prefs.getString("${profileKey}_token", "") ?: ""
                if (profile.schemaVersion == schemaVersion && storedToken == requestedToken) {
                    logger.info("Loaded randomized device profile ${profile.profileId} (${profile.deviceInfo.manufacturer} ${profile.deviceInfo.model})")
                    return profile
                }
            }.onFailure {
                logger.warn("Failed to parse saved randomized device profile, regenerating: ${it.message}")
            }
        }

        val previousProfile = prefs.getString(profileKey, null)?.let { raw ->
            runCatching { RandomizedDeviceProfile.fromJson(raw) }.getOrNull()
        }
        val profile = generateProfile(previousProfile)
        prefs.edit()
            .putString(profileKey, profile.toJson().toString())
            .putString("${profileKey}_token", requestedToken)
            .putString("android_id", profile.androidId)
            .putString("advertising_id", profile.advertisingId)
            .putString("bluetooth_address", profile.bluetoothMacAddress)
            .putString("gsf_id", profile.gsfId)
            .putString("random_device", profile.deviceInfo.model)
            .putString("device_fingerprint", profile.buildFingerprint)
            .apply()

        logger.info(
            "Generated randomized device profile ${profile.profileId}: " +
                "${profile.deviceInfo.manufacturer} ${profile.deviceInfo.model}, " +
                "androidId=${profile.androidId}, ip=${profile.ipAddress}, locale=${profile.localeTag}, tz=${profile.timeZoneId}, " +
                "carrier=${profile.simOperatorName}"
        )
        return profile
    }

    private fun generateProfile(previousProfile: RandomizedDeviceProfile?): RandomizedDeviceProfile {
        val eligibleDevices = DeviceSpoofer.getAvailableDevices().filter {
            DeviceSpoofer.getDeviceTemplate(it)?.capabilities?.let { capabilities ->
                capabilities.isSmsCapable && capabilities.isVoiceCapable && capabilities.isWorldPhone
            } == true
        }.ifEmpty { DeviceSpoofer.getAvailableDevices() }
        val deviceCandidates = eligibleDevices.filterNot {
            previousProfile != null &&
                DeviceSpoofer.getDeviceTemplate(it)?.deviceInfo?.model == previousProfile.deviceInfo.model
        }.ifEmpty { eligibleDevices }
        val deviceName = pick(deviceCandidates)
        val deviceTemplate = DeviceSpoofer.getDeviceTemplate(deviceName) ?: error("Missing device template for $deviceName")
        val regionCandidates = regionProfiles.filterNot {
            previousProfile != null &&
                it.localeTag == previousProfile.localeTag &&
                it.simOperatorName == previousProfile.simOperatorName
        }.ifEmpty { regionProfiles }
        val region = pick(regionCandidates)
        val buildCandidates = deviceTemplate.builds.filter { it.androidRelease in region.androidReleaseOptions }
            .ifEmpty { deviceTemplate.builds }
        val buildProfile = pick(buildCandidates)
        val deviceInfo = deviceTemplate.deviceInfo.copy(
            display = buildProfile.display,
            host = buildProfile.host,
            bootloader = buildProfile.bootloader ?: deviceTemplate.deviceInfo.bootloader
        )
        val androidRelease = buildProfile.androidRelease
        val buildIncremental = buildProfile.incremental
        val buildDisplayId = buildProfile.display
        val buildFingerprint = DeviceSpoofer.generateFingerprint(deviceInfo, buildProfile)
        val buildHost = buildProfile.host
        val buildTime = System.currentTimeMillis() - randomLong(45L, 220L) * 24L * 60L * 60L * 1000L
        val wifiMac = randomMacAddress()
        val bluetoothMac = randomMacAddress()
        val ipAddress = region.randomPublicIpAddress()
        val locale = Locale.forLanguageTag(region.localeTag)
        val timeZone = TimeZone.getTimeZone(region.timeZoneId)
        val capabilities = deviceTemplate.capabilities
        val secureStringSettings = mapOf(
            "accessibility_enabled" to "0",
            "speak_password" to "0",
            "allowed_geolocation_origins" to "",
            "install_non_market_apps" to "0",
            "device_provisioned" to "1",
            "enabled_notification_listeners" to ""
        )
        val secureIntSettings = mapOf(
            "input_method_selector_visibility" to 0,
            "accessibility_display_inversion_enabled" to 0,
            "enabled_accessibility_services" to 0,
            "skip_first_use_hints" to 0,
            "tts_default_synth" to 0
        )
        val systemStringSettings = mapOf(
            "dtmf_tone_type" to "normal",
            "mode_ringer_streams_affected" to "166",
            "mute_streams_affected" to "46",
            "show_password" to "1",
            "user_rotation" to "0"
        )
        val systemIntSettings = mapOf(
            "bluetooth_discoverability" to 0,
            "bluetooth_discoverability_timeout" to 120,
            "date_format" to 0,
            "end_button_behavior" to 2
        )
        val globalStringSettings = mapOf(
            "adb_enabled" to "0",
            "auto_time" to "1",
            "auto_time_zone" to "1",
            "development_settings_enabled" to "0",
            "stay_on_while_plugged_in" to "0",
            "usb_mass_storage_enabled" to "0",
            "wifi_networks_available_notification_on" to "0",
            "data_roaming" to "1"
        )
        val globalIntSettings = mapOf(
            "always_finish_activities" to 0,
            "animator_duration_scale" to 1,
            "http_proxy" to 0,
            "network_preference" to 1,
            "transition_animation_scale" to 1,
            "use_google_mail" to 1,
            "wait_for_debugger" to 0
        )

        return RandomizedDeviceProfile(
            schemaVersion = schemaVersion,
            profileId = UUID.randomUUID().toString().substring(0, 8),
            deviceInfo = deviceInfo,
            androidRelease = androidRelease,
            buildIncremental = buildIncremental,
            buildDisplayId = buildDisplayId,
            buildFingerprint = buildFingerprint,
            buildHost = buildHost,
            buildTime = buildTime,
            supportedAbis = capabilities.supportedAbis,
            supported32BitAbis = capabilities.supported32BitAbis,
            supported64BitAbis = capabilities.supported64BitAbis,
            androidId = randomHex(16),
            gsfId = randomHex(16),
            advertisingId = UUID.randomUUID().toString(),
            wifiMacAddress = wifiMac,
            bluetoothMacAddress = bluetoothMac,
            ipAddress = ipAddress,
            wifiSsid = region.randomWifiSsid(),
            wifiRssi = randomInt(-72, -36),
            localeTag = locale.toLanguageTag(),
            countryIso = region.countryIso,
            timeZoneId = region.timeZoneId,
            timeZoneDisplayName = timeZone.getDisplayName(false, TimeZone.SHORT, locale),
            networkType = 13,
            networkOperator = region.networkOperator,
            networkOperatorName = region.networkOperatorName,
            networkCountryIso = region.countryIso.lowercase(Locale.US),
            simCountryIso = region.countryIso.lowercase(Locale.US),
            simOperator = region.simOperator,
            simOperatorName = region.simOperatorName,
            simState = 5,
            hasIccCard = true,
            phoneCount = capabilities.phoneCount,
            isHearingAidCompatibilitySupported = capabilities.isHearingAidCompatibilitySupported,
            isTtySupported = capabilities.isTtySupported,
            isWorldPhone = capabilities.isWorldPhone,
            isNetworkRoaming = false,
            isSmsCapable = capabilities.isSmsCapable,
            isVoiceCapable = capabilities.isVoiceCapable,
            phoneType = capabilities.phoneType,
            phoneTypeString = capabilities.phoneTypeString,
            mmsUaProfUrl = "",
            mmsUserAgent = region.mmsUserAgent(deviceTemplate.marketingName, androidRelease),
            dnsServers = region.dnsServers.sortedBy { random.nextInt() }.take(2),
            dnsSearchDomains = region.dnsSearchDomains,
            privateDnsServerName = region.privateDnsServerName,
            privateDnsActive = true,
            hasCaptivePortal = false,
            secureStringSettings = secureStringSettings,
            secureIntSettings = secureIntSettings,
            systemStringSettings = systemStringSettings,
            systemIntSettings = systemIntSettings,
            globalStringSettings = globalStringSettings,
            globalIntSettings = globalIntSettings
        )
    }

    private fun randomHex(length: Int): String {
        val chars = CharArray(length)
        val alphabet = "0123456789abcdef"
        for (index in chars.indices) {
            chars[index] = alphabet[random.nextInt(alphabet.length)]
        }
        return String(chars)
    }

    private fun randomDigits(length: Int): String {
        val chars = CharArray(length)
        for (index in chars.indices) {
            chars[index] = ('0'.code + random.nextInt(10)).toChar()
        }
        return String(chars)
    }

    private fun randomMacAddress(): String {
        val bytes = ByteArray(6)
        random.nextBytes(bytes)
        bytes[0] = (bytes[0].toInt() and 0xFE or 0x02).toByte()
        return bytes.joinToString(":") { "%02x".format(it.toInt() and 0xFF) }
    }

    private fun randomPublicIpv4(prefixes: List<Int>? = null): String {
        val firstOctet = prefixes?.takeIf { it.isNotEmpty() }?.let { pick(it) } ?: run {
            generateSequence { randomInt(1, 224) }
                .first { candidate ->
                    candidate != 10 &&
                        candidate != 127 &&
                        candidate != 169 &&
                        candidate != 172 &&
                        candidate != 192
                }
        }
        val secondOctet = randomInt(1, 255)
        val thirdOctet = randomInt(1, 255)
        val fourthOctet = randomInt(2, 255)
        val candidate = "$firstOctet.$secondOctet.$thirdOctet.$fourthOctet"
        return runCatching { InetAddress.getByName(candidate).hostAddress }.getOrDefault(candidate)
    }

    private fun randomInt(minInclusive: Int, maxExclusive: Int): Int {
        require(maxExclusive > minInclusive)
        return minInclusive + random.nextInt(maxExclusive - minInclusive)
    }

    private fun randomLong(minInclusive: Long, maxExclusive: Long): Long {
        require(maxExclusive > minInclusive)
        val bound = maxExclusive - minInclusive
        var bits: Long
        var candidate: Long
        do {
            bits = random.nextLong() ushr 1
            candidate = bits % bound
        } while (bits - candidate + (bound - 1) < 0L)
        return minInclusive + candidate
    }

    private fun <T> pick(values: List<T>): T = values[random.nextInt(values.size)]

    private data class RegionProfile(
        val localeTag: String,
        val countryIso: String,
        val timeZoneId: String,
        val networkOperator: String,
        val networkOperatorName: String,
        val simOperator: String,
        val simOperatorName: String,
        val dnsServers: List<String>,
        val dnsSearchDomains: String,
        val privateDnsServerName: String,
        val timeFormat: String,
        val androidReleaseOptions: List<String>,
        val wifiPrefixes: List<String>,
        val ipPrefixes: List<Int>
    ) {
        fun randomWifiSsid(): String = "${pick(wifiPrefixes)}-${randomDigits(4)}"
        fun randomPublicIpAddress(): String = randomPublicIpv4(ipPrefixes)

        fun mmsUserAgent(model: String, androidRelease: String): String {
            return "$model/$androidRelease"
        }
    }

    private val regionProfiles = listOf(
        RegionProfile(
            localeTag = "en-US",
            countryIso = "US",
            timeZoneId = "America/New_York",
            networkOperator = "310260",
            networkOperatorName = "T-Mobile",
            simOperator = "310260",
            simOperatorName = "T-Mobile",
            dnsServers = listOf("8.8.8.8", "8.8.4.4", "1.1.1.1"),
            dnsSearchDomains = "hsd1.ny.comcast.net",
            privateDnsServerName = "dns.google",
            timeFormat = "12",
            androidReleaseOptions = listOf("14", "15"),
            wifiPrefixes = listOf("TP-Link", "NETGEAR", "XFINITY", "HomeWiFi"),
            ipPrefixes = listOf(23, 24, 45, 47, 66, 67, 68, 69, 72, 73, 98, 104, 107, 108, 162, 184, 198, 199)
        ),
        RegionProfile(
            localeTag = "en-GB",
            countryIso = "GB",
            timeZoneId = "Europe/London",
            networkOperator = "23430",
            networkOperatorName = "EE",
            simOperator = "23430",
            simOperatorName = "EE",
            dnsServers = listOf("1.1.1.1", "1.0.0.1", "8.8.8.8"),
            dnsSearchDomains = "bb.sky.com",
            privateDnsServerName = "one.one.one.one",
            timeFormat = "24",
            androidReleaseOptions = listOf("14", "15"),
            wifiPrefixes = listOf("Sky", "BT-Hub", "VirginMedia", "Linksys"),
            ipPrefixes = listOf(51, 62, 77, 81, 86, 87, 88, 89, 90, 91, 92, 109, 141, 176, 185, 188)
        ),
        RegionProfile(
            localeTag = "de-DE",
            countryIso = "DE",
            timeZoneId = "Europe/Berlin",
            networkOperator = "26202",
            networkOperatorName = "Vodafone DE",
            simOperator = "26202",
            simOperatorName = "Vodafone DE",
            dnsServers = listOf("9.9.9.9", "149.112.112.112", "1.1.1.1"),
            dnsSearchDomains = "fritz.box",
            privateDnsServerName = "dns.quad9.net",
            timeFormat = "24",
            androidReleaseOptions = listOf("14", "15"),
            wifiPrefixes = listOf("FRITZBox", "Vodafone", "Telekom", "WLAN"),
            ipPrefixes = listOf(2, 5, 31, 37, 46, 79, 80, 84, 85, 87, 91, 93, 95, 109, 134, 176, 178, 188)
        ),
        RegionProfile(
            localeTag = "en-IN",
            countryIso = "IN",
            timeZoneId = "Asia/Kolkata",
            networkOperator = "405874",
            networkOperatorName = "Jio",
            simOperator = "405874",
            simOperatorName = "Jio",
            dnsServers = listOf("1.1.1.1", "8.8.8.8", "9.9.9.9"),
            dnsSearchDomains = "airtelbroadband.in",
            privateDnsServerName = "dns.google",
            timeFormat = "12",
            androidReleaseOptions = listOf("14", "15"),
            wifiPrefixes = listOf("JioFiber", "Airtel", "ACTFibernet", "HomeNet"),
            ipPrefixes = listOf(14, 27, 42, 49, 59, 61, 101, 103, 106, 117, 122, 125, 157, 182)
        )
    )
}
