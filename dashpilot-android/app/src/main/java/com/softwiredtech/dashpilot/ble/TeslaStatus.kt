package com.softwiredtech.dashpilot.ble

/**
 * Parsed Tesla status indication from the DashKit app-channel service (CADA0202).
 *
 * The DashKit is installed in the car trim (LEDs not visible), so this frame is
 * the phone's only view of the car / enrollment state. See
 * docs/tesla-ble-app-ux-handoff.md §5 (status frame).
 */
enum class TeslaLinkState(val raw: Int) {
    NeverEnrolled(0x00),
    EnrolledNotConnected(0x01),
    EnrolledConnected(0x02),
    PairingWindow(0x03),
    EnrollmentFault(0x04),
    Staged(0x05),        // car found, awaiting app start
    Connecting(0x06),    // app start accepted; contacting the car (pre-tap window)
    Unknown(0xFF);

    val hasKey: Boolean
        get() = this == EnrolledNotConnected || this == EnrolledConnected

    val connected: Boolean
        get() = this == EnrolledConnected

    companion object {
        fun from(raw: Int): TeslaLinkState = entries.firstOrNull { it.raw == raw } ?: Unknown
    }
}

enum class TeslaFaultDetail(val raw: Int) {
    TapTimeout(0x00),
    Rejected(0x01),
    Protocol(0x02),
    Persist(0x03),
    None(0xFF);

    companion object {
        fun from(raw: Int): TeslaFaultDetail = entries.firstOrNull { it.raw == raw } ?: None
    }
}

data class TeslaStatus(
    val version: Int,
    val linkState: TeslaLinkState,
    val presence: Int,
    val lock: Int,
    val sleep: Int,
    val flags: Int,
    val faultDetail: TeslaFaultDetail,
) {
    companion object {
        /** Stable value before any frame arrives. */
        val Idle = TeslaStatus(0x01, TeslaLinkState.Unknown, 0xFF, 0xFF, 0xFF, 0, TeslaFaultDetail.None)

        /**
         * Parse the 7-byte status frame:
         *   [0] frame version = 0x01
         *   [1] link_state
         *   [2] presence   0 absent, 1 present, 0xFF unknown
         *   [3] lock       0 unlocked, 1 locked, 0xFF unknown
         *   [4] sleep      0 awake, 1 asleep, 0xFF unknown
         *   [5] flags      bit0 charge-connected, bit1 charging, bit2 climate-on
         *   [6] fault detail (0xFF unless link_state == enrollment fault)
         */
        fun parse(payload: ByteArray): TeslaStatus? {
            if (payload.size < 7) return null
            val b = { i: Int -> payload[i].toInt() and 0xFF }
            if (b(0) != 0x01) return null
            return TeslaStatus(
                version = b(0),
                linkState = TeslaLinkState.from(b(1)),
                presence = b(2),
                lock = b(3),
                sleep = b(4),
                flags = b(5),
                faultDetail = TeslaFaultDetail.from(b(6)),
            )
        }
    }
}
