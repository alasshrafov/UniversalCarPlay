package com.shilapi.xcertplay.vehicle

/**
 * BAtAn Cin avtomobil brendlTri AAn dinamik profil vT ekran tTnzinlTmTsi
 */
enum class VehicleBrand(
    val id: String,
    val brandName: String,
    val commercialTitle: String,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val defaultDpi: Int,
    val accentColorHex: String,
    val supportsRotation: Boolean = false,
    val systemPackageCheck: List<String> = emptyList()
) {
    BYD(
        id = "byd",
        brandName = "BYD Auto",
        commercialTitle = "BUILD YOUR DREAMS • DiLink",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 240,
        accentColorHex = "#00F0FF",
        supportsRotation = true,
        systemPackageCheck = listOf("com.byd.carsettings", "com.byd.service", "com.byd.amapservice")
    ),

    CHANGAN(
        id = "changan",
        brandName = "Changan / Deepal",
        commercialTitle = "CHANGAN AUTOMOBILE • DEEPAL SUNFLOWER",
        defaultWidth = 1920,
        defaultHeight = 720,
        defaultDpi = 180,
        accentColorHex = "#2F80ED",
        supportsRotation = false,
        systemPackageCheck = listOf("com.changan.incall", "com.changan.carsettings", "com.deepal.car")
    ),

    GEELY(
        id = "geely",
        brandName = "Geely / Lynk & Co",
        commercialTitle = "GEELY AUTO • FLYME AUTO",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 200,
        accentColorHex = "#155DFC",
        supportsRotation = false,
        systemPackageCheck = listOf("com.geely.inputservice", "com.geely.ecarx", "com.ecarx.carsettings")
    ),

    CHERY(
        id = "chery",
        brandName = "Chery / Exeed / Omoda",
        commercialTitle = "CHERY AUTOMOBILE • LION OS",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 180,
        accentColorHex = "#E53E3E",
        supportsRotation = false,
        systemPackageCheck = listOf("com.chery.automap", "com.chery.carsettings", "com.exeed.service")
    ),

    LI_AUTO(
        id = "liauto",
        brandName = "Li Auto (Li Xiang)",
        commercialTitle = "LEADING IDEAL • 3K SCREEN",
        defaultWidth = 2880,
        defaultHeight = 1620,
        defaultDpi = 320,
        accentColorHex = "#FFD60A",
        supportsRotation = false,
        systemPackageCheck = listOf("com.liauto.car", "com.chj.carsettings")
    ),

    ZEEKR(
        id = "zeekr",
        brandName = "Zeekr OS (001 / 007)",
        commercialTitle = "ZEEKR PERFORMANCE • 8295 CHIPSET",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 220,
        accentColorHex = "#30D158",
        supportsRotation = false,
        systemPackageCheck = listOf("com.zeekrlife.car", "com.zeekr.carsettings")
    ),

    HAVAL(
        id = "haval",
        brandName = "Haval / GWM / Tank",
        commercialTitle = "GWM TANK • COFFEE INTELLIGENCE",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 180,
        accentColorHex = "#FF6B00",
        supportsRotation = false,
        systemPackageCheck = listOf("com.greatwall.hvac", "com.gwm.carsettings", "com.tank.service")
    ),

    UNIVERSAL(
        id = "universal",
        brandName = "CarTwin Universal",
        commercialTitle = "CARTWIN PREMIUM CONNECT",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 160,
        accentColorHex = "#E5B869",
        supportsRotation = false
    );

    companion object {
        fun fromId(id: String?): VehicleBrand {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: UNIVERSAL
        }
    }
}
