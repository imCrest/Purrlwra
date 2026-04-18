package cock.crest.purrfectsnap.lite.common.config.impl

import cock.crest.purrfectsnap.lite.common.config.ConfigContainer
import cock.crest.purrfectsnap.lite.common.config.ConfigFlag

class Spoof : ConfigContainer(hasGlobalState = true) {
    companion object {
        val supportedSnapchatLanguages = listOf(
            "ar", "bn", "bn-BD", "bn-IN", "da", "de", "el", "en-GB", "es", "es-AR", "es-ES", "es-MX",
            "fi", "fil", "fil-PH", "fr", "gu", "gu-IN", "hi", "hi-IN", "in", "it", "ja", "kn", "kn-IN",
            "ko", "ml", "ml-IN", "mr", "mr-IN", "ms", "ms-MY", "nb", "nl", "pa", "pa-IN", "pl", "pt",
            "pt-PT", "ro", "ru", "sv", "ta", "ta-IN", "te", "te-IN", "th", "th-TH", "tr", "ur", "ur-PK",
            "vi", "vi-VN", "zh", "zh-CN", "zh-TW"
        )
    }

    inner class RandomizedDeviceProfileConfig : ConfigContainer(hasGlobalState = true) {
        inner class RandomizedBuildVersionConfig : ConfigContainer(hasGlobalState = true) {
            val fingerprint = boolean("fingerprint", defaultValue = true) { requireRestart() }
            val display = boolean("display", defaultValue = true) { requireRestart() }
            val host = boolean("host", defaultValue = true) { requireRestart() }
            val bootloader = boolean("bootloader", defaultValue = true) { requireRestart() }
            val buildTime = boolean("build_time", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedDeviceIdentityConfig : ConfigContainer(hasGlobalState = true) {
            val manufacturerModel = boolean("manufacturer_model", defaultValue = true) { requireRestart() }
            val brandProduct = boolean("brand_product", defaultValue = true) { requireRestart() }
            val hardwareBoard = boolean("hardware_board", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedAbiListsConfig : ConfigContainer(hasGlobalState = true) {
            val combinedAbis = boolean("combined_abis", defaultValue = true) { requireRestart() }
            val splitAbis = boolean("split_abis", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedSystemPropertiesConfig : ConfigContainer(hasGlobalState = true) {
            val build = boolean("build", defaultValue = true) { requireRestart() }
            val locale = boolean("locale", defaultValue = true) { requireRestart() }
            val telephony = boolean("telephony", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedBuildPropertiesConfig : ConfigContainer(hasGlobalState = true) {
            val deviceIdentity = container("device_identity", RandomizedDeviceIdentityConfig().apply { globalState = true })
            val abiLists = container("abi_lists", RandomizedAbiListsConfig().apply { globalState = true })
            val systemProperties = container("system_properties", RandomizedSystemPropertiesConfig().apply { globalState = true })
            val buildVersion = container("build_version", RandomizedBuildVersionConfig().apply { globalState = true })
        }

        inner class RandomizedLocaleValueConfig : ConfigContainer(hasGlobalState = true) {
            val language = boolean("language", defaultValue = true) { requireRestart() }
            val region = boolean("region", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedTimeConfig : ConfigContainer(hasGlobalState = true) {
            val timeZoneId = boolean("time_zone_id", defaultValue = false) { requireRestart() }
            val timeZoneDisplayName = boolean("time_zone_display_name", defaultValue = false) { requireRestart() }
            val autoTime = boolean("auto_time", defaultValue = false) { requireRestart() }
            val autoTimeZone = boolean("auto_time_zone", defaultValue = false) { requireRestart() }
        }

        inner class RandomizedLocaleConfig : ConfigContainer(hasGlobalState = true) {
            val locale = container("locale", RandomizedLocaleValueConfig().apply { globalState = true })
            val time = container("time", RandomizedTimeConfig().apply { globalState = true })
        }

        inner class RandomizedMmsConfig : ConfigContainer(hasGlobalState = true) {
            val userAgent = boolean("user_agent", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedNetworkIdentityConfig : ConfigContainer(hasGlobalState = true) {
            val networkType = boolean("network_type", defaultValue = true) { requireRestart() }
            val operatorNumeric = boolean("operator_numeric", defaultValue = true) { requireRestart() }
            val operatorName = boolean("operator_name", defaultValue = true) { requireRestart() }
            val countryIso = boolean("country_iso", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedSimIdentityConfig : ConfigContainer(hasGlobalState = true) {
            val countryIso = boolean("country_iso", defaultValue = true) { requireRestart() }
            val operatorNumeric = boolean("operator_numeric", defaultValue = true) { requireRestart() }
            val operatorName = boolean("operator_name", defaultValue = true) { requireRestart() }
            val simState = boolean("sim_state", defaultValue = true) { requireRestart() }
            val hasIccCard = boolean("has_icc_card", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedPhoneCapabilitiesConfig : ConfigContainer(hasGlobalState = true) {
            val phoneCount = boolean("phone_count", defaultValue = true) { requireRestart() }
            val hearingAid = boolean("hearing_aid", defaultValue = true) { requireRestart() }
            val tty = boolean("tty", defaultValue = true) { requireRestart() }
            val worldPhone = boolean("world_phone", defaultValue = true) { requireRestart() }
            val roaming = boolean("roaming", defaultValue = true) { requireRestart() }
            val smsVoice = boolean("sms_voice", defaultValue = true) { requireRestart() }
            val phoneType = boolean("phone_type", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedTelephonyConfig : ConfigContainer(hasGlobalState = true) {
            val mms = container("mms", RandomizedMmsConfig().apply { globalState = true })
            val networkIdentity = container("network_identity", RandomizedNetworkIdentityConfig().apply { globalState = true })
            val simIdentity = container("sim_identity", RandomizedSimIdentityConfig().apply { globalState = true })
            val phoneCapabilities = container("phone_capabilities", RandomizedPhoneCapabilitiesConfig().apply { globalState = true })
        }

        inner class RandomizedSecureSettingsConfig : ConfigContainer(hasGlobalState = true) {
            val base = boolean("base", defaultValue = true) { requireRestart() }
            val tts = boolean("tts", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedSystemSettingsConfig : ConfigContainer(hasGlobalState = true) {
            val base = boolean("base", defaultValue = true) { requireRestart() }
            val bluetooth = boolean("bluetooth", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedGlobalSettingsConfig : ConfigContainer(hasGlobalState = true) {
            val base = boolean("base", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedSettingsConfig : ConfigContainer(hasGlobalState = true) {
            val secureSettings = container("secure_settings", RandomizedSecureSettingsConfig().apply { globalState = true })
            val systemSettings = container("system_settings", RandomizedSystemSettingsConfig().apply { globalState = true })
            val globalSettings = container("global_settings", RandomizedGlobalSettingsConfig().apply { globalState = true })
        }

        inner class RandomizedWifiConfig : ConfigContainer(hasGlobalState = true) {
            val ssid = boolean("ssid", defaultValue = true) { requireRestart() }
            val rssi = boolean("rssi", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedDnsConfig : ConfigContainer(hasGlobalState = true) {
            val servers = boolean("servers", defaultValue = true) { requireRestart() }
            val searchDomains = boolean("search_domains", defaultValue = true) { requireRestart() }
            val privateDns = boolean("private_dns", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedCaptivePortalConfig : ConfigContainer(hasGlobalState = true) {
            val capability = boolean("capability", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedNetworkConfig : ConfigContainer(hasGlobalState = true) {
            val wifi = container("wifi", RandomizedWifiConfig().apply { globalState = true })
            val dns = container("dns", RandomizedDnsConfig().apply { globalState = true })
            val captivePortal = container("captive_portal", RandomizedCaptivePortalConfig().apply { globalState = true })
        }

        inner class RandomizedAndroidIdConfig : ConfigContainer(hasGlobalState = true) {
            val stringValue = boolean("string_value", defaultValue = true) { requireRestart() }
            val longValue = boolean("long_value", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedAdvertisingIdConfig : ConfigContainer(hasGlobalState = true) {
            val settingsValue = boolean("settings_value", defaultValue = true) { requireRestart() }
            val playServices = boolean("play_services", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedHardwareAddressesConfig : ConfigContainer(hasGlobalState = true) {
            val wifiMac = boolean("wifi_mac", defaultValue = true) { requireRestart() }
            val bluetoothMac = boolean("bluetooth_mac", defaultValue = true) { requireRestart() }
        }

        inner class RandomizedIdentifiersConfig : ConfigContainer(hasGlobalState = true) {
            val androidId = container("android_id", RandomizedAndroidIdConfig().apply { globalState = true })
            val advertisingId = container("advertising_id", RandomizedAdvertisingIdConfig().apply { globalState = true })
            val hardwareAddresses = container("hardware_addresses", RandomizedHardwareAddressesConfig().apply { globalState = true })
        }

        val showActivationOverlay = boolean("show_activation_overlay", defaultValue = false)
        val randomizeIpAddress = boolean("randomize_ip_address", defaultValue = false) { requireRestart() }
        val buildProperties = container("build_properties", RandomizedBuildPropertiesConfig().apply { globalState = true })
        val localeOptions = container("locale_options", RandomizedLocaleConfig().apply { globalState = true })
        val telephonyOptions = container("telephony_options", RandomizedTelephonyConfig().apply { globalState = true })
        val settingsOptions = container("settings_options", RandomizedSettingsConfig().apply { globalState = true })
        val networkOptions = container("network_options", RandomizedNetworkConfig().apply { globalState = true })
        val identifierOptions = container("identifier_options", RandomizedIdentifiersConfig().apply { globalState = true })
        val persistentAppLanguage = unique("persistent_app_language", *supportedSnapchatLanguages.toTypedArray()) {
            requireRestart()
            addFlags(ConfigFlag.NO_TRANSLATE)
            disabledKey = "system_default"
            customOptionTranslationPath = "features.options.persistent_app_language"
        }
        val generateFreshProfileAction = string("generate_fresh_profile_action")
        val viewCurrentProfileAction = string("view_current_profile_action")
        val backupProfileAction = string("backup_profile_action")
        val restoreProfileAction = string("restore_profile_action")
        val profileGenerationToken = string("profile_generation_token") {
            addFlags(ConfigFlag.HIDDEN)
        }
        val currentProfileSnapshot = string("current_profile_snapshot") {
            addFlags(ConfigFlag.HIDDEN)
        }
    }

    inner class SpoofDeviceIdConfig : ConfigContainer() {
        val spoofAndroidId = boolean("spoof_android_id") { requireRestart(); addFlags(ConfigFlag.HIDDEN) }
        val customAndroidId = string("custom_android_id") {
            requireRestart()
            addFlags(ConfigFlag.HIDDEN)
            inputCheck = { it.isEmpty() || (it.length == 16 && it.all { c -> c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F' }) }
        }
    }

    val overridePlayStoreInstallerPackageName = boolean("play_store_installer_package_name") { requireRestart() }
    val removeVpnTransportFlag = boolean("remove_vpn_transport_flag") { requireRestart() }
    val removeMockLocationFlag = boolean("remove_mock_location_flag") { requireRestart() }
    val forceWifiTransportFlag = boolean("force_wifi_transport_flag") { requireRestart() }
    val randomizeDeviceProfile = container("randomize_device_profile", RandomizedDeviceProfileConfig()) { requireRestart() }
    val spoofDeviceId = container("spoof_device_id", SpoofDeviceIdConfig()) { requireRestart(); addFlags(ConfigFlag.HIDDEN) }
    val spoofDevice = boolean("spoof_device") { requireRestart(); addFlags(ConfigFlag.HIDDEN) }
    val deviceModel = unique("device_model",
        "none",
        "random",
        "Pixel 8 Pro",
        "Pixel 9 Pro XL",
        "Pixel 10",
        "Pixel 10 Pro",
        "Pixel 10 Pro XL",
        "Pixel 10 Pro Fold",
        "Galaxy S23 Ultra",
        "Galaxy S24 Ultra",
        "Galaxy S25 Ultra",
        "OnePlus 15",
        "OnePlus Open",
        "Xiaomi 15 Ultra",
        "OPPO Find X9 Pro",
        "vivo X100 Pro",
        "realme GT 6"
    ) {
        requireRestart()
        addFlags(ConfigFlag.HIDDEN)
        customOptionTranslationPath = "features.options.device_model"
    }
}
