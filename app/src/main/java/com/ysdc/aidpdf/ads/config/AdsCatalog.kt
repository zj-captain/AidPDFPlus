package com.ysdc.aidpdf.ads.config

data class AdsCatalog(
    private val unitsByScene: Map<AdsScene, List<AdsUnitConfig>> = emptyMap()
) {

    fun unitsFor(scene: AdsScene): List<AdsUnitConfig> {
        return unitsByScene[scene].orEmpty()
    }

    fun unitsFor(scene: AdsScene, platform: AdsPlatform): List<AdsUnitConfig> {
        return unitsFor(scene).filter { it.platform == platform }
    }

    fun containsUnit(scene: AdsScene, platform: AdsPlatform, unitId: String): Boolean {
        return unitsFor(scene, platform).any { it.unitId == unitId }
    }

    companion object {
        fun from(units: Map<AdsScene, List<AdsUnitConfig>>): AdsCatalog {
            return AdsCatalog(units.filterValues { it.isNotEmpty() })
        }
    }
}
