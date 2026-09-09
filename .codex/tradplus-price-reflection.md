1. 项目的 libs 目录下引入 aar，
   2.gradle 文件里引入
   implementation (name: 'compare_price-release', ext: 'aar')
3. 在调用页面导入
   import static com.tp.compareprice.ComparePriceUtil.recursiveComparePrice;

建议调用时机：在需要展示广告前，查询 TP 的 isReady，如果返回 true 说明有可用广告，可以调用 recursiveComparePrice，传入 tp 广告位 id，返回价格。

备注：
1. 默认价格探测区间，0~10000$
2. 返回美元单位的 eCPM，保留小数点后两位
3. 一次只能传入一个 tp 广告位 id，如果需要查多个 id 的，可多次调用。

附录为 AAR 文件，如有需要也可用 Jar 包