package cock.crest.purrfectsnap.lite.core.features.impl.experiments

import java.security.SecureRandom

data class DeviceInfo(
    val manufacturer: String,
    val model: String,
    val brand: String,
    val device: String,
    val product: String,
    val hardware: String,
    val board: String,
    val bootloader: String,
    val display: String,
    val host: String
)

data class DeviceBuildProfile(
    val androidRelease: String,
    val display: String,
    val buildId: String,
    val incremental: String,
    val host: String,
    val bootloader: String? = null
)

data class DeviceCapabilityProfile(
    val supportedAbis: List<String>,
    val supported32BitAbis: List<String>,
    val supported64BitAbis: List<String>,
    val phoneCount: Int,
    val isHearingAidCompatibilitySupported: Boolean,
    val isTtySupported: Boolean,
    val isWorldPhone: Boolean,
    val isSmsCapable: Boolean,
    val isVoiceCapable: Boolean,
    val phoneType: Int,
    val phoneTypeString: String
)

data class DeviceTemplate(
    val marketingName: String,
    val deviceInfo: DeviceInfo,
    val builds: List<DeviceBuildProfile>,
    val capabilities: DeviceCapabilityProfile
)

object DeviceSpoofer {
    private val defaultCapabilities = DeviceCapabilityProfile(
        supportedAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi"),
        supported32BitAbis = listOf("armeabi-v7a", "armeabi"),
        supported64BitAbis = listOf("arm64-v8a"),
        phoneCount = 2,
        isHearingAidCompatibilitySupported = true,
        isTtySupported = false,
        isWorldPhone = true,
        isSmsCapable = true,
        isVoiceCapable = true,
        phoneType = 1,
        phoneTypeString = "PHONE_TYPE_GSM"
    )

    private val singleSimCapabilities = defaultCapabilities.copy(phoneCount = 1)

    private val devices = mapOf(
        "Pixel 8 Pro" to DeviceTemplate(
            marketingName = "Pixel 8 Pro",
            deviceInfo = DeviceInfo(
                manufacturer = "Google",
                model = "Pixel 8 Pro",
                brand = "google",
                device = "husky",
                product = "husky",
                hardware = "husky",
                board = "husky",
                bootloader = "husky-1.0-11003666",
                display = "UQ1A.231205.015",
                host = "abfarm-release-rbe-64-00163"
            ),
            builds = listOf(
                DeviceBuildProfile("14", "UQ1A.231205.015", "UQ1A.231205.015", "11003666", "abfarm-release-rbe-64-00163", "husky-1.0-11003666"),
                DeviceBuildProfile("15", "AP4A.250205.002", "AP4A.250205.002", "12141234", "abfarm-release-rbe-64-00171", "husky-1.0-12141234")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 1, isWorldPhone = false)
        ),
        "Pixel 9 Pro XL" to DeviceTemplate(
            marketingName = "Pixel 9 Pro XL",
            deviceInfo = DeviceInfo(
                manufacturer = "Google",
                model = "Pixel 9 Pro XL",
                brand = "google",
                device = "komodo",
                product = "komodo",
                hardware = "komodo",
                board = "komodo",
                bootloader = "komodo-1.0-12110753",
                display = "AP3A.241105.008",
                host = "abfarm-release-rbe-64-00163"
            ),
            builds = listOf(
                DeviceBuildProfile("14", "AP3A.241105.008", "AP3A.241105.008", "12110753", "abfarm-release-rbe-64-00163", "komodo-1.0-12110753"),
                DeviceBuildProfile("15", "BP1A.250105.006", "BP1A.250105.006", "13120567", "abfarm-release-rbe-65-00088", "komodo-1.0-13120567")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 1, isWorldPhone = false)
        ),
        "Pixel 10" to DeviceTemplate(
            marketingName = "Pixel 10",
            deviceInfo = DeviceInfo(
                manufacturer = "Google",
                model = "Pixel 10",
                brand = "google",
                device = "frankel",
                product = "frankel",
                hardware = "tensor_g5",
                board = "frankel",
                bootloader = "frankel-1.0-12345678",
                display = "BP1A.250105.002",
                host = "abfarm-release-rbe-65-00200"
            ),
            builds = listOf(
                DeviceBuildProfile("15", "BP1A.250105.002", "BP1A.250105.002", "12345678", "abfarm-release-rbe-65-00200", "frankel-1.0-12345678")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 1, isWorldPhone = false)
        ),
        "Pixel 10 Pro" to DeviceTemplate(
            marketingName = "Pixel 10 Pro",
            deviceInfo = DeviceInfo(
                manufacturer = "Google",
                model = "Pixel 10 Pro",
                brand = "google",
                device = "blazer",
                product = "blazer",
                hardware = "tensor_g5",
                board = "blazer",
                bootloader = "blazer-1.0-12345679",
                display = "BP1A.250105.002",
                host = "abfarm-release-rbe-65-00201"
            ),
            builds = listOf(
                DeviceBuildProfile("15", "BP1A.250105.002", "BP1A.250105.002", "12345679", "abfarm-release-rbe-65-00201", "blazer-1.0-12345679")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 1, isWorldPhone = false)
        ),
        "Pixel 10 Pro XL" to DeviceTemplate(
            marketingName = "Pixel 10 Pro XL",
            deviceInfo = DeviceInfo(
                manufacturer = "Google",
                model = "Pixel 10 Pro XL",
                brand = "google",
                device = "mustang",
                product = "mustang",
                hardware = "tensor_g5",
                board = "mustang",
                bootloader = "mustang-1.0-12345680",
                display = "BP1A.250105.002",
                host = "abfarm-release-rbe-65-00202"
            ),
            builds = listOf(
                DeviceBuildProfile("15", "BP1A.250105.002", "BP1A.250105.002", "12345680", "abfarm-release-rbe-65-00202", "mustang-1.0-12345680")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 1, isWorldPhone = false)
        ),
        "Pixel 10 Pro Fold" to DeviceTemplate(
            marketingName = "Pixel 10 Pro Fold",
            deviceInfo = DeviceInfo(
                manufacturer = "Google",
                model = "Pixel 10 Pro Fold",
                brand = "google",
                device = "rango",
                product = "rango",
                hardware = "tensor_g5",
                board = "rango",
                bootloader = "rango-1.0-12345681",
                display = "BP1A.250105.002",
                host = "abfarm-release-rbe-65-00203"
            ),
            builds = listOf(
                DeviceBuildProfile("15", "BP1A.250105.002", "BP1A.250105.002", "12345681", "abfarm-release-rbe-65-00203", "rango-1.0-12345681")
            ),
            capabilities = singleSimCapabilities.copy(isWorldPhone = false)
        ),
        "Galaxy S23 Ultra" to DeviceTemplate(
            marketingName = "Galaxy S23 Ultra",
            deviceInfo = DeviceInfo(
                manufacturer = "Samsung",
                model = "SM-S918B",
                brand = "samsung",
                device = "dm3q",
                product = "dm3qxx",
                hardware = "qcom",
                board = "kalama",
                bootloader = "S918BXXU3BWJM",
                display = "UP1A.231005.007.S918BXXU3BWJM",
                host = "21DH7R2P"
            ),
            builds = listOf(
                DeviceBuildProfile("14", "UP1A.231005.007.S918BXXU3BWJM", "UP1A.231005.007", "S918BXXU3BWJM", "21DH7R2P", "S918BXXU3BWJM"),
                DeviceBuildProfile("15", "AP3A.240905.015.S918BXXU4CXA1", "AP3A.240905.015", "S918BXXU4CXA1", "21DH7R2P", "S918BXXU4CXA1")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 2)
        ),
        "Galaxy S24 Ultra" to DeviceTemplate(
            marketingName = "Galaxy S24 Ultra",
            deviceInfo = DeviceInfo(
                manufacturer = "Samsung",
                model = "SM-S928B",
                brand = "samsung",
                device = "e9q",
                product = "e9qxx",
                hardware = "qcom",
                board = "pineapple",
                bootloader = "S928BXXU1AXB5",
                display = "UP1A.231005.007.S928BXXU1AXB5",
                host = "21DH7R2P"
            ),
            builds = listOf(
                DeviceBuildProfile("14", "UP1A.231005.007.S928BXXU1AXB5", "UP1A.231005.007", "S928BXXU1AXB5", "21DH7R2P", "S928BXXU1AXB5"),
                DeviceBuildProfile("15", "AP3A.240905.015.S928BXXU2BYD6", "AP3A.240905.015", "S928BXXU2BYD6", "21DH7R2P", "S928BXXU2BYD6")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 2)
        ),
        "Galaxy S25 Ultra" to DeviceTemplate(
            marketingName = "Galaxy S25 Ultra",
            deviceInfo = DeviceInfo(
                manufacturer = "Samsung",
                model = "SM-S938B",
                brand = "samsung",
                device = "e3q",
                product = "e3qxx",
                hardware = "qcom",
                board = "s5e9945",
                bootloader = "S938BXXU1AXL2",
                display = "UP1A.231005.007.S938BXXU1AXL2",
                host = "21DH7R2P"
            ),
            builds = listOf(
                DeviceBuildProfile("15", "AP3A.241005.019.S938BXXU1AXL2", "AP3A.241005.019", "S938BXXU1AXL2", "21DH7R2P", "S938BXXU1AXL2")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 2)
        ),
        "OnePlus 15" to DeviceTemplate(
            marketingName = "OnePlus 15",
            deviceInfo = DeviceInfo(
                manufacturer = "OnePlus",
                model = "CPH2651",
                brand = "OnePlus",
                device = "OP5929L1",
                product = "OP5929L1_EEA",
                hardware = "qcom",
                board = "taro",
                bootloader = "unknown",
                display = "CPH2651_15.0.0.503(EX01)",
                host = "ubuntu-build"
            ),
            builds = listOf(
                DeviceBuildProfile("15", "CPH2651_15.0.0.503(EX01)", "CPH2651_15.0.0.503(EX01)", "15.0.0.503", "ubuntu-build"),
                DeviceBuildProfile("15", "CPH2651_15.0.0.601(EX01)", "CPH2651_15.0.0.601(EX01)", "15.0.0.601", "ubuntu-build")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 2)
        ),
        "OnePlus Open" to DeviceTemplate(
            marketingName = "OnePlus Open",
            deviceInfo = DeviceInfo(
                manufacturer = "OnePlus",
                model = "CPH2551",
                brand = "OnePlus",
                device = "OP594DL1",
                product = "OP594DL1_EEA",
                hardware = "qcom",
                board = "taro",
                bootloader = "unknown",
                display = "CPH2551_14.0.0.600(EX01)",
                host = "ubuntu-build"
            ),
            builds = listOf(
                DeviceBuildProfile("14", "CPH2551_14.0.0.600(EX01)", "CPH2551_14.0.0.600(EX01)", "14.0.0.600", "ubuntu-build"),
                DeviceBuildProfile("15", "CPH2551_15.0.0.305(EX01)", "CPH2551_15.0.0.305(EX01)", "15.0.0.305", "ubuntu-build")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 2)
        ),
        "Xiaomi 15 Ultra" to DeviceTemplate(
            marketingName = "Xiaomi 15 Ultra",
            deviceInfo = DeviceInfo(
                manufacturer = "Xiaomi",
                model = "25010PN30G",
                brand = "Xiaomi",
                device = "xuanyuan",
                product = "xuanyuan_global",
                hardware = "qcom",
                board = "taro",
                bootloader = "unknown",
                display = "VK.15.0.3.0.VNGMIXM",
                host = "c3-miui-ota-bd164.bj"
            ),
            builds = listOf(
                DeviceBuildProfile("15", "VK.15.0.3.0.VNGMIXM", "VK.15.0.3.0.VNGMIXM", "15.0.3.0", "c3-miui-ota-bd164.bj"),
                DeviceBuildProfile("15", "VK.15.0.6.0.VNGMIXM", "VK.15.0.6.0.VNGMIXM", "15.0.6.0", "c3-miui-ota-bd164.bj")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 2)
        ),
        "OPPO Find X9 Pro" to DeviceTemplate(
            marketingName = "OPPO Find X9 Pro",
            deviceInfo = DeviceInfo(
                manufacturer = "OPPO",
                model = "PHY110",
                brand = "OPPO",
                device = "OP595DL1",
                product = "OP595DL1_EEA",
                hardware = "mt6989",
                board = "k6989v1_64",
                bootloader = "unknown",
                display = "PHY110_15.0.0.100(EX01)",
                host = "ubuntu-build-server"
            ),
            builds = listOf(
                DeviceBuildProfile("15", "PHY110_15.0.0.100(EX01)", "PHY110_15.0.0.100(EX01)", "15.0.0.100", "ubuntu-build-server"),
                DeviceBuildProfile("15", "PHY110_15.0.0.202(EX01)", "PHY110_15.0.0.202(EX01)", "15.0.0.202", "ubuntu-build-server")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 2)
        ),
        "vivo X100 Pro" to DeviceTemplate(
            marketingName = "vivo X100 Pro",
            deviceInfo = DeviceInfo(
                manufacturer = "vivo",
                model = "V2309A",
                brand = "vivo",
                device = "V2309A",
                product = "PD2309",
                hardware = "mt6989",
                board = "k6989v1_64",
                bootloader = "unknown",
                display = "PD2309F_EX_A_14.0.13.2.W30",
                host = "compiler-server"
            ),
            builds = listOf(
                DeviceBuildProfile("14", "PD2309F_EX_A_14.0.13.2.W30", "PD2309F_EX_A_14.0.13.2.W30", "14.0.13.2", "compiler-server"),
                DeviceBuildProfile("15", "PD2309F_EX_A_15.0.8.5.W30", "PD2309F_EX_A_15.0.8.5.W30", "15.0.8.5", "compiler-server")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 2)
        ),
        "realme GT 6" to DeviceTemplate(
            marketingName = "realme GT 6",
            deviceInfo = DeviceInfo(
                manufacturer = "realme",
                model = "RMX3851",
                brand = "realme",
                device = "RMX3851",
                product = "RMX3851_11_A.13",
                hardware = "qcom",
                board = "taro",
                bootloader = "unknown",
                display = "RMX3851_14.0.0.700(EX01)",
                host = "ubuntu-server"
            ),
            builds = listOf(
                DeviceBuildProfile("14", "RMX3851_14.0.0.700(EX01)", "RMX3851_14.0.0.700(EX01)", "14.0.0.700", "ubuntu-server"),
                DeviceBuildProfile("15", "RMX3851_15.0.0.205(EX01)", "RMX3851_15.0.0.205(EX01)", "15.0.0.205", "ubuntu-server")
            ),
            capabilities = defaultCapabilities.copy(phoneCount = 2)
        )
    )

    fun getAvailableDevices(): List<String> = devices.keys.toList()

    fun getDeviceInfo(modelName: String): DeviceInfo? {
        return devices[modelName]?.deviceInfo
    }

    fun getDeviceTemplate(modelName: String): DeviceTemplate? {
        return devices[modelName]
    }

    fun generateFingerprint(deviceInfo: DeviceInfo, buildVersion: String): String {
        val id = "AP3A.${System.currentTimeMillis().toString().take(6)}.005"
        val incremental = System.nanoTime().toString().take(8)
        return "${deviceInfo.brand}/${deviceInfo.product}/${deviceInfo.device}:$buildVersion/$id/$incremental:user/release-keys"
    }

    fun generateFingerprint(deviceInfo: DeviceInfo, buildProfile: DeviceBuildProfile): String {
        return "${deviceInfo.brand}/${deviceInfo.product}/${deviceInfo.device}:${buildProfile.androidRelease}/${buildProfile.buildId}/${buildProfile.incremental}:user/release-keys"
    }

    fun generateAndroidId(): String {
        val random = SecureRandom()
        val bytes = ByteArray(8)
        random.nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun androidIdToBytes(androidId: String): ByteArray {
        return try {
            val len = androidId.length
            val data = ByteArray(len / 2)
            var i = 0
            while (i < len) {
                data[i / 2] = ((Character.digit(androidId[i], 16) shl 4) + Character.digit(androidId[i + 1], 16)).toByte()
                i += 2
            }
            data
        } catch (e: Exception) {
            ByteArray(8)
        }
    }
}
