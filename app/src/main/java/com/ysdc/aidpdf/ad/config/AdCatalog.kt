package com.ysdc.aidpdf.ad.config

data class AdCatalog(
    private val unitsByScene: Map<AdScene, List<AdUnitConfig>> = emptyMap()
) {

    fun unitsFor(scene: AdScene): List<AdUnitConfig> {
        return unitsByScene[scene].orEmpty()
    }

    companion object {
        fun from(units: Map<AdScene, List<AdUnitConfig>>): AdCatalog {
            return AdCatalog(units.filterValues { it.isNotEmpty() })
        }
    }
}

