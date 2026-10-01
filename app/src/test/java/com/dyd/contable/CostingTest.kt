package com.dyd.contable

import com.dyd.contable.domain.BomInput
import com.dyd.contable.domain.Costing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CostingTest {
    private val palletBom = listOf(
        BomInput(quantityPerUnit = 7.0, wastePercent = 2.0, unitCost = 600_000), // tablas
        BomInput(quantityPerUnit = 3.0, wastePercent = 2.0, unitCost = 500_000), // largueros
        BomInput(quantityPerUnit = 9.0, wastePercent = 2.0, unitCost = 500_000), // tacos
        BomInput(quantityPerUnit = 0.25, wastePercent = 5.0, unitCost = 800_000), // clavos (kg)
    )

    @Test fun consumptionIncludesWaste() = assertEquals(71.4, Costing.consumption(7.0, 2.0, 10.0), 1e-9)

    @Test fun unitCostMatchesDatabaseExample() {
        // Mismo caso probado contra Postgres: 10 pallets costaron 106.140.000 en materiales.
        val cost = Costing.unitCost(palletBom, laborPerUnit = 300_000, overheadPerUnit = 100_000)
        assertEquals(10_614_000L, cost.materials)
        assertEquals(11_014_000L, cost.total)
    }

    @Test fun marginAndTargetPrice() {
        val cost = Costing.unitCost(emptyList(), 7_000_000, 1_000_000)
        assertEquals(20.0, cost.marginAt(10_000_000)!!, 1e-9)
        assertEquals(10_000_000L, cost.priceForMargin(20.0))
        assertNull(cost.marginAt(0))
    }

    @Test fun weightedAverageCost() {
        assertEquals(600_000L, Costing.weightedAverage(100.0, 500_000, 100.0, 700_000))
        assertEquals(700_000L, Costing.weightedAverage(-5.0, 500_000, 10.0, 700_000))
    }
}
