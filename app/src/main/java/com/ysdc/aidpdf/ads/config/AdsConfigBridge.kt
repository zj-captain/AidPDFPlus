package com.ysdc.aidpdf.ads.config

import com.ysdc.aidpdf.ad.remote.NatConfig
import com.ysdc.aidpdf.ads.core.AdsLogger
import com.ysdc.aidpdf.remote.RemoteConfigUtils

object AdsConfigBridge {
    @Volatile
    var virtual_block_switch = 1  //隐藏虚拟按键逻辑开关

    var natConfig: NatConfig? = null    //原生广告误触配置
    private const val REMOTE_AD_CONFIG_KEY = "ac_ad_config"

    // 供 RemoteConfigUtils.setDefaultsAsync 作为兜底默认值
    internal const val LOCAL_ADS_CONFIG_JSON = """
        {
          "ac_launch": [
            {
              "ad_unit_id": "ca-app-pub-9270792764224532/1447961922",
              "ad_paltfrom": "admob",
              "ad_type": "op",
              "ad_timelimit": 13800
            },
            {
              "ad_unit_id": "4A1E0CCC2D7D9C8F146EDD76F3BD6E12",
              "ad_paltfrom": "tp",
              "ad_type": "op",
              "ad_timelimit": 13800
            }
          ],
          "ac_back_int": [
            {
              "ad_unit_id": "ca-app-pub-9270792764224532/8703740613",
              "ad_paltfrom": "admob",
              "ad_type": "int",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "5041097CA9419D1C1D05A61F8AEA9212",
              "ad_paltfrom": "tp",
              "ad_type": "int",
              "ad_timelimit": 3000
            }
          ],
          "ac_result_int": [
            {
              "ad_unit_id": "ca-app-pub-9270792764224532/8703740613",
              "ad_paltfrom": "admob",
              "ad_type": "int",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "5041097CA9419D1C1D05A61F8AEA9212",
              "ad_paltfrom": "tp",
              "ad_type": "int",
              "ad_timelimit": 3000
            }
          ],
          "ac_main_nat": [
            {
              "ad_unit_id": "ca-app-pub-9270792764224532/4596974643",
              "ad_paltfrom": "admob",
              "ad_type": "nat",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "386CFFD673E1FB10106E36649FBDE312",
              "ad_paltfrom": "tp",
              "ad_type": "nat",
              "ad_timelimit": 3000
            }
          ],
          "ac_result_nat": [
            {
              "ad_unit_id": "ca-app-pub-9270792764224532/4596974643",
              "ad_paltfrom": "admob",
              "ad_type": "nat",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "386CFFD673E1FB10106E36649FBDE312",
              "ad_paltfrom": "tp",
              "ad_type": "nat",
              "ad_timelimit": 3000
            }
          ],
          "ac_main_banner": [
            {
              "ad_unit_id": "ca-app-pub-9270792764224532/4081969320",
              "ad_paltfrom": "admob",
              "ad_type": "banner",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "0EEBA179B9D67F343D3FB7A19D59C912",
              "ad_paltfrom": "tp",
              "ad_type": "banner",
              "ad_timelimit": 3000
            }
          ]
        }
    """

    /**
     * 本地默认广告目录，启动时立即可用，保证首次启动不空。
     */
    fun localCatalog(): AdsCatalog {
        return AdsConfigParser.parse(LOCAL_ADS_CONFIG_JSON)
    }

    /**
     * 从 Firebase Remote Config 拉取远程广告配置。
     * - 远程 JSON 为空或解析失败时返回 null，由调用方决定是否保持当前配置。
     * - 解析成功返回 AdsCatalog，可直接传给 Ads.configure() 实现热切换。
     */
    fun remoteCatalog(): AdsCatalog? {
        val json = RemoteConfigUtils.getString(REMOTE_AD_CONFIG_KEY)
        if (json.isBlank()) {
            AdsLogger.d("远程广告配置(ac_ad_config)为空，使用本地默认配置")
            return null
        }
        return runCatching {
            AdsConfigParser.parse(json, fallback = localCatalog())
        }.onFailure {
            AdsLogger.w("远程广告配置解析失败，使用本地默认配置：${it.message}")
        }.getOrNull()
    }
}
