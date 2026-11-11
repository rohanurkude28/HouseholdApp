package com.houseaffairs.app.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class ScanTest {
    private val today = LocalDate.of(2026, 11, 11)

    @Test
    fun `a receipt gives its total - shop and date`() {
        val text = """
            TAX INVOICE
            DMart Avenue Supermarts
            Date: 09/11/2026  Time 18:42
            Milk 1L        68.00
            Rice 5kg      450.00
            Sub total     518.00
            GST            12.50
            Grand Total  ₹ 530.50
        """.trimIndent()
        val r = scanReceipt(text, today)
        assertEquals(BigDecimal("530.50"), r.total)
        assertEquals("DMart Avenue Supermarts", r.merchant)
        assertEquals(LocalDate.of(2026, 11, 9), r.date)
    }

    @Test
    fun `expiry from the usual labels`() {
        assertEquals(LocalDate.of(2027, 8, 31), scanExpiry("Batch B123\nEXP 08/2027\nMRP 45.00", today))
        assertEquals(LocalDate.of(2027, 8, 31), scanExpiry("Exp. Date: AUG 2027", today))
        assertEquals(LocalDate.of(2027, 3, 12), scanExpiry("Use by 12/03/27", today))
        assertEquals(LocalDate.of(2026, 9, 1), scanExpiry("Best before 6 months from manufacture\nMfg: 01/03/2026", today))
        assertNull(scanExpiry("Paracetamol 500 mg", today))
    }

    @Test
    fun `nutrition label to macros`() {
        val label = """
            Nutrition information per 100 g
            Energy 380 kcal
            Protein 12.5 g
            Total Carbohydrate 60 g
            Total Fat 9 g
            Dietary Fibre 6g
        """.trimIndent()
        assertEquals("12.5g · 60g · 9g · 6g", scanNutrition(label))
        assertNull(scanNutrition("Energy 200 kcal"))
    }

    @Test
    fun `a bill's items with quantities and prices`() {
        val text = """
            DMart Avenue Supermarts
            Date: 09/11/2026
            Item        Qty   Rate   Amount
            Milk 1L      2   34.00   68.00
            Basmati Rice 5kg       450.00
            3 x Eggs 6s            180
            Sub total            698.00
            GST                   12.50
            Grand Total          710.50
        """.trimIndent()
        val bill = scanBill(text, today)
        assertEquals(listOf("Milk 1L", "Basmati Rice 5kg", "Eggs 6s"), bill.items.map { it.name })
        assertEquals(listOf(2, 1, 3), bill.items.map { it.quantity })
        assertEquals(6_800L, bill.items.first().priceMinor)
        assertEquals(BigDecimal("710.50"), bill.receipt.total)
        assertEquals(false, bill.pharmacy)
        assertEquals(true, scanBill("Apollo Pharmacy\nCrocin 500mg tab 10   35.00\nTotal 35.00", today).pharmacy)
    }

    @Test
    fun `what a scan shows decides where it can go`() {
        assertEquals(ScanKind.RECEIPT, scanKind("DMart\nMilk 68.00\nRice 450.00\nGrand Total 518.00\nGST 12.50"))
        assertEquals(ScanKind.NUTRITION, scanKind("Nutrition per 100g\nEnergy 380 kcal\nProtein 12g\nTotal Fat 9g"))
        assertEquals(ScanKind.PACK, scanKind("Batch B12\nEXP 08/2027\nMRP 45"))
        assertEquals(ScanKind.UNKNOWN, scanKind("Happy birthday Riya"))
    }

    @Test
    fun `gemini's reading of a bill is checked field by field`() {
        val json = """{"total": 530.5, "shop": "DMart", "date": "2026-11-09", "pharmacy": false,
            "items": [{"name": "Milk 1L", "qty": 2, "price": 68}, {"name": "?", "qty": 1}, {"name": "Rice", "qty": 900, "price": null}]}"""
        val bill = com.houseaffairs.app.feature.quickadd.billFromAi(json, today)!!
        assertEquals(BigDecimal("530.5"), bill.receipt.total)
        assertEquals(LocalDate.of(2026, 11, 9), bill.receipt.date)
        assertEquals(listOf("Milk 1L", "Rice"), bill.items.map { it.name })
        assertEquals(50, bill.items.last().quantity)
        assertNull(com.houseaffairs.app.feature.quickadd.billFromAi("""{"total": null, "items": []}""", today))
        assertNull(com.houseaffairs.app.feature.quickadd.billFromAi("not json", today))
    }
}
