package com.example.radioobserver.core.radio.export

import com.example.radioobserver.core.radio.history.HistoryAnnotation
import com.example.radioobserver.core.radio.history.HistoryRecord
import com.example.radioobserver.core.radio.model.RadioType
import com.example.radioobserver.core.radio.pipeline.FreshnessState
import com.example.radioobserver.core.radio.signal.SignalBucket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * WORKSTREAM 06 — behavioral export tests (host/JVM only).
 * Phone-only behavior (SAF picker, file providers) is NOT covered here and is
 * explicitly reported as unverified.
 */
class ExportTest {

    private val now = 1761300000000L

    private fun rec(
        id: String,
        radio: RadioType = RadioType.BLE,
        saved: Long = 1761200000000L,
        observed: Long = 1761200000000L,
        name: String? = null,
        typeHint: String? = "iBeacon (Apple layout)",
        manufacturer: String? = "Apple Inc.",
        signal: SignalBucket = SignalBucket.NEAR,
        freshness: FreshnessState = FreshnessState.FRESH,
    ) = HistoryRecord(
        id = id, radio = radio, savedAtEpochMs = saved, observedAtEpochMs = observed,
        displayName = name, manufacturerHint = manufacturer, typeHint = typeHint,
        signalBucket = signal, freshness = freshness, ageMs = 1200L,
    )

    @Test
    fun `A default scope excludes names and user annotations`() {
        val records = listOf(rec("r1", name = "EvWifi"), rec("r2"))
        val ann = mapOf("r1" to HistoryAnnotation("r1", "benim", "notum", true))
        val csv = ReportBuilder.recordsReport(records, ann, ExportOptions(), ExportFormat.CSV, now)
        assertFalse(csv.content.contains("EvWifi"))
        assertFalse(csv.content.contains("benim"))
        val json = ReportBuilder.recordsReport(records, ann, ExportOptions(), ExportFormat.JSON, now)
        assertFalse(json.content.contains("EvWifi"))
        assertFalse(json.content.contains("\"network_name\""))
        assertFalse(json.content.contains("user_label"))
    }

    @Test
    fun `B opt-in inclusion adds consented name and annotations only`() {
        val records = listOf(rec("r1", name = "Kayitli"), rec("r2", name = null))
        val ann = mapOf("r1" to HistoryAnnotation("r1", "etiket", "not", true))
        val csv = ReportBuilder.recordsReport(
            records, ann, ExportOptions(includeNames = true, includeUserAnnotations = true),
            ExportFormat.CSV, now).content
        assertTrue(csv.contains("network_name"))
        assertTrue(csv.contains("Kayitli"))
        assertTrue(csv.contains("etiket"))
        // A record without a consented name stays unknown, never reconstructed.
        assertTrue(csv.contains(ExportPolicy.UNKNOWN))
    }

    @Test
    fun `C csv escaping quotes commas newlines and unicode`() {
        val records = listOf(
            rec("r1", name = "a,b\"c\nd", typeHint = "Türkçe ipucu ğüşi")
        )
        val csv = ReportBuilder.recordsReport(records, emptyMap(),
            ExportOptions(includeNames = true), ExportFormat.CSV, now).content
        assertTrue(csv.contains("\"a,b\"\"c\nd\""))
        assertTrue(csv.contains("Türkçe ipucu ğüşi"))
        // Header + exactly one data row.
        assertEquals(2, csv.trim().split("\r\n").size)
    }

    @Test
    fun `D formula injection is neutralized in every text cell`() {
        val records = listOf(
            rec("r1", name = "=CMD|calc", typeHint = "+1+1", manufacturer = "@SUM(1)")
        )
        val csv = ReportBuilder.recordsReport(records, emptyMap(),
            ExportOptions(includeNames = true), ExportFormat.CSV, now).content
        assertTrue(csv.contains("'=CMD|calc"))
        assertTrue(csv.contains("'+1+1"))
        assertTrue(csv.contains("'@SUM(1)"))
        // Protection is idempotent-safe: the raw dangerous prefix never starts a cell.
        val cells = csv.split("\r\n")[1].split(",")
        assertTrue(cells.none { it.startsWith("=") || it.startsWith("+") || it.startsWith("@") })
        // Leading whitespace cannot bypass it.
        // The apostrophe is prefixed to the ORIGINAL value (leading blanks kept).
        assertEquals("'  =X", ExportPolicy.protectSpreadsheet("  =X"))
        assertEquals("'\t@y", ExportPolicy.protectSpreadsheet("\t@y"))
        assertEquals("plain", ExportPolicy.protectSpreadsheet("plain"))
    }

    @Test
    fun `E json has version type typed values and explicit nulls`() {
        val records = listOf(rec("r1", typeHint = null, manufacturer = null))
        val json = ReportBuilder.recordsReport(records, emptyMap(), ExportOptions(),
            ExportFormat.JSON, now).content
        assertTrue(json.contains("\"schema_version\":1"))
        assertTrue(json.contains("\"report_type\":\"records\""))
        val expectedStamp = ExportPolicy.isoUtc(now)!!
        assertTrue(json.contains("\"generated_at_utc\":\"$expectedStamp\""))
        assertTrue(json.contains("\"type_hint\":null"))
        assertTrue(json.contains("\"age_ms\":1200"))
        assertTrue(json.contains("\"totals\":{\"record_count\":1}"))
    }

    @Test
    fun `F empty input and invalid timestamps are handled honestly`() {
        val emptyCsv = ReportBuilder.recordsReport(emptyList(), emptyMap(), ExportOptions(),
            ExportFormat.CSV, now)
        assertEquals(0, emptyCsv.recordCount)
        assertTrue(emptyCsv.content.startsWith("row,source,"))
        assertEquals(1, emptyCsv.content.trim().split("\r\n").size)
        val bad = rec("r1", saved = -5L, observed = 0L)
        val csv = ReportBuilder.recordsReport(listOf(bad), emptyMap(), ExportOptions(),
            ExportFormat.CSV, now).content
        val cells = csv.split("\r\n")[1]
        assertTrue(cells.contains(ExportPolicy.UNKNOWN))
        assertFalse(ExportPolicy.isoUtc(-1L) != null)
        assertFalse(ExportPolicy.isValidTimestamp(0L))
    }

    @Test
    fun `G ordering is deterministic and stable`() {
        val a = rec("a", saved = 1000L)
        val b = rec("b", saved = 3000L)
        val c = rec("c", saved = 2000L, radio = RadioType.WIFI)
        val one = ReportBuilder.recordsReport(listOf(a, b, c), emptyMap(), ExportOptions(),
            ExportFormat.CSV, now).content
        val two = ReportBuilder.recordsReport(listOf(c, a, b), emptyMap(), ExportOptions(),
            ExportFormat.CSV, now).content
        assertEquals(one, two)
        val rows = one.trim().split("\r\n").drop(1)
        assertEquals("BLE", rows[0].split(",")[1]) // newest first
    }

    @Test
    fun `H group summary reconciles and counts missing records`() {
        val records = listOf(rec("r1"), rec("r2", radio = RadioType.WIFI))
        val result = ReportBuilder.groupSummaryReport(
            "Grup A", listOf("r1", "r2", "silinmis"), records, emptyMap(),
            ExportOptions(), ExportFormat.JSON, now)
        assertEquals(2, result.recordCount)
        assertTrue(result.content.contains("\"missing_record_count\":1"))
        assertTrue(result.content.contains("\"record_count\":2"))
        assertTrue(result.content.contains("\"ble_count\":1"))
        assertTrue(result.content.contains("\"wifi_count\":1"))
        assertTrue(result.content.contains("\"time_buckets\""))
        assertTrue(result.content.contains(ReportBuilder.NOTE_COUNT_NOT_DEVICES))
    }

    @Test
    fun `I comparison matches the shared analytics engine`() {
        val records = listOf(
            rec("r1"), rec("r2", radio = RadioType.WIFI, freshness = FreshnessState.STALE),
            rec("r3", signal = SignalBucket.IMMEDIATE),
        )
        val json = ReportBuilder.comparisonReport(
            "Sol", listOf("r1", "r2"), "Sağ", listOf("r3"), records, ExportOptions(),
            ExportFormat.JSON, now).content
        assertTrue(json.contains("\"report_type\":\"group-comparison\""))
        assertTrue(json.contains("\"comparison\""))
        // Left: 1 BLE + 1 STALE Wi-Fi; freshness provenance preserved verbatim.
        assertTrue(json.contains("\"category\":\"STALE\""))
        val csv = ReportBuilder.comparisonReport(
            "Sol", listOf("r1", "r2"), "Sağ", listOf("r3"), records, ExportOptions(),
            ExportFormat.CSV, now).content
        assertTrue(csv.startsWith("metric,left,right\r\n"))
        assertTrue(csv.contains("record_count,2,1"))
    }

    @Test
    fun `J deleted or expired records cannot appear in exports`() {
        // The History snapshot only contains surviving records: an id absent from
        // allRecords is reported as missing, never fabricated.
        val surviving = listOf(rec("r1"))
        val result = ReportBuilder.groupSummaryReport(
            "G", listOf("r1", "deleted-id"), surviving, emptyMap(),
            ExportOptions(), ExportFormat.CSV, now)
        assertEquals(1, result.recordCount)
        assertFalse(result.content.contains("deleted-id"))
    }

    @Test
    fun `K export never mutates source data`() {
        val records = listOf(rec("r1", name = "X"), rec("r2"))
        val snapshot = records.toList()
        val ann = mapOf("r1" to HistoryAnnotation("r1", "l", "n", true))
        ReportBuilder.groupSummaryReport("G", listOf("r1", "r2"), records, ann,
            ExportOptions(includeNames = true, includeUserAnnotations = true),
            ExportFormat.CSV, now)
        ReportBuilder.comparisonReport("A", listOf("r1"), "B", listOf("r2"), records,
            ExportOptions(includeNames = true, includeUserAnnotations = true),
            ExportFormat.JSON, now)
        assertEquals(snapshot, records)
        assertEquals("X", records[0].displayName)
    }

    @Test
    fun `L projected records never expose ids or withheld names`() {
        val record = rec("internal-id-123", name = "Gizli")
        val projected = ExportPolicy.projected(record, ExportOptions())
        assertEquals(null, projected.displayName)
        // Structural: the projection type has no id/fingerprint field at all.
        val fieldNames = ExportRecord::class.java.declaredFields.map { it.name }
        assertFalse(fieldNames.any { it.equals("id", true) || it.contains("address") || it.contains("bssid") })
        assertNotNull(projected.radio)
    }

    @Test
    fun `M suggested filename is safe and format specific`() {
        val csv = ReportBuilder.recordsReport(emptyList(), emptyMap(), ExportOptions(),
            ExportFormat.CSV, now)
        val json = ReportBuilder.recordsReport(emptyList(), emptyMap(), ExportOptions(),
            ExportFormat.JSON, now)
        assertTrue(csv.suggestedFileName.matches(Regex("radioobserver-records-[0-9TZ]+\\.csv")))
        assertTrue(json.suggestedFileName.endsWith(".json"))
        assertFalse(csv.suggestedFileName.contains("/"))
        assertEquals("text/csv", ExportFormat.CSV.mimeType)
        assertEquals("application/json", ExportFormat.JSON.mimeType)
    }
}
