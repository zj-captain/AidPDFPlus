package com.ysdc.aidpdf.ads.config

object AdsConfigBridge {

    private const val LOCAL_ADS_CONFIG_JSON = """
        {
          "ac_launch": [
            {
              "ad_unit_id": "ca-app-pub-3940256099942544/9257395921",
              "ad_paltfrom": "admob",
              "ad_type": "op",
              "ad_timelimit": 13800
            },
            {
              "ad_unit_id": "CF08D70991970B5C90747A9B20220F12",
              "ad_paltfrom": "tp",
              "ad_type": "op",
              "ad_timelimit": 13800
            }
          ],
          "ac_back_int": [
            {
              "ad_unit_id": "ca-app-pub-3940256099942544/1033173712",
              "ad_paltfrom": "admob",
              "ad_type": "int",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "6EF26876E9E56CD96DD4F01E2A1D9412",
              "ad_paltfrom": "tp",
              "ad_type": "int",
              "ad_timelimit": 3000
            }
          ],
          "ac_result_int": [
            {
              "ad_unit_id": "ca-app-pub-3940256099942544/8691691433",
              "ad_paltfrom": "admob",
              "ad_type": "int",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "3E1F59FFE26B1D4BBC3A7C2E51D57A12",
              "ad_paltfrom": "tp",
              "ad_type": "int",
              "ad_timelimit": 3000
            }
          ],
          "ac_main_nat": [
            {
              "ad_unit_id": "ca-app-pub-3940256099942544/2247696110",
              "ad_paltfrom": "admob",
              "ad_type": "nat",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "7879155BC6A5285C67D3C003D40D7E12",
              "ad_paltfrom": "tp",
              "ad_type": "nat",
              "ad_timelimit": 3000
            }
          ],
          "ac_result_nat": [
            {
              "ad_unit_id": "ca-app-pub-3940256099942544/1044960115",
              "ad_paltfrom": "admob",
              "ad_type": "nat",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "161079FEE749053E5963BF20648FF012",
              "ad_paltfrom": "tp",
              "ad_type": "nat",
              "ad_timelimit": 3000
            }
          ],
          "ac_main_banner": [
            {
              "ad_unit_id": "ca-app-pub-3940256099942544/6300978111",
              "ad_paltfrom": "admob",
              "ad_type": "banner",
              "ad_timelimit": 3000
            },
            {
              "ad_unit_id": "AC5896E2EAF7F1AD848820F04588C512",
              "ad_paltfrom": "tp",
              "ad_type": "banner",
              "ad_timelimit": 3000
            }
          ]
        }
    """

    fun localCatalog(): AdsCatalog {
        // 新广告模块当前先只接本地默认配置，后续如果接远端配置，再在这个桥接层扩展。
        return AdsConfigParser.parse(LOCAL_ADS_CONFIG_JSON)
    }
}
