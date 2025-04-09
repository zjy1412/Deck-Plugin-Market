import club.xiaojiawei.DeckStrategy
import club.xiaojiawei.bean.Card
import club.xiaojiawei.bean.area.HandArea
import club.xiaojiawei.bean.area.PlayArea
import club.xiaojiawei.bean.isValid
import club.xiaojiawei.config.log
import club.xiaojiawei.enums.CardTypeEnum
import club.xiaojiawei.enums.RunModeEnum
import club.xiaojiawei.status.WAR
import java.util.HashSet
import club.xiaojiawei.CardAction
import club.xiaojiawei.bean.PlayAction
import club.xiaojiawei.bean.Player

/**
 * 乌索尔天启骑策略
 * 基于乌索尔+暂避锋芒的组合，通过法庭秩序检索关键牌，实现天启四骑士胜利
 * @author zjy1412
 * @date 2025/4/7
 */
class UsorlApocalypseDeckStrategy : DeckStrategy() {

    // Flag to track if Court Order has been played
    private var courtOrderPlayed = false
    
    // Flag to track if Nozdormu has been played
    private var nozdormuPlayed = false

    override fun name(): String {
        return "乌索尔天启骑"
    }

    override fun getRunMode(): Array<RunModeEnum> {
        // 策略允许运行的模式 - 狂野模式
        return arrayOf(RunModeEnum.WILD)
    }

    override fun deckCode(): String {
        // 双水晶学版本
        return "AAEBAcClBwaO0wLH0wKHrQPh5APlsAS0gQcM1RPZ/gLPhgOEsQPM6wP09gPquQTA4gSFjgbTngaiswbP/gYAAA=="
    }

    override fun id(): String {
        return "usorl-apocalypse-deck-strategy"
    }

    /**
     * 换牌策略
     * 优先保留：法庭秩序、时光巨龙诺兹多姆、水晶学、龙鳞军备
     * 对快攻保留：潜水俯冲鹈鹕、深潜炸弹
     */
    override fun executeChangeCard(cards: HashSet<Card>) {
        val toList = cards.toList()

        // 需要保留的卡牌ID
        val keepCardIds = setOf(
            "MAW_016", // 法庭秩序
            "DRG_309", // 时光巨龙诺兹多姆
            "BOT_909", // 水晶学
            "EDR_251",  // 龙鳞军备
            "BAR_873" // 圣礼骑士
        )

        for (card in toList) {
            // 保留关键牌
            if (card.cardId in keepCardIds) {
                continue
            }

            // 其他牌换掉
            cards.remove(card)
        }
    }

    /**
     * 出牌策略
     * 1. 优先使用法庭秩序检索关键牌
     * 2. 尽早打出时光巨龙诺兹多姆
     * 3. 使用乌索尔+暂避锋芒组合
     * 4. 使用天启四骑士进行斩杀
     * 5. 保持场上随从数量少于等于2个
     * 6. 优先进行随从交换而非攻击英雄
     * 7. 防止爆牌（手牌上限10张）
     */
    override fun executeOutCard() {
        var reevaluate = true
        while (reevaluate) {
            reevaluate = false // Assume single pass unless Finley resets it
            
            // 我方玩家
            val me = WAR.me
            if (!me.isValid()) return
            // 敌方玩家
            val rival = WAR.rival
            if (!rival.isValid()) return

            // 获取战场信息
            val handCards = me.handArea.cards
            val playCards = me.playArea.cards
            val hero = me.playArea.hero
            val weapon = me.playArea.weapon
            val power = me.playArea.power

            // 卡牌ID常量
            val COURT_ORDER = "MAW_016"      // 法庭秩序
            val NOZARI = "DRG_309"           // 时光巨龙诺兹多姆
            val USORL = "EDR_259"            // 乌索尔
            val TIME_OUT = "TRL_302"         // 暂避锋芒
            val UTHER_DK = "ICC_829"         // 黑锋骑士乌瑟尔
            val GARRISON_COMMANDER = "AT_080" // 要塞指挥官
            val AUCTIONEER_JAXON = "TOY_528" // 伴唱机
            val SAINT_LIFE_KNIGHT = "BAR_873" // 圣礼骑士
            val ICE_SPIKE = "CORE_UNG_205" // 冰川裂片
            val SNAKE_OIL = "WW_331t" // 蛇油
            val SECURITY_CHECKER = "DMF_125" // 安全检查员
            val CRYSTOLOGY = "BOT_909" // 水晶学
            val FENRIS_JESTER = "TSC_908" // 海中向导芬利爵士
            val COIN = "COIN" // 幸运币

            // 关键随从牌，需要谨慎使用
            val KEY_MINIONS = setOf(
                USORL,              // 乌索尔
                GARRISON_COMMANDER, // 要塞指挥官
                AUCTIONEER_JAXON    // 伴唱机
            )

            // 检查手牌数量，防止爆牌
            val handCardCount = handCards.size

            // Make mutable copies for processing within this loop iteration
            val currentHandCards = handCards.toMutableList()

            var timeOutPlayedThisTurn = false

            // 更新战场随从数 (每次可能出牌/攻击前重新计算)
            var minionCount = me.playArea.cards.count { it.cardType == CardTypeEnum.MINION }

            // 0. 攻击
            // Re-fetch playCards as minions might have been played
            val currentPlayCards = me.playArea.cards.toMutableList()
            minionCount = currentPlayCards.count { it.cardType == CardTypeEnum.MINION } // Update count before attacking
            for (playCard in currentPlayCards) {
                if (playCard.canAttack()) {
                    // 如果对方场上有嘲讽随从，优先攻击嘲讽随从
                    var hasTaunt = false
                    var tauntCard: Card? = null

                    for (rivalCard in rival.playArea.cards) {
                        if (rivalCard.isTaunt) {
                            hasTaunt = true
                            tauntCard = rivalCard
                            break
                        }
                    }

                    if (hasTaunt && tauntCard != null) {
                        log.info { "攻击嘲讽随从: ${tauntCard.cardId}" }
                        playCard.action.attack(tauntCard)
                    // 如果使用了法庭秩序且场上随从多于1个，优先送掉自己的随从
                    } else if (courtOrderPlayed && minionCount > 1 && rival.playArea.cards.isNotEmpty()) {
                        // 尝试进行交换，即使不一定是最优解，目标是减少我方场面
                        var targetMinion: Card? = null
                        // 优先攻击能换掉的或者高威胁随从 (简化：攻击任意随从)
                        targetMinion = rival.playArea.cards.firstOrNull { it.cardType == CardTypeEnum.MINION }
                        if(targetMinion != null) {
                            log.info { "法庭秩序后，尝试送掉随从，攻击: ${targetMinion.cardId}" }
                            playCard.action.attack(targetMinion)
                        } else {
                            log.info { "没有敌方随从可交换，攻击敌方英雄" }
                            playCard.action.attackHero()
                        }
                    } else if (rival.playArea.cards.isNotEmpty()) {
                        // 优先进行随从交换，而非攻击英雄 (标准逻辑)
                        var bestTarget: Card? = null

                        // 策略1：找能一击必杀且自己不会死的随从
                        for (rivalCard in rival.playArea.cards) {
                            if (playCard.atc >= rivalCard.blood() && rivalCard.atc < playCard.blood()) {
                                bestTarget = rivalCard
                                break
                            }
                        }

                        // 策略2：如果没有找到理想目标，找血量最低的随从
                        if (bestTarget == null) {
                            var lowestHealth = Int.MAX_VALUE
                            for (rivalCard in rival.playArea.cards) {
                                if (rivalCard.blood() < lowestHealth) {
                                    lowestHealth = rivalCard.blood()
                                    bestTarget = rivalCard
                                }
                            }
                        }

                        if (bestTarget != null) {
                            log.info { "进行随从交换，攻击: ${bestTarget.cardId}" }
                            playCard.action.attack(bestTarget)
                        } else {
                            log.info { "没有合适的交换目标，攻击敌方英雄" }
                            playCard.action.attackHero()
                        }
                    } else {
                        // 没有敌方随从，直接攻击英雄
                        log.info { "没有敌方随从，攻击敌方英雄" }
                        playCard.action.attackHero()
                    }
                }
            }

            // 1. 检查是否可以使用天启四骑士斩杀
            var hasUtherDK = false
            var hasGarrisonCommander = false
            var hasAuctioneerJaxon = false

            if (hero?.cardId == UTHER_DK) {
                hasUtherDK = true
            }

            // 检查手牌中是否有要塞指挥官和伴唱机
            for (handCard in currentHandCards) {
                if (handCard.cardId == GARRISON_COMMANDER) {
                    hasGarrisonCommander = true
                }
                if (handCard.cardId == AUCTIONEER_JAXON) {
                    hasAuctioneerJaxon = true
                }
            }

            // 如果场上有黑锋骑士乌瑟尔，尝试打出要塞指挥官和伴唱机
            if (hasUtherDK && hasGarrisonCommander && hasAuctioneerJaxon && me.usableResource >= 8 && minionCount <= 1) {
                if (hasGarrisonCommander && me.usableResource >= 2) {
                    val commanderCard = currentHandCards.find { it.cardId == GARRISON_COMMANDER }
                    if (commanderCard != null) {
                        log.info { "打出要塞指挥官" }
                        commanderCard.action.power()
                    }
                }

                if (hasAuctioneerJaxon && me.usableResource >= 2) {
                    val jaxonCard = currentHandCards.find { it.cardId == AUCTIONEER_JAXON }
                    if (jaxonCard != null) {
                        log.info { "打出伴唱机" }
                        jaxonCard.action.power()
                    }
                }

                // 使用英雄技能
                log.info { "使用两次英雄技能获取游戏胜利" }
                me.playArea.power?.let {
                    if (me.usableResource >= it.cost || it.cost == 0) {
                            it.action.lClick()
                            Thread.sleep(5000) // Wait 5 seconds
                    }
                }
                me.playArea.power?.let {
                    if (me.usableResource >= it.cost || it.cost == 0) {
                            it.action.lClick()
                    }
                }
                return
            }

            // 2. 使用乌索尔+暂避锋芒组合
            var hasUsorlInHand = false
            var hasTimeOut = false

            for (handCard in currentHandCards) {
                if (handCard.cardId == USORL) {
                    hasUsorlInHand = true
                }
                if (handCard.cardId == TIME_OUT) {
                    hasTimeOut = true
                }
            }

            // 使用乌索尔+暂避锋芒组合
            if (hasUsorlInHand && hasTimeOut && me.usableResource >= 8) {
                // 先打出乌索尔
                val usorlCard = currentHandCards.find { it.cardId == USORL }
                if (usorlCard != null) {
                    log.info { "打出乌索尔" }
                    usorlCard.action.power()
                    currentHandCards.remove(usorlCard) // Update copy

                    // 紧接着打出暂避锋芒 (如果费用还够且之前没打过)
                    if (!timeOutPlayedThisTurn) {
                        val timeOutCard = currentHandCards.find { it.cardId == TIME_OUT && it.cost <= me.usableResource }
                        if (timeOutCard != null) {
                            log.info { "配合乌索尔打出暂避锋芒" }
                            timeOutCard.action.power()
                            currentHandCards.remove(timeOutCard) // Update copy
                        }
                    }
                }
            }

            // 2.5. 使用幸运币以便提前打出时光巨龙诺兹多姆
            val coinCard = currentHandCards.find { it.cardId == COIN && it.cost <= me.usableResource }
            val nozariCardCheck = currentHandCards.find { it.cardId == NOZARI }

            if (coinCard != null && nozariCardCheck != null &&
                me.usableResource < nozariCardCheck.cost && // 当前费用不够诺兹多姆
                me.usableResource + 1 >= nozariCardCheck.cost) { // 但使用硬币后就够了
                log.info { "使用幸运币以便打出时光巨龙诺兹多姆" }
                coinCard.action.power()
                currentHandCards.remove(coinCard) // Update copy
                // Mana should update in WAR.me.usableResource implicitly after playing coin
            }

            // 3. 尽早打出时光巨龙诺兹多姆
            for (handCard in currentHandCards.toList()) {
                if (handCard.cardId == NOZARI && handCard.cost <= me.usableResource) {
                    log.info { "打出时光巨龙诺兹多姆" }
                    handCard.action.power()
                    nozdormuPlayed = true // Set the flag
                    break
                }
            }

            // 打出黑锋骑士乌瑟尔
            for (handCard in currentHandCards) {
                if (handCard.cardId == UTHER_DK && handCard.cost <= me.usableResource) {
                    log.info { "打出黑锋骑士乌瑟尔" }
                    handCard.action.power()
                }
            }

            // 4. 使用法庭秩序
            for (handCard in currentHandCards.toList()) {
                if (handCard.cardId == COURT_ORDER && handCard.cost <= me.usableResource) {
                    log.info { "使用法庭秩序" }
                    handCard.action.power()
                    courtOrderPlayed = true // Set the flag
                    // Card draw occurred, re-evaluate turn
                    log.info { "法庭秩序抽牌后，重新评估回合" }
                    reevaluate = true
                    continue // Immediately restart the while loop
                }
            }

            // 4.5 打出海中向导芬利爵士 (如果未使用法庭秩序，且手牌不好时考虑)
            // Re-fetch current hand state as Court Order might have drawn cards
            val handAfterOrder = me.handArea.cards.toMutableList()
            // Only consider Finley if Court Order has *not* been played yet in this game
            // (to preserve drawn cards if Court Order was played).
            if (!courtOrderPlayed) { // <--- This condition prevents Finley if Court Order was played
                val finleyCard = handAfterOrder.find { it.cardId == FENRIS_JESTER && it.cost <= me.usableResource }
                if (finleyCard != null) {
                    log.info { "未使用法庭秩序，打出海中向导芬利爵士更换手牌并重新评估回合" }
                    finleyCard.action.power()
                    // 手牌已被替换，后续逻辑将使用新抽到的牌
                    // Set flag to restart the turn evaluation loop
                    reevaluate = true
                    continue // Immediately restart the while loop
                }
            }

            // 打出水晶学过牌 (如果费用允许且手牌不多)
            // Fetch hand again in case Finley swapped it (or other state changed)
            val handForCrystology = me.handArea.cards.toMutableList()
            val handCardCountForCrystology = handForCrystology.size // Use fetched hand size
            val crystologyCard = handForCrystology.find { it.cardId == CRYSTOLOGY && it.cost <= me.usableResource }
            if (crystologyCard != null && handCardCountForCrystology < 9) { // 留1张防爆牌 (基于当前手牌数)
                log.info { "打出水晶学" }
                crystologyCard.action.power()
                // Card draw occurred, re-evaluate turn
                log.info { "水晶学抽牌后，重新评估回合" }
                reevaluate = true
                continue // Restart evaluation loop
            }

            // 打出圣礼骑士 (在水晶学之后考虑，如果费用允许且场面允许)
            val handForKnight = me.handArea.cards.toMutableList() // Fetch latest hand
            minionCount = me.playArea.cards.count { it.cardType == CardTypeEnum.MINION } // Update minion count
            val knightCard = handForKnight.find { it.cardId == SAINT_LIFE_KNIGHT && it.cost <= me.usableResource }
            if (knightCard != null) {
                // Apply minion playing constraints (similar to step 6 logic)
                val maxMinions = if (courtOrderPlayed) 1 else 3 // Max minions allowed based on Court Order status
                if (minionCount < maxMinions) {
                    log.info { "打出圣礼骑士" }
                    knightCard.action.power()
                    // Card draw occurred, re-evaluate turn
                    log.info { "圣礼骑士抽牌后，重新评估回合" }
                    reevaluate = true
                    continue // Restart evaluation loop
                } else {
                    log.info { "场上随从数量已达上限(${minionCount}/${maxMinions})，暂不打出圣礼骑士" }
                }
            }


            // 6. 打出其他可用的牌
            // Use the latest hand card state, especially important if Finley was played
            val finalHandCards = me.handArea.cards.toMutableList() // Explicitly fetch latest hand state
            minionCount = me.playArea.cards.count { it.cardType == CardTypeEnum.MINION } // Update count again before final plays

            for (handCard in finalHandCards.toList()) { // Iterate over a copy for safe removal
                // 费用够
                if (handCard.cost <= me.usableResource) {
                    // 跳过蛇油
                    if (handCard.cardId == SNAKE_OIL) {
                        log.info { "跳过蛇油: ${handCard.cardId}" }
                        continue
                    }

                    // 随从牌，需要控制场上随从数量
                    if (handCard.cardType == CardTypeEnum.MINION) {

                        // 特殊处理：冰川裂片 (CORE_UNG_205)
                        if (handCard.cardId == ICE_SPIKE) {
                            // 寻找攻击力最高的敌方目标 (英雄或随从)
                            var target: Card? = null
                            var maxAtk = -1

                            // 检查敌方随从
                            rival.playArea.cards.filter { it.canBeTargetedByRival() }.forEach { rivalCard ->
                                if (rivalCard.atc > maxAtk) {
                                    maxAtk = rivalCard.atc
                                    target = rivalCard
                                }
                            }
                            // 检查敌方英雄 (如果可以被指向且攻击力更高)
                            val rivalHero = rival.playArea.hero
                            if (rivalHero != null && rivalHero.canBeTargetedByRival() && rivalHero.atc > maxAtk) {
                                target = rivalHero
                            }

                            if (target != null) {
                                log.info { "打出冰川裂片，冻结: ${target.cardId} (Atk: ${target.atc})" }
                                handCard.action.power()?.pointTo(target)
                            } else {
                                // 没有可指向的目标，但费用够，仍然打出 (如果需要站场)
                                log.info { "打出冰川裂片 (无合适冻结目标)" }
                                handCard.action.power()
                            }
                            continue // 处理完冰川裂片，跳过后续普通随从逻辑
                        }

                        // 检查是否是关键随从
                        val isKeyMinion = handCard.cardId in KEY_MINIONS

                        // 法庭秩序后的特殊逻辑
                        if (courtOrderPlayed) {
                            // 不打安全检查员
                            if (handCard.cardId == SECURITY_CHECKER) {
                                log.info { "法庭秩序已使用，跳过安全检查员: ${handCard.cardId}" }
                                continue
                            }
                            // 不打芬利爵士
                            if (handCard.cardId == FENRIS_JESTER) {
                                log.info { "法庭秩序已使用，跳过芬利爵士: ${handCard.cardId}" }
                                continue
                            }
                            // Special handling for Knight of Anointment moved earlier
                            if (handCard.cardId == SAINT_LIFE_KNIGHT) {
                                continue // Already handled specifically before attacks
                            }

                            // 尽量保持场上随从 <= 1
                            if (!isKeyMinion && minionCount < 1) {
                                log.info { "法庭秩序后，打出非关键随从 (场上随从<1): ${handCard.cardId}" }
                                handCard.action.power()
                                minionCount++ // Assume play succeeds for count tracking
                            } else if (!isKeyMinion) {
                                log.info { "法庭秩序后，场上随从已达到1个，不再打出非关键随从: ${handCard.cardId}" }
                            } else {
                                log.info { "法庭秩序后，保留关键随从: ${handCard.cardId}" }
                            }
                        }
                        // 标准逻辑 (未使用法庭秩序)
                        else {
                            // 场上随从少于等于2时，才考虑打出非关键随从
                            if (!isKeyMinion && minionCount <= 3) {
                                // Special handling for Knight of Anointment moved earlier
                                if (handCard.cardId == SAINT_LIFE_KNIGHT) {
                                    continue // Already handled specifically before attacks
                                }
                                log.info { "打出非关键随从 (未使用法庭秩序，场上随从<=3): ${handCard.cardId}" }
                                handCard.action.power()
                                // Check if Safety Inspector was played, if so, re-evaluate
                                if (handCard.cardId == SECURITY_CHECKER) {
                                    log.info { "安全检查员触发效果后，重新评估回合" } // Note: Safety Inspector adds spells cast, doesn't draw from deck itself. Re-evaluating as requested.
                                    reevaluate = true
                                    continue // Restart evaluation loop
                                }
                                // Otherwise, just update count locally for this pass
                                minionCount++
                            } else if (isKeyMinion) {
                                log.info { "保留关键随从: ${handCard.cardId}" }
                            } else {
                                log.info { "场上随从已达到上限(${minionCount}/3)，不再打出非关键随从: ${handCard.cardId}" }
                            }
                        }
                    }
                    // 法术牌直接打出
                    else if (handCard.cardType == CardTypeEnum.SPELL &&
                             handCard.cardId != TIME_OUT && // 避免再次打出Time Out!
                             handCard.cardId != COIN) { // Coin handled separately below
                        // Skip Crystology here as it's handled earlier
                        if (handCard.cardId == CRYSTOLOGY) continue
                        log.info { "打出法术: ${handCard.cardId}" }
                        handCard.action.power()
                    } else if (handCard.cardId == COIN && (nozdormuPlayed || me.usableResource >= 4)) {
                        log.info { "打出幸运币 (条件满足: 诺兹多姆已使用或费用>=4)" }
                        handCard.action.power()
                    }
                }
            }
        } // End of while(reevaluate) loop
    }

    override fun executeDiscoverChooseCard(vararg cards: Card): Int {
        // 发现牌策略
        // 优先选择关键牌
        val keyCardIds = setOf(
            "MAW_016", // 法庭秩序
            "DRG_309", // 时光巨龙诺兹多姆
            "EDR_259", // 乌索尔
            "TRL_302", // 暂避锋芒
            "ICC_829", // 黑锋骑士乌瑟尔
            "AT_080",  // 要塞指挥官
            "TOY_528"  // 伴唱机
        )

        for (i in cards.indices) {
            if (cards[i].cardId in keyCardIds) {
                return i
            }
        }

        // 如果没有关键牌，选择费用最低的牌
        var lowestCostIndex = 0
        var lowestCost = Int.MAX_VALUE

        for (i in cards.indices) {
            if (cards[i].cost < lowestCost) {
                lowestCost = cards[i].cost
                lowestCostIndex = i
            }
        }

        return lowestCostIndex
    }
}
