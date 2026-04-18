package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import android.annotation.SuppressLint
import android.location.Location
import android.net.ConnectivityManager
import android.net.LinkAddress
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Build
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Wifi
import cock.crest.purrfectsnap.lite.core.features.Feature
import cock.crest.purrfectsnap.lite.core.util.LSPatchUpdater
import cock.crest.purrfectsnap.lite.core.util.hook.HookStage
import cock.crest.purrfectsnap.lite.core.util.hook.hook
import java.lang.reflect.Modifier
import java.net.InetAddress
import java.security.SecureRandom
import java.util.Locale
import java.util.TimeZone
import cock.crest.purrfectsnap.lite.common.config.ModConfig

class DeviceSpooferHook : Feature("Device Spoofer") {
    private var spoofedAndroidId: String? = null
    private var spoofedDeviceInfo: DeviceInfo? = null
    private var spoofedFingerprint: String? = null
    private var randomizedProfile: RandomizedDeviceProfile? = null

    private fun spoofPrefs() = context.androidContext.getSharedPreferences("purrfectsnap_spoof", 0)

    private fun generateAndroidId(): String {
        val customId = context.config.experimental.spoof.spoofDeviceId.customAndroidId.getNullable()
        if (!customId.isNullOrEmpty()) {
            val normalizedId = customId.lowercase().trim()
            if (normalizedId.length == 16 && normalizedId.all { it in '0'..'9' || it in 'a'..'f' }) {
                if (spoofedAndroidId != normalizedId) {
                    spoofedAndroidId = normalizedId
                    context.log.info("Using custom Android ID: $spoofedAndroidId")
                }
                return spoofedAndroidId!!
            } else {
                context.log.warn("Invalid custom Android ID format (must be 16 hex chars), generating new one")
            }
        }

        val sharedPrefs = spoofPrefs()
        val storedId = sharedPrefs.getString("android_id", null)
        if (storedId == null || storedId.length != 16) {
            spoofedAndroidId = DeviceSpoofer.generateAndroidId()
            sharedPrefs.edit().putString("android_id", spoofedAndroidId).apply()
            context.log.info("Generated new Android ID: $spoofedAndroidId")
        } else if (spoofedAndroidId != storedId) {
            spoofedAndroidId = storedId
            context.log.info("Using stored Android ID: $spoofedAndroidId")
        }
        return spoofedAndroidId!!
    }

    private fun getDeviceInfo(modelName: String): DeviceInfo? = DeviceSpoofer.getDeviceInfo(modelName)

    private fun getSpoofedDeviceInfo(): DeviceInfo? {
        val selectedModel = context.config.experimental.spoof.deviceModel.getNullable() ?: return null
        if (selectedModel == "random") {
            val sharedPrefs = spoofPrefs()
            val randomDevice = sharedPrefs.getString("random_device", null)
            if (randomDevice == null) {
                val availableDevices = DeviceSpoofer.getAvailableDevices()
                val newRandomDevice = availableDevices.random()
                sharedPrefs.edit().putString("random_device", newRandomDevice).apply()
                context.log.info("Randomly selected device: $newRandomDevice")
                spoofedDeviceInfo = getDeviceInfo(newRandomDevice)
            } else {
                context.log.info("Using stored random device: $randomDevice")
                spoofedDeviceInfo = getDeviceInfo(randomDevice)
            }
            return spoofedDeviceInfo
        }
        if (selectedModel == "none" || selectedModel == "null") return null
        spoofedDeviceInfo = getDeviceInfo(selectedModel)
        return spoofedDeviceInfo
    }

    private fun getSpoofedFingerprint(deviceInfo: DeviceInfo): String {
        val sharedPrefs = spoofPrefs()
        val storedFingerprint = sharedPrefs.getString("device_fingerprint", null)
        if (storedFingerprint == null) {
            val buildVersion = Build.VERSION.RELEASE
            spoofedFingerprint = DeviceSpoofer.generateFingerprint(deviceInfo, buildVersion)
            sharedPrefs.edit().putString("device_fingerprint", spoofedFingerprint).apply()
            context.log.info("Generated new device fingerprint: $spoofedFingerprint")
        } else {
            spoofedFingerprint = storedFingerprint
            context.log.info("Using stored device fingerprint: $spoofedFingerprint")
        }
        return spoofedFingerprint!!
    }

    private fun getRandomGsfId(): String {
        val sharedPrefs = spoofPrefs()
        val savedGsfId = sharedPrefs.getString("gsf_id", null)
        if (savedGsfId != null) return savedGsfId
        val random = SecureRandom()
        val gsfId = (1..16).map {
            "0123456789abcdef"[random.nextInt(16)]
        }.joinToString("")
        sharedPrefs.edit().putString("gsf_id", gsfId).apply()
        return gsfId
    }

    private fun generateRandomUUID(): String {
        val sharedPrefs = spoofPrefs()
        val savedUuid = sharedPrefs.getString("advertising_id", null)
        if (savedUuid != null) return savedUuid
        val random = SecureRandom()
        val uuid = "%08x-%04x-%04x-%04x-%012x".format(
            random.nextInt(),
            random.nextInt() and 0xFFFF,
            (random.nextInt() and 0x0FFF) or 0x4000,
            (random.nextInt() and 0x3FFF) or 0x8000,
            random.nextLong() and 0xFFFFFFFFFFFFL
        )
        sharedPrefs.edit().putString("advertising_id", uuid).apply()
        return uuid
    }

    private fun generateRandomMacAddress(): String {
        val sharedPrefs = spoofPrefs()
        val savedMac = sharedPrefs.getString("bluetooth_address", null)
        if (savedMac != null) return savedMac
        val random = SecureRandom()
        val mac = (1..6).map {
            "%02x".format(random.nextInt(256))
        }.joinToString(":")
        sharedPrefs.edit().putString("bluetooth_address", mac).apply()
        return mac
    }

    private fun hookInstallerPackageName() {
        context.androidContext.packageManager::class.java.hook("getInstallerPackageName", HookStage.BEFORE) { param ->
            param.setResult("com.android.vending")
        }
    }

    private fun getRandomizedProfile(): RandomizedDeviceProfile {
        val generationToken = context.config.experimental.spoof.randomizeDeviceProfile.profileGenerationToken.getNullable()
        return randomizedProfile ?: RandomizedDeviceProfileStore
            .getOrCreate(context.androidContext, context.log, generationToken)
            .also { profile ->
                randomizedProfile = profile
                persistRandomizedProfileSnapshot(profile)
            }
    }

    private fun persistRandomizedProfileSnapshot(profile: RandomizedDeviceProfile) {
        val spoofConfig = context.config.experimental.spoof.randomizeDeviceProfile
        val snapshot = profile.toJson().toString(2)
        if (spoofConfig.currentProfileSnapshot.getNullable() == snapshot) return
        spoofConfig.currentProfileSnapshot.set(snapshot)
        runCatching {
            val field = context.javaClass.getDeclaredField("_config\$delegate")
            field.isAccessible = true
            val lazyConfig = field.get(context) as Lazy<*>
            val modConfig = lazyConfig.value as? ModConfig ?: return@runCatching
            modConfig.writeConfig(dispatchConfigListener = false)
            context.log.verbose("Persisted randomized device profile snapshot to config")
        }.onFailure {
            context.log.warn("Failed to persist randomized device profile snapshot: ${it.message}")
        }
    }

    private fun androidIdAsLong(androidId: String): Long {
        return runCatching { androidId.toULong(16).toLong() }.getOrElse { androidId.hashCode().toLong() }
    }

    private fun getEffectiveRandomizedLocale(profile: RandomizedDeviceProfile): Locale {
        val forcedLanguage = context.config.experimental.spoof.randomizeDeviceProfile.persistentAppLanguage.getNullable()
        return forcedLanguage?.let(Locale::forLanguageTag) ?: profile.locale()
    }

    private data class RandomizedProfileToggleState(
        val buildProperties: Boolean,
        val locale: Boolean,
        val telephony: Boolean,
        val settings: Boolean,
        val network: Boolean,
        val identifiers: Boolean
    )

    private data class RandomizedBuildToggleState(
        val deviceIdentity: Boolean,
        val abiLists: Boolean,
        val systemProperties: Boolean,
        val fingerprint: Boolean,
        val display: Boolean,
        val host: Boolean,
        val bootloader: Boolean,
        val buildTime: Boolean
    )

    private data class RandomizedLocaleToggleState(
        val locale: Boolean,
        val timeZone: Boolean,
        val autoTime: Boolean,
        val autoTimeZone: Boolean
    )

    private data class RandomizedTelephonyToggleState(
        val mmsUserAgent: Boolean,
        val networkIdentity: Boolean,
        val simIdentity: Boolean,
        val phoneCapabilities: Boolean
    )

    private data class RandomizedSettingsToggleState(
        val secure: Boolean,
        val system: Boolean,
        val global: Boolean
    )

    private data class RandomizedNetworkToggleState(
        val wifi: Boolean,
        val dns: Boolean,
        val captivePortal: Boolean,
        val ipAddress: Boolean
    )

    private data class RandomizedIdentifierToggleState(
        val androidId: Boolean,
        val advertisingId: Boolean,
        val hardwareAddresses: Boolean
    )

    private fun getRandomizedProfileToggleState(): RandomizedProfileToggleState {
        val config = context.config.experimental.spoof.randomizeDeviceProfile
        return RandomizedProfileToggleState(
            buildProperties = config.buildProperties.globalState == true,
            locale = config.localeOptions.globalState == true,
            telephony = config.telephonyOptions.globalState == true,
            settings = config.settingsOptions.globalState == true,
            network = config.networkOptions.globalState == true || config.randomizeIpAddress.get(),
            identifiers = config.identifierOptions.globalState == true
        )
    }

    private fun getRandomizedBuildToggleState(): RandomizedBuildToggleState {
        val config = context.config.experimental.spoof.randomizeDeviceProfile.buildProperties
        val buildVersion = config.buildVersion
        val deviceIdentity = config.deviceIdentity
        val abiLists = config.abiLists
        val systemProperties = config.systemProperties
        return RandomizedBuildToggleState(
            deviceIdentity = deviceIdentity.globalState == true &&
                (deviceIdentity.manufacturerModel.get() || deviceIdentity.brandProduct.get() || deviceIdentity.hardwareBoard.get()),
            abiLists = abiLists.globalState == true && (abiLists.combinedAbis.get() || abiLists.splitAbis.get()),
            systemProperties = systemProperties.globalState == true &&
                (systemProperties.build.get() || systemProperties.locale.get() || systemProperties.telephony.get()),
            fingerprint = buildVersion.globalState == true && buildVersion.fingerprint.get(),
            display = buildVersion.globalState == true && buildVersion.display.get(),
            host = buildVersion.globalState == true && buildVersion.host.get(),
            bootloader = buildVersion.globalState == true && buildVersion.bootloader.get(),
            buildTime = buildVersion.globalState == true && buildVersion.buildTime.get()
        )
    }

    private fun getRandomizedLocaleToggleState(): RandomizedLocaleToggleState {
        val config = context.config.experimental.spoof.randomizeDeviceProfile.localeOptions
        val locale = config.locale
        val time = config.time
        return RandomizedLocaleToggleState(
            locale = locale.globalState == true && (locale.language.get() || locale.region.get()),
            timeZone = time.globalState == true && (time.timeZoneId.get() || time.timeZoneDisplayName.get()),
            autoTime = time.globalState == true && time.autoTime.get(),
            autoTimeZone = time.globalState == true && time.autoTimeZone.get()
        )
    }

    private fun getRandomizedTelephonyToggleState(): RandomizedTelephonyToggleState {
        val config = context.config.experimental.spoof.randomizeDeviceProfile.telephonyOptions
        val mms = config.mms
        val networkIdentity = config.networkIdentity
        val simIdentity = config.simIdentity
        val phoneCapabilities = config.phoneCapabilities
        return RandomizedTelephonyToggleState(
            mmsUserAgent = mms.globalState == true && mms.userAgent.get(),
            networkIdentity = networkIdentity.globalState == true &&
                (networkIdentity.networkType.get() || networkIdentity.operatorNumeric.get() || networkIdentity.operatorName.get() || networkIdentity.countryIso.get()),
            simIdentity = simIdentity.globalState == true &&
                (simIdentity.countryIso.get() || simIdentity.operatorNumeric.get() || simIdentity.operatorName.get() || simIdentity.simState.get() || simIdentity.hasIccCard.get()),
            phoneCapabilities = phoneCapabilities.globalState == true &&
                (phoneCapabilities.phoneCount.get() || phoneCapabilities.hearingAid.get() || phoneCapabilities.tty.get() ||
                    phoneCapabilities.worldPhone.get() || phoneCapabilities.roaming.get() || phoneCapabilities.smsVoice.get() || phoneCapabilities.phoneType.get())
        )
    }

    private fun getRandomizedSettingsToggleState(): RandomizedSettingsToggleState {
        val config = context.config.experimental.spoof.randomizeDeviceProfile.settingsOptions
        val secure = config.secureSettings
        val system = config.systemSettings
        val global = config.globalSettings
        return RandomizedSettingsToggleState(
            secure = secure.globalState == true && (secure.base.get() || secure.tts.get()),
            system = system.globalState == true && (system.base.get() || system.bluetooth.get()),
            global = global.globalState == true && global.base.get()
        )
    }

    private fun getRandomizedNetworkToggleState(): RandomizedNetworkToggleState {
        val config = context.config.experimental.spoof.randomizeDeviceProfile.networkOptions
        val wifi = config.wifi
        val dns = config.dns
        val captivePortal = config.captivePortal
        return RandomizedNetworkToggleState(
            wifi = wifi.globalState == true && (wifi.ssid.get() || wifi.rssi.get()),
            dns = dns.globalState == true && (dns.servers.get() || dns.searchDomains.get() || dns.privateDns.get()),
            captivePortal = captivePortal.globalState == true && captivePortal.capability.get(),
            ipAddress = context.config.experimental.spoof.randomizeDeviceProfile.randomizeIpAddress.get()
        )
    }

    private fun getRandomizedIdentifierToggleState(): RandomizedIdentifierToggleState {
        val config = context.config.experimental.spoof.randomizeDeviceProfile.identifierOptions
        val androidId = config.androidId
        val advertisingId = config.advertisingId
        val hardwareAddresses = config.hardwareAddresses
        return RandomizedIdentifierToggleState(
            androidId = androidId.globalState == true && (androidId.stringValue.get() || androidId.longValue.get()),
            advertisingId = advertisingId.globalState == true && (advertisingId.settingsValue.get() || advertisingId.playServices.get()),
            hardwareAddresses = hardwareAddresses.globalState == true && (hardwareAddresses.wifiMac.get() || hardwareAddresses.bluetoothMac.get())
        )
    }

    private fun applyBuildFieldOverrides(
        manufacturer: String,
        model: String,
        brand: String,
        device: String,
        product: String,
        hardware: String,
        board: String,
        bootloader: String,
        display: String,
        host: String,
        fingerprint: String,
        buildTime: Long,
        overrideDeviceIdentity: Boolean = true,
        overrideAbiLists: Boolean = true,
        overrideFingerprint: Boolean = true,
        overrideDisplay: Boolean = true,
        overrideHost: Boolean = true,
        overrideBootloader: Boolean = true,
        overrideBuildTime: Boolean = true,
        buildIncremental: String? = null,
        buildRelease: String? = null,
        supportedAbis: List<String>? = null,
        supported32BitAbis: List<String>? = null,
        supported64BitAbis: List<String>? = null
    ) {
        val safeSupportedAbis = supportedAbis?.takeIf { it.isNotEmpty() } ?: (Build.SUPPORTED_ABIS?.toList() ?: emptyList())
        val safeSupported32BitAbis = supported32BitAbis?.takeIf { it.isNotEmpty() } ?: (Build.SUPPORTED_32_BIT_ABIS?.toList() ?: emptyList())
        val safeSupported64BitAbis = supported64BitAbis?.takeIf { it.isNotEmpty() } ?: (Build.SUPPORTED_64_BIT_ABIS?.toList() ?: emptyList())

        Build::class.java.fields.forEach { field ->
            if (!field.isAccessible) field.isAccessible = true
            runCatching {
                val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
            }
            when (field.name) {
                "MANUFACTURER" -> if (overrideDeviceIdentity) field.set(null, manufacturer)
                "MODEL" -> if (overrideDeviceIdentity) field.set(null, model)
                "BRAND" -> if (overrideDeviceIdentity) field.set(null, brand)
                "DEVICE" -> if (overrideDeviceIdentity) field.set(null, device)
                "PRODUCT" -> if (overrideDeviceIdentity) field.set(null, product)
                "HARDWARE" -> if (overrideDeviceIdentity) field.set(null, hardware)
                "FINGERPRINT" -> if (overrideFingerprint) field.set(null, fingerprint)
                "BOARD" -> if (overrideDeviceIdentity) runCatching { field.set(null, board) }
                "BOOTLOADER" -> if (overrideBootloader) runCatching { field.set(null, bootloader) }
                "DISPLAY" -> if (overrideDisplay) runCatching { field.set(null, display) }
                "HOST" -> if (overrideHost) runCatching { field.set(null, host) }
                "TIME" -> if (overrideBuildTime) runCatching { field.setLong(null, buildTime) }
                "SUPPORTED_ABIS" -> if (overrideAbiLists) runCatching { field.set(null, safeSupportedAbis.toTypedArray()) }
                "SUPPORTED_32_BIT_ABIS" -> if (overrideAbiLists) runCatching { field.set(null, safeSupported32BitAbis.toTypedArray()) }
                "SUPPORTED_64_BIT_ABIS" -> if (overrideAbiLists) runCatching { field.set(null, safeSupported64BitAbis.toTypedArray()) }
            }
        }

        Build.VERSION::class.java.fields.forEach { field ->
            if (!field.isAccessible) field.isAccessible = true
            runCatching {
                val modifiersField = java.lang.reflect.Field::class.java.getDeclaredField("modifiers")
                modifiersField.isAccessible = true
                modifiersField.setInt(field, field.modifiers and Modifier.FINAL.inv())
            }
            when (field.name) {
                "RELEASE" -> {}
                "INCREMENTAL" -> {}
            }
        }
    }

    private fun installSystemPropertyHooks(
        profile: RandomizedDeviceProfile,
        buildToggles: RandomizedBuildToggleState,
        localeToggles: RandomizedLocaleToggleState,
        telephonyToggles: RandomizedTelephonyToggleState
    ) {
        val effectiveLocale = getEffectiveRandomizedLocale(profile)
        runCatching {
            findClass("android.os.SystemProperties").hook("get", HookStage.BEFORE) { param ->
                when (val key = param.argNullable<String>(0) ?: return@hook) {
                    "ro.product.manufacturer" -> if (buildToggles.deviceIdentity) param.setResult(profile.deviceInfo.manufacturer) else return@hook
                    "ro.product.model" -> if (buildToggles.deviceIdentity) param.setResult(profile.deviceInfo.model) else return@hook
                    "ro.product.brand" -> if (buildToggles.deviceIdentity) param.setResult(profile.deviceInfo.brand) else return@hook
                    "ro.product.device" -> if (buildToggles.deviceIdentity) param.setResult(profile.deviceInfo.device) else return@hook
                    "ro.product.name" -> if (buildToggles.deviceIdentity) param.setResult(profile.deviceInfo.product) else return@hook
                    "ro.product.board" -> if (buildToggles.deviceIdentity) param.setResult(profile.deviceInfo.board) else return@hook
                    "ro.hardware" -> if (buildToggles.deviceIdentity) param.setResult(profile.deviceInfo.hardware) else return@hook
                    "ro.build.fingerprint" -> if (buildToggles.fingerprint) param.setResult(profile.buildFingerprint) else return@hook
                    "ro.build.id" -> if (buildToggles.display) param.setResult(profile.buildDisplayId) else return@hook
                    "ro.build.display.id" -> if (buildToggles.display) param.setResult(profile.buildDisplayId) else return@hook
                    "ro.build.version.incremental" -> return@hook
                    "ro.build.version.release" -> return@hook
                    "ro.bootloader" -> if (buildToggles.bootloader) param.setResult(profile.deviceInfo.bootloader) else return@hook
                    "persist.sys.locale" -> if (localeToggles.locale) param.setResult(effectiveLocale.toLanguageTag()) else return@hook
                    "ro.product.locale" -> if (localeToggles.locale) param.setResult(effectiveLocale.toLanguageTag()) else return@hook
                    "ro.product.locale.language" -> if (localeToggles.locale) param.setResult(effectiveLocale.language) else return@hook
                    "ro.product.locale.region" -> if (localeToggles.locale) param.setResult(effectiveLocale.country) else return@hook
                    "persist.sys.timezone" -> if (localeToggles.timeZone) param.setResult(profile.timeZoneId) else return@hook
                    "gsm.operator.alpha" -> if (telephonyToggles.networkIdentity) param.setResult(profile.networkOperatorName) else return@hook
                    "gsm.operator.numeric" -> if (telephonyToggles.networkIdentity) param.setResult(profile.networkOperator) else return@hook
                    "gsm.sim.operator.alpha" -> if (telephonyToggles.simIdentity) param.setResult(profile.simOperatorName) else return@hook
                    "gsm.sim.operator.numeric" -> if (telephonyToggles.simIdentity) param.setResult(profile.simOperator) else return@hook
                    "gsm.sim.operator.iso-country" -> if (telephonyToggles.simIdentity) param.setResult(profile.simCountryIso) else return@hook
                    else -> return@hook
                }
                if (key.startsWith("gsm.") || key.startsWith("persist.sys.") || key.startsWith("ro.")) {
                    context.log.verbose("Random profile SystemProperties override: $key")
                }
            }
            context.log.info("Randomized profile SystemProperties hooks installed")
        }.onFailure {
            context.log.warn("Failed to hook randomized SystemProperties: ${it.message}")
        }
    }

    private fun installLocaleHooks(profile: RandomizedDeviceProfile, toggles: RandomizedLocaleToggleState) {
        val effectiveLocale = getEffectiveRandomizedLocale(profile)
        runCatching {
            if (toggles.locale) {
                Locale::class.java.hook("getDefault", HookStage.BEFORE) { param ->
                    param.setResult(effectiveLocale)
                }
            }
            if (toggles.timeZone) {
                TimeZone::class.java.hook("getDefault", HookStage.BEFORE) { param ->
                    param.setResult(profile.timeZone())
                }
            }
            context.log.info("Randomized profile locale/timezone hooks installed")
        }.onFailure {
            context.log.warn("Failed to hook locale/timezone for randomized profile: ${it.message}")
        }
    }

    private fun installTelephonyHooks(profile: RandomizedDeviceProfile, toggles: RandomizedTelephonyToggleState) {
        runCatching {
            findClass("android.telephony.TelephonyManager").apply {
                if (toggles.mmsUserAgent) {
                    hook("getMmsUserAgent", HookStage.BEFORE) { it.setResult(profile.mmsUserAgent) }
                }
                if (toggles.networkIdentity) {
                    hook("getNetworkType", HookStage.BEFORE) { it.setResult(profile.networkType) }
                    hook("getNetworkOperator", HookStage.BEFORE) { it.setResult(profile.networkOperator) }
                    hook("getNetworkOperatorName", HookStage.BEFORE) { it.setResult(profile.networkOperatorName) }
                    hook("getNetworkCountryIso", HookStage.BEFORE) { it.setResult(profile.networkCountryIso) }
                }
                if (toggles.simIdentity) {
                    hook("getSimCountryIso", HookStage.BEFORE) { it.setResult(profile.simCountryIso) }
                    hook("getSimOperator", HookStage.BEFORE) { it.setResult(profile.simOperator) }
                    hook("getSimOperatorName", HookStage.BEFORE) { it.setResult(profile.simOperatorName) }
                    hook("getSimState", HookStage.BEFORE) { it.setResult(profile.simState) }
                    hook("hasIccCard", HookStage.BEFORE) { it.setResult(profile.hasIccCard) }
                }
                if (toggles.phoneCapabilities) {
                    hook("getPhoneCount", HookStage.BEFORE) { it.setResult(profile.phoneCount) }
                    hook("isHearingAidCompatibilitySupported", HookStage.BEFORE) {
                        it.setResult(profile.isHearingAidCompatibilitySupported)
                    }
                    hook("isTtyModeSupported", HookStage.BEFORE) { it.setResult(profile.isTtySupported) }
                    hook("isWorldPhone", HookStage.BEFORE) { it.setResult(profile.isWorldPhone) }
                    hook("isNetworkRoaming", HookStage.BEFORE) { it.setResult(profile.isNetworkRoaming) }
                    hook("isSmsCapable", HookStage.BEFORE) { it.setResult(profile.isSmsCapable) }
                    hook("isVoiceCapable", HookStage.BEFORE) { it.setResult(profile.isVoiceCapable) }
                    hook("getPhoneType", HookStage.BEFORE) { it.setResult(profile.phoneType) }
                }
            }
            context.log.info("Randomized profile telephony hooks installed")
        }.onFailure {
            context.log.warn("Failed to hook telephony for randomized profile: ${it.message}")
        }
    }

    private fun installSettingsHooks(
        profile: RandomizedDeviceProfile,
        settingToggles: RandomizedSettingsToggleState,
        identifierToggles: RandomizedIdentifierToggleState,
        localeToggles: RandomizedLocaleToggleState
    ) {
        fun hookStringSettings(className: String, values: Map<String, String>) {
            findClass(className).hook("getString", HookStage.BEFORE) { param ->
                val key = param.argNullable<String>(1) ?: return@hook
                values[key]?.let { param.setResult(it) }
            }
        }

        fun hookIntSettings(className: String, values: Map<String, Int>) {
            findClass(className).hook("getInt", HookStage.BEFORE) { param ->
                val key = param.argNullable<String>(1) ?: return@hook
                values[key]?.let { param.setResult(it) }
            }
        }

        runCatching {
            if (settingToggles.secure || identifierToggles.androidId || identifierToggles.advertisingId || identifierToggles.hardwareAddresses) {
                val secureStrings = buildMap {
                    if (settingToggles.secure) putAll(profile.secureStringSettings)
                    if (identifierToggles.androidId) put("android_id", profile.androidId)
                    if (identifierToggles.advertisingId) put("advertising_id", profile.advertisingId)
                    if (identifierToggles.hardwareAddresses) put("bluetooth_address", profile.bluetoothMacAddress)
                }
                hookStringSettings("android.provider.Settings\$Secure", secureStrings)
            }
            if (settingToggles.secure) {
                hookIntSettings("android.provider.Settings\$Secure", profile.secureIntSettings)
            }
            if (identifierToggles.androidId) {
                findClass("android.provider.Settings\$Secure").hook("getLong", HookStage.BEFORE) { param ->
                    val key = param.argNullable<String>(1) ?: return@hook
                    if (key == "android_id") {
                        param.setResult(androidIdAsLong(profile.androidId))
                    }
                }
            }

            if (settingToggles.system) {
                hookStringSettings("android.provider.Settings\$System", profile.systemStringSettings)
                hookIntSettings("android.provider.Settings\$System", profile.systemIntSettings)
            }

            val globalStrings = buildMap {
                if (settingToggles.global) {
                    putAll(profile.globalStringSettings.filterKeys { it != "auto_time" && it != "auto_time_zone" })
                }
                if (localeToggles.autoTime) {
                    put("auto_time", profile.globalStringSettings["auto_time"] ?: "1")
                }
                if (localeToggles.autoTimeZone) {
                    put("auto_time_zone", profile.globalStringSettings["auto_time_zone"] ?: "1")
                }
            }
            if (globalStrings.isNotEmpty()) {
                hookStringSettings("android.provider.Settings\$Global", globalStrings)
            }
            if (settingToggles.global) {
                hookIntSettings("android.provider.Settings\$Global", profile.globalIntSettings)
            }
            context.log.info("Randomized profile settings hooks installed")
        }.onFailure {
            context.log.warn("Failed to hook settings for randomized profile: ${it.message}")
        }
    }

    private fun installNetworkHooks(
        profile: RandomizedDeviceProfile,
        networkToggles: RandomizedNetworkToggleState,
        identifierToggles: RandomizedIdentifierToggleState
    ) {
        if (networkToggles.wifi || identifierToggles.hardwareAddresses) {
            runCatching {
                findClass("android.net.wifi.WifiInfo").apply {
                    if (networkToggles.wifi) {
                        hook("getSSID", HookStage.BEFORE) { it.setResult("\"${profile.wifiSsid}\"") }
                        hook("getRssi", HookStage.BEFORE) { it.setResult(profile.wifiRssi) }
                    }
                    if (identifierToggles.hardwareAddresses) {
                        hook("getMacAddress", HookStage.BEFORE) { it.setResult(profile.wifiMacAddress) }
                    }
                }
            }
        }

        if (identifierToggles.hardwareAddresses) {
            runCatching {
                findClass("android.bluetooth.BluetoothAdapter").hook("getAddress", HookStage.BEFORE) {
                    it.setResult(profile.bluetoothMacAddress)
                }
            }
        }

        if (networkToggles.dns) {
            runCatching {
                LinkProperties::class.java.apply {
                    hook("getDnsServers", HookStage.BEFORE) { param ->
                        param.setResult(profile.dnsServers.map { InetAddress.getByName(it) })
                    }
                    hook("getDomains", HookStage.BEFORE) { param ->
                        param.setResult(profile.dnsSearchDomains)
                    }
                    hook("isPrivateDnsActive", HookStage.BEFORE) { param ->
                        param.setResult(profile.privateDnsActive)
                    }
                    hook("getPrivateDnsServerName", HookStage.BEFORE) { param ->
                        param.setResult(profile.privateDnsServerName)
                    }
                }
            }
        }

        if (networkToggles.ipAddress) {
            runCatching {
                val spoofedAddress = InetAddress.getByName(profile.ipAddress)
                val spoofedLinkAddress = runCatching {
                    LinkAddress::class.java.getConstructor(String::class.java)
                        .newInstance("${profile.ipAddress}/24")
                }.getOrNull()
                LinkProperties::class.java.apply {
                    hook("getAddresses", HookStage.BEFORE) { param ->
                        param.setResult(listOf(spoofedAddress))
                    }
                    spoofedLinkAddress?.let { linkAddress ->
                        hook("getLinkAddresses", HookStage.BEFORE) { param ->
                            param.setResult(listOf(linkAddress))
                        }
                    }
                }
                context.log.info("Randomized profile IP override active: ${profile.ipAddress}")
            }.onFailure {
                context.log.warn("Failed to hook randomized IP address: ${it.message}")
            }
        }

        if (networkToggles.captivePortal) {
            runCatching {
                NetworkCapabilities::class.java.hook("hasCapability", HookStage.BEFORE) { param ->
                    val capability = param.argNullable<Int>(0) ?: return@hook
                    if (capability == NetworkCapabilities.NET_CAPABILITY_CAPTIVE_PORTAL) {
                        param.setResult(profile.hasCaptivePortal)
                    }
                }
            }
        }

        if (identifierToggles.advertisingId) {
            val advertisingInfoClass = runCatching {
                findClass("com.google.android.gms.ads.identifier.AdvertisingIdClient\$Info")
            }.getOrNull()
            if (advertisingInfoClass != null) {
                advertisingInfoClass.apply {
                    hook("getId", HookStage.BEFORE) { it.setResult(profile.advertisingId) }
                    hook("isLimitAdTrackingEnabled", HookStage.BEFORE) { it.setResult(false) }
                }
                context.log.info("Randomized profile network/identifier hooks installed")
            }
        }
    }

    private fun installRandomizedProfileHooks(profile: RandomizedDeviceProfile) {
        val toggles = getRandomizedProfileToggleState()
        val buildToggles = getRandomizedBuildToggleState()
        val localeToggles = getRandomizedLocaleToggleState()
        val telephonyToggles = getRandomizedTelephonyToggleState()
        val settingsToggles = getRandomizedSettingsToggleState()
        val networkToggles = getRandomizedNetworkToggleState()
        val identifierToggles = getRandomizedIdentifierToggleState()
        if (toggles.buildProperties) {
            applyBuildFieldOverrides(
                manufacturer = profile.deviceInfo.manufacturer,
                model = profile.deviceInfo.model,
                brand = profile.deviceInfo.brand,
                device = profile.deviceInfo.device,
                product = profile.deviceInfo.product,
                hardware = profile.deviceInfo.hardware,
                board = profile.deviceInfo.board,
                bootloader = profile.deviceInfo.bootloader,
                display = profile.buildDisplayId,
                host = profile.buildHost,
                fingerprint = profile.buildFingerprint,
                buildTime = profile.buildTime,
                overrideDeviceIdentity = buildToggles.deviceIdentity,
                overrideAbiLists = buildToggles.abiLists,
                overrideFingerprint = buildToggles.fingerprint,
                overrideDisplay = buildToggles.display,
                overrideHost = buildToggles.host,
                overrideBootloader = buildToggles.bootloader,
                overrideBuildTime = buildToggles.buildTime,
                buildIncremental = profile.buildIncremental,
                buildRelease = profile.androidRelease,
                supportedAbis = profile.supportedAbis,
                supported32BitAbis = profile.supported32BitAbis,
                supported64BitAbis = profile.supported64BitAbis
            )
            if (buildToggles.systemProperties || toggles.locale || toggles.telephony) {
                installSystemPropertyHooks(profile, buildToggles, localeToggles, telephonyToggles)
            }
        }
        if (toggles.locale && (localeToggles.locale || localeToggles.timeZone)) {
            installLocaleHooks(profile, localeToggles)
        }
        if (toggles.telephony) {
            installTelephonyHooks(profile, telephonyToggles)
        }
        if (toggles.settings || toggles.identifiers) {
            installSettingsHooks(profile, settingsToggles, identifierToggles, localeToggles)
        }
        if (toggles.network || toggles.identifiers) {
            installNetworkHooks(profile, networkToggles, identifierToggles)
        }
        context.log.info(
            "Randomized profile active: ${profile.deviceInfo.manufacturer} ${profile.deviceInfo.model}, " +
                "androidId=${profile.androidId}, operator=${profile.simOperatorName}, locale=${profile.localeTag}"
        )
    }

    @SuppressLint("MissingPermission")
    override fun init() {
        val randomizeDeviceProfile by context.config.experimental.spoof.randomizeDeviceProfile
        val spoofDevice by context.config.experimental.spoof.spoofDevice
        val randomizeDeviceProfileEnabled = randomizeDeviceProfile == true

        if (!randomizeDeviceProfileEnabled && spoofDevice) {
            val deviceInfo = getSpoofedDeviceInfo()
            if (deviceInfo != null) {
                getSpoofedFingerprint(deviceInfo)
                context.log.info("Device spoofing initialized: ${deviceInfo.manufacturer} ${deviceInfo.model}")
            }
        }

        if (LSPatchUpdater.HAS_LSPATCH) {
            hookInstallerPackageName()
        }

        if (context.config.experimental.spoof.globalState != true) return

        if (randomizeDeviceProfileEnabled) {
            val profile = getRandomizedProfile()
            installRandomizedProfileHooks(profile)
            if (context.config.experimental.spoof.randomizeDeviceProfile.showActivationOverlay.get()) {
                onNextActivityCreate {
                    context.inAppOverlay.showStatusToast(
                        icon = Icons.Filled.CheckCircle,
                        text = "Randomized device profile active",
                        durationMs = 3200
                    )
                }
            }
        }

        val removeMockLocationFlag by context.config.experimental.spoof.removeMockLocationFlag
        val overridePlayStoreInstallerPackageName by context.config.experimental.spoof.overridePlayStoreInstallerPackageName
        val removeVpnTransportFlag by context.config.experimental.spoof.removeVpnTransportFlag
        val forceWifiTransportFlag by context.config.experimental.spoof.forceWifiTransportFlag
        val networkOptimization by context.config.experimental.networkOptimization
        val spoofAndroidId by context.config.experimental.spoof.spoofDeviceId.spoofAndroidId

        if (overridePlayStoreInstallerPackageName) {
            hookInstallerPackageName()
        }

        if (removeMockLocationFlag) {
            Location::class.java.hook("isMock", HookStage.BEFORE) { param ->
                param.setResult(false)
            }
        }

        if (removeVpnTransportFlag) {
            ConnectivityManager::class.java.hook("getAllNetworks", HookStage.AFTER) { param ->
                val instance = param.thisObject() as? ConnectivityManager ?: return@hook
                val networks = param.getResult() as? Array<*> ?: return@hook

                param.setResult(networks.filterIsInstance<Network>().filter { network ->
                    val capabilities = instance.getNetworkCapabilities(network) ?: return@filter false
                    !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)
                }.toTypedArray())
            }
        }

        if (forceWifiTransportFlag) {
            val connectivityManager = context.androidContext.getSystemService(ConnectivityManager::class.java)
            val activeNetwork = connectivityManager?.activeNetwork
            val initialCapabilities = activeNetwork?.let { connectivityManager.getNetworkCapabilities(it) }
            val isReallyOnWifi = initialCapabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true

            onNextActivityCreate {
                if (isReallyOnWifi) {
                    context.inAppOverlay.showStatusToast(
                        icon = Icons.Filled.Wifi,
                        text = "Connected to Real WiFi",
                        durationMs = 3000
                    )
                } else {
                    context.inAppOverlay.showStatusToast(
                        icon = Icons.Filled.Wifi,
                        text = "WiFi Spoofing Active",
                        durationMs = 3000
                    )
                }
            }

            NetworkCapabilities::class.java.apply {
                hook("hasTransport", HookStage.BEFORE) { param ->
                    val transportType = param.args().getOrNull(0) as? Int
                    val actuallyHasWifi = runCatching {
                        param.invokeOriginal() as Boolean
                    }.getOrDefault(false)

                    if (actuallyHasWifi && transportType == NetworkCapabilities.TRANSPORT_WIFI) {
                        return@hook
                    }

                    if (transportType == NetworkCapabilities.TRANSPORT_WIFI) {
                        param.setResult(true)
                    } else if (transportType == NetworkCapabilities.TRANSPORT_CELLULAR) {
                        param.setResult(false)
                    }
                }
                hook("hasCapability", HookStage.BEFORE) { param ->
                    val capability = param.args().getOrNull(0) as? Int
                    if (capability == NetworkCapabilities.NET_CAPABILITY_NOT_VPN) {
                        param.setResult(true)
                    }
                }
            }
            findClass("android.net.NetworkInfo").apply {
                hook("getType", HookStage.BEFORE) { param ->
                    val originalType = runCatching {
                        param.invokeOriginal() as Int
                    }.getOrDefault(-1)

                    if (originalType == 1) {
                        return@hook
                    }
                    param.setResult(1)
                }
                hook("getTypeName", HookStage.BEFORE) { param ->
                    val originalTypeName = runCatching {
                        param.invokeOriginal() as String
                    }.getOrNull()

                    if (originalTypeName?.equals("WIFI", ignoreCase = true) == true) {
                        return@hook
                    }
                    param.setResult("WIFI")
                }
                hook("isConnected", HookStage.BEFORE) { param ->
                    param.setResult(true)
                }
            }
            findClass("org.chromium.base.RadioUtils").hook("isWifiConnected", HookStage.BEFORE) { param ->
                val actuallyOnWifi = runCatching {
                    val connectivityMgr = context.androidContext.getSystemService(ConnectivityManager::class.java)
                    val activeNet = connectivityMgr?.activeNetwork
                    val caps = activeNet?.let { connectivityMgr.getNetworkCapabilities(it) }
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
                }.getOrDefault(false)

                if (!actuallyOnWifi) {
                    param.setResult(true)
                }
            }
        }

        if (networkOptimization) {
            findClass("java.net.Socket").apply {
                hook("setSendBufferSize", HookStage.BEFORE) { param ->
                    val size = param.arg<Int>(0)
                    if (size < 1024 * 1024) param.setArg(0, 1024 * 1024)
                }
                hook("setReceiveBufferSize", HookStage.BEFORE) { param ->
                    val size = param.arg<Int>(0)
                    if (size < 1024 * 1024) param.setArg(0, 1024 * 1024)
                }
            }
        }

        if (!randomizeDeviceProfileEnabled && spoofAndroidId) {
            val gsfId = getRandomGsfId()
            val wifiMac = generateRandomMacAddress()
            findClass("android.provider.Settings\$Secure").hook("getString", HookStage.BEFORE) { param ->
                val settingName = param.argNullable<String>(1)
                when (settingName) {
                    "android_id" -> param.setResult(generateAndroidId())
                    "advertising_id" -> param.setResult(generateRandomUUID())
                    "bluetooth_address" -> param.setResult(wifiMac)
                    "gsf_id" -> param.setResult(gsfId)
                    else -> return@hook
                }
                context.log.verbose("Manual Android ID spoof override: $settingName")
            }
            findClass("android.provider.Settings\$Secure").hook("getLong", HookStage.BEFORE) { param ->
                val settingName = param.argNullable<String>(1)
                if (settingName == "android_id") {
                    param.setResult(androidIdAsLong(generateAndroidId()))
                }
            }
            runCatching {
                findClass("android.net.wifi.WifiInfo").hook("getMacAddress", HookStage.BEFORE) { param ->
                    param.setResult(wifiMac)
                }
            }
            runCatching {
                findClass("android.bluetooth.BluetoothAdapter").hook("getAddress", HookStage.BEFORE) { param ->
                    param.setResult(wifiMac)
                }
            }
        }

        if (!randomizeDeviceProfileEnabled && spoofDevice) {
            val deviceInfo = getSpoofedDeviceInfo()
            if (deviceInfo != null) {
                val fingerprint = getSpoofedFingerprint(deviceInfo)

                context.log.info("Device spoofing active: ${deviceInfo.manufacturer} ${deviceInfo.model}")

                applyBuildFieldOverrides(
                    manufacturer = deviceInfo.manufacturer,
                    model = deviceInfo.model,
                    brand = deviceInfo.brand,
                    device = deviceInfo.device,
                    product = deviceInfo.product,
                    hardware = deviceInfo.hardware,
                    board = deviceInfo.board,
                    bootloader = deviceInfo.bootloader,
                    display = deviceInfo.display,
                    host = deviceInfo.host,
                    fingerprint = fingerprint,
                    buildTime = System.currentTimeMillis() - (30L..180L).random() * 24L * 60L * 60L * 1000L
                )

                runCatching {
                    findClass("android.os.SystemProperties").hook("get", HookStage.BEFORE) { param ->
                        when (param.argNullable<String>(0) ?: return@hook) {
                            "ro.product.manufacturer" -> param.setResult(deviceInfo.manufacturer)
                            "ro.product.model" -> param.setResult(deviceInfo.model)
                            "ro.product.brand" -> param.setResult(deviceInfo.brand)
                            "ro.product.device" -> param.setResult(deviceInfo.device)
                            "ro.product.name" -> param.setResult(deviceInfo.product)
                            "ro.product.board" -> param.setResult(deviceInfo.board)
                            "ro.hardware" -> param.setResult(deviceInfo.hardware)
                            "ro.build.fingerprint" -> param.setResult(fingerprint)
                            "ro.bootloader" -> param.setResult(deviceInfo.bootloader)
                            "ro.build.display.id" -> param.setResult(deviceInfo.display)
                        }
                    }
                    context.log.info("SystemProperties hooks installed successfully")
                }.onFailure {
                    context.log.warn("Failed to hook SystemProperties: ${it.message}")
                }
            }
        }
    }
}
