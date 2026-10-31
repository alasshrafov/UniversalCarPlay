package com.shilapi.xcertplay.vehicle

/**
 * BAtAn Cin avtomobil brendlTri vT qruplar AAn geniqlTndirilmiq profil sistemi
 */
enum class VehicleBrand(
    val id: String,
    val brandName: String,
    val groupName: String,
    val commercialTitle: String,
    val defaultWidth: Int,
    val defaultHeight: Int,
    val defaultDpi: Int,
    val accentColorHex: String,
    val supportsRotation: Boolean = false,
    val systemPackageCheck: List<String> = emptyList()
) {
    // 1. BYD Qrupu
    BYD(
        id = "byd",
        brandName = "BYD (Denza, Yangwang)",
        groupName = "BYD Group",
        commercialTitle = "BUILD YOUR DREAMS • DILINK",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 240,
        accentColorHex = "#00F0FF",
        supportsRotation = true,
        systemPackageCheck = listOf("com.byd.carsettings", "com.byd.service", "com.byd.amapservice", "com.denza.car")
    ),

    // 2. Changan Qrupu (Deepal, Avatr, UNI)
    CHANGAN(
        id = "changan",
        brandName = "Changan (Deepal, Avatr, UNI)",
        groupName = "Changan Group",
        commercialTitle = "CHANGAN AUTOMOBILE • DEEPAL SUNFLOWER",
        defaultWidth = 1920,
        defaultHeight = 720,
        defaultDpi = 180,
        accentColorHex = "#2F80ED",
        supportsRotation = false,
        systemPackageCheck = listOf("com.changan.incall", "com.changan.carsettings", "com.deepal.car", "com.avatr.car")
    ),

    // 3. Geely Qrupu (Galaxy, Lynk & Co)
    GEELY(
        id = "geely",
        brandName = "Geely (Galaxy, Lynk & Co)",
        groupName = "Geely Group",
        commercialTitle = "GEELY AUTO • FLYME AUTO",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 200,
        accentColorHex = "#155DFC",
        supportsRotation = false,
        systemPackageCheck = listOf("com.geely.inputservice", "com.geely.ecarx", "com.ecarx.carsettings", "com.lynkco.car")
    ),

    // 4. Zeekr (Geely Premium EV)
    ZEEKR(
        id = "zeekr",
        brandName = "Zeekr (001, 007, 009, X)",
        groupName = "Geely Group",
        commercialTitle = "ZEEKR PERFORMANCE • 8295 OS",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 220,
        accentColorHex = "#30D158",
        supportsRotation = false,
        systemPackageCheck = listOf("com.zeekrlife.car", "com.zeekr.carsettings")
    ),

    // 5. Chery Qrupu (Jetour, Jaecoo, Omoda, Exeed)
    CHERY(
        id = "chery",
        brandName = "Chery (Jetour, Jaecoo, Omoda, Exeed)",
        groupName = "Chery Group",
        commercialTitle = "CHERY AUTOMOBILE • LION OS",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 180,
        accentColorHex = "#E53E3E",
        supportsRotation = false,
        systemPackageCheck = listOf("com.chery.automap", "com.chery.carsettings", "com.jetour.car", "com.exeed.service")
    ),

    // 6. Great Wall Motor (Tank, Haval, Wey, Poer)
    HAVAL(
        id = "haval",
        brandName = "Haval / GWM (Tank, Wey, Poer)",
        groupName = "GWM Group",
        commercialTitle = "GWM TANK • COFFEE INTELLIGENCE",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 180,
        accentColorHex = "#FF6B00",
        supportsRotation = false,
        systemPackageCheck = listOf("com.greatwall.hvac", "com.gwm.carsettings", "com.tank.service", "com.haval.car")
    ),

    // 7. Li Auto (Li Xiang)
    LI_AUTO(
        id = "liauto",
        brandName = "Li Auto (L6, L7, L8, L9, Mega)",
        groupName = "Li Auto",
        commercialTitle = "LEADING IDEAL • 3K DISPLAY",
        defaultWidth = 2880,
        defaultHeight = 1620,
        defaultDpi = 320,
        accentColorHex = "#FFD60A",
        supportsRotation = false,
        systemPackageCheck = listOf("com.liauto.car", "com.chj.carsettings")
    ),

    // 8. NIO & Onvo
    NIO(
        id = "nio",
        brandName = "NIO (ES6, ES8, ET5, ET7, Onvo)",
        groupName = "NIO Inc.",
        commercialTitle = "NIO SMART EV • BANYAN OS",
        defaultWidth = 1920,
        defaultHeight = 1200,
        defaultDpi = 240,
        accentColorHex = "#00C3FF",
        supportsRotation = false,
        systemPackageCheck = listOf("com.nio.car", "com.nio.banyan")
    ),

    // 9. XPeng (Xiaopeng)
    XPENG(
        id = "xpeng",
        brandName = "XPeng (G6, G9, P7, P7i, X9)",
        groupName = "XPeng Motors",
        commercialTitle = "XPENG INTELLIGENCE • XMART OS",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 200,
        accentColorHex = "#00E5FF",
        supportsRotation = false,
        systemPackageCheck = listOf("com.xiaopeng.car", "com.xiaopeng.xmart")
    ),

    // 10. GAC Group (Trumpchi, AION, Hyper)
    GAC(
        id = "gac",
        brandName = "GAC (Trumpchi, AION, Hyper)",
        groupName = "GAC Group",
        commercialTitle = "GAC MOTOR • ADiGO OS",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 200,
        accentColorHex = "#3672F8",
        supportsRotation = false,
        systemPackageCheck = listOf("com.gac.car", "com.aion.carsettings", "com.gac.adigo")
    ),

    // 11. Dongfeng & Voyah (M-Hero, Forthing)
    VOYAH(
        id = "voyah",
        brandName = "Voyah / Dongfeng (M-Hero, Forthing)",
        groupName = "Dongfeng Motor",
        commercialTitle = "VOYAH LUXURY EV • SMART CABIN",
        defaultWidth = 1920,
        defaultHeight = 720,
        defaultDpi = 180,
        accentColorHex = "#8E44AD",
        supportsRotation = false,
        systemPackageCheck = listOf("com.voyah.car", "com.dongfeng.car", "com.forthing.car")
    ),

    // 12. Hongqi (FAW Flagship)
    HONGQI(
        id = "hongqi",
        brandName = "Hongqi (H5, H9, E-HS9, HS5)",
        groupName = "FAW Group",
        commercialTitle = "HONGQI LUXURY • RED FLAG OS",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 220,
        accentColorHex = "#C0392B",
        supportsRotation = false,
        systemPackageCheck = listOf("com.faw.hongqi", "com.hongqi.carsettings")
    ),

    // 13. BAIC (Beijing, BJ40, Arcfox)
    BAIC(
        id = "baic",
        brandName = "BAIC (BJ40, BJ60, Arcfox)",
        groupName = "BAIC Group",
        commercialTitle = "BEIJING AUTO • ARCFOX COCKPIT",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 200,
        accentColorHex = "#D35400",
        supportsRotation = false,
        systemPackageCheck = listOf("com.baic.car", "com.arcfox.carsettings")
    ),

    // 14. SAIC MG & Roewe (Maxus, IM Motors)
    MG_SAIC(
        id = "mg",
        brandName = "MG / Roewe (Maxus, IM Motors)",
        groupName = "SAIC Motor",
        commercialTitle = "MG MOTOR • VENUS OS",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 200,
        accentColorHex = "#E74C3C",
        supportsRotation = false,
        systemPackageCheck = listOf("com.saic.car", "com.mg.carsettings", "com.roewe.car")
    ),

    // 15. Xiaomi Auto (SU7 / HyperOS)
    XIAOMI_AUTO(
        id = "xiaomi",
        brandName = "Xiaomi Auto (SU7, SU7 Max)",
        groupName = "Xiaomi EV",
        commercialTitle = "XIAOMI EV • HYPEROS SMART CABIN",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 240,
        accentColorHex = "#FF6900",
        supportsRotation = false,
        systemPackageCheck = listOf("com.xiaomi.car", "com.miui.car", "com.xiaomi.ev")
    ),

    // 16. Leapmotor (C10, C11, C16)
    LEAPMOTOR(
        id = "leapmotor",
        brandName = "Leapmotor (C10, C11, C16)",
        groupName = "Leapmotor",
        commercialTitle = "LEAPMOTOR • SMART EV OS",
        defaultWidth = 1920,
        defaultHeight = 1080,
        defaultDpi = 200,
        accentColorHex = "#16A085",
        supportsRotation = false,
        systemPackageCheck = listOf("com.leapmotor.car", "com.leapmotor.carsettings")
    ),

    // 17. Standart Universal Rejim
    UNIVERSAL(
        id = "universal",
        brandName = "CarTwin Universal",
        groupName = "Universal Platform",
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
