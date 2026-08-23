package com.softwiredtech.dashpilot.ble

/**
 * Local VIN decoder for the provisioning flow, aligned with Tesla's published
 * VIN tables (Tesla service manual VIN decoding + the community references).
 *
 * What the BLE advertisement actually leaks:
 *  - legacy advert: "S" + first 8 bytes of SHA1(VIN) + role letter. SHA1 is a
 *    one-way hash — the full VIN (and thus make/model) is NOT recoverable.
 *  - modern advert: "Tesla " + the LAST 6 VIN characters. That IS a partial
 *    VIN (the production sequence), but make/model/body/year/plant live in VIN
 *    positions 1–8 and 10–11, which are never broadcast.
 *
 * So the app decodes the VIN the user types for provisioning (all 17 chars are
 * required by the firmware's TESLA_CMD_PROVISION anyway) into the pieces an
 * owner cares about: model, drive unit, model year, and assembly plant.
 *
 * Best-effort local tables for common Tesla WMIs / VDS patterns — not a
 * licensed decoder database.
 */
object TeslaVinDecoder {

    private const val VALID_CHARS = "ABCDEFGHJKLMNPRSTVWXYZ0123456789"

    /** Known Tesla World Manufacturer Identifiers (Tesla service manual + references). */
    private val WMI_PLANT = mapOf(
        "5YJ" to "Fremont, CA",
        "7SA" to "Austin, TX",
        "7G2" to "Austin, TX", // Cybertruck / Semi class
        "LRW" to "Shanghai, China",
        "XP7" to "Berlin, Germany",
        "SFZ" to "Hethel, UK", // original Roadster
    )

    /** VIN char 4 -> model / line series (Tesla service manual). */
    private val MODEL_BY_CHAR = mapOf(
        'S' to "Model S",
        'X' to "Model X",
        '3' to "Model 3",
        'Y' to "Model Y",
        'C' to "Cybertruck",
        'R' to "Roadster",
    )

    /** VIN char 5 -> body style (Tesla service manual / decoder tables). */
    private val BODY_BY_CHAR = mapOf(
        'A' to "Hatchback LHD",
        'B' to "Hatchback RHD",
        'C' to "MPV LHD",
        'D' to "MPV RHD",
        'E' to "Sedan LHD",
        'F' to "Sedan RHD",
        'G' to "MPV LHD",
        'H' to "MPV RHD",
    )

    /** VIN char 7 -> battery chemistry (E = ternary Li-ion, F = LFP). */
    private val BATTERY_BY_CHAR = mapOf(
        'E' to "Li-Ion",
        'F' to "LiFePO4",
        'H' to "Li-Ion",
        'S' to "Li-Ion",
        'V' to "Li-Ion",
    )

    /** VIN char 8 -> motor / drive unit, per Tesla's per-model table. */
    private val DRIVE_BY_MODEL = mapOf(
        'S' to mapOf('5' to "Dual Motor", '6' to "Tri Motor (Plaid)", '4' to "Dual Motor", '2' to "Single Motor"),
        'X' to mapOf('5' to "Dual Motor", '6' to "Tri Motor (Plaid)", '4' to "Dual Motor", '2' to "Single Motor"),
        '3' to mapOf(
            'A' to "Single Motor", 'B' to "Dual Motor", 'C' to "Dual Motor Performance",
            'R' to "Single Motor", 'S' to "Single Motor", 'T' to "Single Motor",
        ),
        'Y' to mapOf(
            'D' to "Single Motor", 'E' to "Dual Motor", 'F' to "Dual Motor Performance",
            'J' to "Single Motor", 'K' to "Dual Motor", 'L' to "Dual Motor Performance",
        ),
        'C' to mapOf('A' to "Dual Motor", 'B' to "Tri Motor", 'C' to "Dual Motor"),
    )

    /** VIN char 10 -> model year (2010–2030 cycling). */
    private val YEAR_BY_CHAR = mapOf(
        'A' to 2010, 'B' to 2011, 'C' to 2012, 'D' to 2013, 'E' to 2014,
        'F' to 2015, 'G' to 2016, 'H' to 2017, 'J' to 2018, 'K' to 2019,
        'L' to 2020, 'M' to 2021, 'N' to 2022, 'P' to 2023, 'R' to 2024,
        'S' to 2025, 'T' to 2026, 'V' to 2027, 'W' to 2028, 'X' to 2029,
        'Y' to 2030,
    )

    /** VIN char 11 -> assembly plant (Tesla service manual). */
    private val PLANT_BY_CHAR = mapOf(
        'F' to "Fremont, CA",
        'A' to "Austin, TX",
        'B' to "Berlin, Germany",
        'G' to "Shanghai, China",
        'R' to "Shanghai, China",
        'K' to "Lathrop, CA", // Semi
    )

    data class DecodedVin(
        val manufacturer: String?,
        val model: String?,
        val body: String?,
        val drive: String?,
        val battery: String?,
        val modelYear: Int?,
        val plant: String?,
        val valid: Boolean,
    )

    fun isValidVinLength(vin: String): Boolean =
        vin.length == 17 && vin.all { it in VALID_CHARS }

    /** Complete, legal VIN belonging to a Tesla WMI known by this decoder. */
    fun isTeslaVin(vin: String): Boolean {
        val upper = vin.uppercase()
        return isValidVinLength(upper) && upper.take(3) in WMI_PLANT
    }

    /** ISO 3779 transliteration values (I, O, Q never appear in a VIN). */
    private val CHAR_VALUES: Map<Char, Int> = buildMap {
        ('0'..'9').forEach { put(it, it - '0') }
        putAll(
            mapOf(
                'A' to 1, 'B' to 2, 'C' to 3, 'D' to 4, 'E' to 5, 'F' to 6, 'G' to 7, 'H' to 8,
                'J' to 1, 'K' to 2, 'L' to 3, 'M' to 4, 'N' to 5,
                'P' to 7, 'R' to 9,
                'S' to 2, 'T' to 3, 'U' to 4, 'V' to 5, 'W' to 6, 'X' to 7, 'Y' to 8, 'Z' to 9,
            ),
        )
    }

    /** ISO 3779 position weights. */
    private val POSITION_WEIGHTS = intArrayOf(8, 7, 6, 5, 4, 3, 2, 10, 0, 9, 8, 7, 6, 5, 4, 3, 2)

    /**
     * True when the position-9 check digit satisfies ISO 3779. Every Tesla
     * plant (US, CN, EU) computes it, so a failure almost always means a
     * mistyped character — precisely what an advert tail match cannot catch.
     *
     * Deliberately NOT part of [isTeslaVin]: some regions do not enforce the
     * digit, and blocking here could lock out a legitimate car. Callers treat
     * a failure as "require explicit confirmation", not "impossible".
     */
    fun checkDigitValid(vin: String): Boolean {
        val upper = vin.uppercase()
        if (upper.length != 17) return false
        var sum = 0
        for (i in upper.indices) {
            val value = CHAR_VALUES[upper[i]] ?: return false
            sum += value * POSITION_WEIGHTS[i]
        }
        val expected = sum % 11
        return upper[8] == if (expected == 10) 'X' else ('0' + expected)
    }

    fun decode(vin: String): DecodedVin {
        val upper = vin.uppercase()
        if (!isTeslaVin(upper)) {
            return DecodedVin(null, null, null, null, null, null, null, false)
        }

        val wmi = upper.substring(0, 3)
        val modelChar = upper[3]
        val model = MODEL_BY_CHAR[modelChar]
        val body = BODY_BY_CHAR[upper[4]]
        val battery = BATTERY_BY_CHAR[upper[6]]
        // Drive table is model-specific; only decode when both are known.
        val drive = DRIVE_BY_MODEL[modelChar]?.get(upper[7])
        val modelYear = YEAR_BY_CHAR[upper[9]]
        val plant = PLANT_BY_CHAR[upper[10]] ?: WMI_PLANT[wmi]
        val manufacturer = "Tesla"
        return DecodedVin(manufacturer, model, body, drive, battery, modelYear, plant, true)
    }

    /** Human one-liner, e.g. "Tesla Model 3 · Dual Motor · 2021 · Fremont, CA". */
    fun descriptive(decoded: DecodedVin): String {
        val parts = buildList {
            add(decoded.manufacturer ?: "Vehicle")
            decoded.model?.let { add(it) }
            decoded.drive?.let { add(it) }
            decoded.modelYear?.let { add(it.toString()) }
            decoded.plant?.let { add(it) }
        }
        return parts.joinToString(" · ")
    }

    /** Secondary detail line, e.g. "Sedan LHD · LiFePO4". Empty when unknown. */
    fun detailLine(decoded: DecodedVin): String {
        val bits = listOfNotNull(decoded.body, decoded.battery, decoded.drive)
        return bits.distinct().joinToString(" · ")
    }
}
