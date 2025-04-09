import club.xiaojiawei.DeckPlugin

/**
 * 乌索尔天启骑插件
 * @author zjy1412
 * @date 2025/4/7
 */
class UsorlApocalypsePlugin: DeckPlugin {
    override fun description(): String {
        return "乌索尔天启骑插件，基于乌索尔+暂避锋芒的组合，通过法庭秩序检索关键牌，实现天启四骑士胜利"
    }

    override fun author(): String {
        return "zjy1412"
    }

    override fun version(): String {
        return "1.0.0"
    }

    override fun id(): String {
        return "usorl-apocalypse-deck"
    }

    override fun name(): String {
        return "乌索尔天启骑"
    }

    override fun homeUrl(): String {
        return "https://www.iyingdi.com/tz/post/5590414"
    }
}
