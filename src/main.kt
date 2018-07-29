import java.util.*

fun main(args: Array<String>) {
    val input = Scanner(System.`in`)
    var player: Player
    var opponent: Player
    var isPlaying = false

    while (true) {

        player = Player(input.nextInt(), input.nextInt(), input.nextInt(), input.nextInt())
        opponent = Player(input.nextInt(), input.nextInt(), input.nextInt(), input.nextInt())

        if (!isPlaying && player.mana == 1) isPlaying = true

        val opponentHand = input.nextInt()
        // cards in hand + summoned cards
        val cardCount = input.nextInt()

        var cards: List<Card> = emptyList()

        var summonedCards = 0
        var handCards = 0

        for (i in 0 until cardCount) {
            val cardNumber = input.nextInt()
            val instanceId = input.nextInt()
            val location = input.nextInt()
            val cardType = input.nextInt()
            val cost = input.nextInt()
            val attack = input.nextInt()
            val defense = input.nextInt()
            val abilitiesStr = input.next()
            val myHealthChange = input.nextInt()
            val opponentHealthChange = input.nextInt()
            val cardDraw = input.nextInt()

            val card = Card(cardNumber, instanceId, location, CardType(cardType), cost, attack, defense, Abilities(abilitiesStr),
                    myHealthChange, opponentHealthChange, cardDraw)

            cards += card

            if (card.location == Location.HANDS) {
                handCards++
            } else if (card.location == Location.PLAYER) {
                summonedCards++
            }
        }


        if (!isPlaying) {
            // TODO stragy to select better cards
            // make sure to have atleast some low-cost cards in deck
            println("PASS")
            continue
        }

        var summonString = ""

        var summonableCards = cards.summonable(player.remainingMana, summonedCards)

        while (summonableCards.isNotEmpty()) {

            val cardToSummon = cardToSummon(cards, summonableCards)

            cardToSummon.location = Location.UNUSABLE
            player.remainingMana -= cardToSummon.cost

            if (cardToSummon.isCreature())
                summonedCards++

            when {
                cardToSummon.isCreature() -> summonString += SummonAction(cardToSummon)
                cardToSummon.isGreenItem() -> {
                    summonString += UseAction(cardToSummon, targetForGreenItem(cards))
                }
                cardToSummon.isRedItem() -> {
                    summonString += UseAction(cardToSummon, targetForRedItem(cards))
                }
                cardToSummon.isBlueItem() -> {
                    summonString += UseAction(cardToSummon.id, -1)
                }
            }

            summonableCards = cards.summonable(player.remainingMana, summonedCards)
        }

        println(summonString + attackAction(cards, opponent))
    }
}

class Location {
    companion object {
        const val HANDS = 0
        const val PLAYER = 1
        const val OPPONENT = -1
        // makes sure the card isn't played, but isn't picked to be summoned again either
        // if:
        // - a creature just got summoned
        // - an item just got used
        // - a creature just attacked
        const val UNUSABLE = 2
    }
}


class Abilities(var abilities: String) {

    companion object {
        const val BREAKTHROUGH = "B"
        const val CHARGE = "C"
        const val DRAIN = "D"
        const val GUARD = "G"
        const val LETHAL = "L"
        const val WARD = "W"
    }

    fun hasBreakThrough(): Boolean {
        return abilities.contains(BREAKTHROUGH)
    }

    fun hasCharge(): Boolean {
        return abilities.contains(CHARGE)
    }

    fun hasDrain(): Boolean {
        return abilities.contains(DRAIN)
    }

    fun hasGuard(): Boolean {
        return abilities.contains(GUARD)
    }

    fun hasWard(): Boolean {
        return abilities.contains(WARD)
    }

    fun hasLethal(): Boolean {
        return abilities.contains(LETHAL)
    }
}

class CardType(var cardType: Int) {

    companion object {
        const val CREATURE = 0
        const val GREEN_ITEM = 1 // Target : player creature.
        const val RED_ITEM = 2   // Target : opponent creatures.
        const val BLUE_ITEM = 3  // Target : -1 for effect on player or opponent
        // if negative card, the id of an opponent's creature can also be used instead
    }
}


fun List<Card>.inHands(): List<Card> {
    return filter { it.location == Location.HANDS }
}

fun List<Card>.onBoardPlayer(): List<Card> {
    return this.filter { it.location == Location.PLAYER }
}

fun List<Card>.onBoardOpponent(): List<Card> {
    return this.filter { it.location == Location.OPPONENT && it.defense > 0 }
}

fun List<Card>.guards(): List<Card> {
    return this.filter { it.abilities.hasGuard() }
}

// this method needs to be called on the list containing ALL THE CARDS
fun List<Card>.summonable(remainingMana: Int, summonedCards: Int): List<Card> {

    if (summonedCards > 6)
        return emptyList()

    val onBoardOpponent = this.onBoardOpponent()
    val onBoardPlayer = this.onBoardPlayer()

    return this.inHands()
            .filter { it.cost <= remainingMana }
            .filter {
                it.isCreature() || it.isBlueItem()
                        || it.isGreenItem() && onBoardPlayer.isNotEmpty()
                        || it.isRedItem() && onBoardOpponent.isNotEmpty()
            }
}


fun cardToSummon(cards: List<Card>, summonableCards: List<Card>): Card {

    // if no guards in game, summon a guard first if available
    if (cards.inHands().guards().isEmpty()) {
        val summonableGuards = summonableCards.guards()

        if (summonableGuards.isNotEmpty())
            return summonableGuards.first()
    }

    return summonableCards.sortedBy { -it.cost }
            .first()
}


fun targetForGreenItem(cards: List<Card>): Card {

    val playerCards = cards.onBoardPlayer();
    val playerGuard = playerCards.firstOrNull { it.abilities.hasGuard() }
    if (playerGuard != null)
        return playerGuard

    return cards.onBoardPlayer().first()
}

fun targetForRedItem(cards: List<Card>): Card {

    val opponentGuards = cards.onBoardOpponent().guards()
    if (opponentGuards.isNotEmpty()) {
        return opponentGuards.first()
    }
    // todo take in account card.hpChange and card.opponentHpChange
    return cards.onBoardOpponent().first()
}

fun attackAction(cards: List<Card>, opponent: Player): Action {

    val actions = Action.emptyInstance()

    val attackingCards = cards.onBoardPlayer()
            .filter { it.attack > 0 }

    val actionsToWin = actionsToWin(cards, opponent)

    if (!actionsToWin.isPass()) {
        return actionsToWin
    } else {

        for (attackingCard in attackingCards) {

            val attackedCard = cardToAttack(attackingCard, cards, opponent)

            val attackAction = if (attackedCard != null)
                attackingCard.attack(attackedCard)
            else
                attackingCard.attackPlayer(opponent)

            actions += attackAction
        }
    }
    return actions
}

fun attackingGuardId(cards: List<Card>, guard: Card): Int {

    val onBoardPlayer = cards.onBoardPlayer()
            .sortedBy { it.attack }

    val onBoardPlayerOptimal = onBoardPlayer.filter { it.attack > guard.defense }

    if (onBoardPlayerOptimal.isNotEmpty()) {
        return onBoardPlayerOptimal.first().id
    } else {

        return if (onBoardPlayer.isEmpty()) {
            0
        } else {
            onBoardPlayer.first().id
        }
    }
}


fun cardToAttack(playerCard: Card, cards: List<Card>, opponent: Player): Card? {
    val opponentCards = cards.onBoardOpponent()
    val opponentGuards = cards.onBoardOpponent().guards()
            .sortedBy { -attackWorthiness(opponentCards, playerCard, it) }

    // attack order :
    // - cards with high attack and low defense

    // player alive : card.defense > ecard.attack
    // player dead : card.defense <= ecard.attack

    // enemy alive : ecard.defense > card.attack
    // enemy dead : ecard.defense <= card.attack

    // player alive, enemy dead :   card.defense > ecard.attack && ecard.defense <= card.attack
    // both alive :                 card.defense > ecard.attack && ecard.defense > card.attack
    // both dead :                  card.defense <= ecard.attack && ecard.defense <= card.attack
    // player dead, enemy alive :   card.defense <= ecard.attack && ecard.defense > card.attack


    if (opponentGuards.isNotEmpty()) {

        val optimal = opponentGuards
                .filter { it.defense < playerCard.attack }

        if (optimal.isNotEmpty()) {
            return optimal[0]
        }
        return opponentGuards[0]
    }

    // TODO is it more worthy to attack the opponent or an opponent's creature ?

    if (opponentCards.isNotEmpty()) {
        if (playerCard.abilities.hasLethal()) {
            return opponentCards.filter { !it.abilities.hasWard() }
                    .sortedBy { -it.defense }
                    .first()
        }
        return opponentCards.sortedBy { -attackWorthiness(opponentCards, playerCard, it) }
                .first()
    }
    return null // attack the player
}


fun attackWorthiness(opponentCards: List<Card>, playerCard: Card, opponentCard: Card): Double {

    var worthiness = 0.0
    //  consider abilities :
    //   BREAKTHROUGH
    //   CHARGE
    //   DRAIN
    //   GUARD
    //   LETHAL
    //   WARD

    val combatResults = simulateAttack(playerCard, opponentCard)
    val playerResult = combatResults.first
    val opponentResult = combatResults.second

    val playerAlive = playerResult.newDefense > 0
    val opponentAlive = opponentResult.newDefense > 0

    val potentialDmgLost = playerResult.potentialDmg - playerResult.dmgDone

    if (!playerCard.abilities.hasBreakThrough()) {
        worthiness -= potentialDmgLost
        debugPrint("(${playerCard.id}) worthiness for ${opponentCard.id}: $worthiness")
    }
    // todo worthiness increases if drain and card.dmg > opponentcard.dmg

    return worthiness
}
// attack in priority a card with defense close to the attackingpoints
// with cards ordered by attack :
// card.attack >=

fun simulateAttack(playerCard: Card, opponentCard: Card): Pair<CombatResultPlayer, CombatResultOpponent> {

    var newPlayerHp = playerCard.defense
    var newOpponentHp = opponentCard.defense

    if (playerCard.abilities.hasLethal()) {
        newOpponentHp = 0
    } else {
        newOpponentHp -= playerCard.attack
    }

    if (opponentCard.abilities.hasWard()) {
        newOpponentHp = opponentCard.defense
    }

    if (opponentCard.abilities.hasLethal()) {
        newPlayerHp = 0
    } else {
        newPlayerHp -= opponentCard.attack
    }

    if (playerCard.abilities.hasWard()) {
        newPlayerHp = playerCard.defense
    }

    val dmgDoneByPlayer = opponentCard.defense - newOpponentHp.coerceAtLeast(0)
    val totalDmgDoneByPlayer: Int

    val dmgDoneByOpponent = playerCard.defense - newPlayerHp.coerceAtLeast(0)

    // TODO check special case where card has both Breakthrough and Lethal
    if (!playerCard.abilities.hasBreakThrough()) {
        totalDmgDoneByPlayer = dmgDoneByPlayer
    } else {
        totalDmgDoneByPlayer = opponentCard.defense - newOpponentHp
    }

    val potentialDmg = if (opponentCard.abilities.hasWard()) 0 else playerCard.attack

    val playerResult = CombatResultPlayer(newPlayerHp, potentialDmg, dmgDoneByPlayer, totalDmgDoneByPlayer)
    val opponentResult = CombatResultOpponent(newOpponentHp, dmgDoneByOpponent)

    return Pair(playerResult, opponentResult)
}

// target of items depending on abilities given

fun actionsToWin(cards: List<Card>, opponent: Player): Action {

    val winActions = Action.emptyInstance()

    if (cards.onBoardOpponent().guards().isNotEmpty()) {
        // TODO also check if there are guards, check if guards have wards
        return Action.emptyInstance()
    }

    val playerCards = cards.onBoardPlayer()
    var totalDmg = playerCards.filter { it.isCreature() }.sumBy { it.attack }
    totalDmg += playerCards.filter { it.isBlueItem() }.sumBy { -it.defense - it.opponentHpChange }

    if (totalDmg > opponent.hp) {
        for (card in playerCards) {
            when {
                card.isCreature() -> winActions += AttackAction(card.id, -1)
                card.isBlueItem() -> winActions += UseAction(card.id, -1)
            }
        }
    }
    return winActions
}


fun debugPrint(msg: Any?) {
    System.err.println(msg.toString())
}

open class Action protected constructor(private var actionStr: String) {

    private fun intimidate(): String {
        return ""
    }

    companion object {
        fun emptyInstance(): Action {
            return Action("")
        }
    }

    fun isPass(): Boolean {
        return actionStr == ""
    }

    override fun toString(): String {
        if (isPass()) return "PASS"
        return actionStr + intimidate() + ";"
    }

    operator fun plusAssign(other: Action) {
        if (isPass()) {
            actionStr = other.toString()
        } else {
            actionStr += other.toString()
        }
    }
}

class SummonAction(private val playerCardId: Int) : Action("SUMMON $playerCardId") {

    constructor(card: Card) : this(card.id)
}

class AttackAction(private val id1: Int, private val id2: Int) : Action("ATTACK $id1 $id2") {

    constructor(card1: Card, card2: Card) : this(card1.id, card2.id)
}

class UseAction(private val id1: Int, private val id2: Int) : Action("USE $id1 $id2") {

    constructor(card1: Card, card2: Card) : this(card1.id, card2.id)
}


/**
 * @param newDefense defense after the attack
 * @param potentialDmg the potential damage : 0 if the opponentCard has ward, else playerCard.attack
 * @param dmgDone real damage inflicted to opponentCard
 * @param totalDmgDone sum of damage to opponentCard + damage to the opponent if the card has breakthrough
 */
data class CombatResultPlayer(val newDefense: Int, val potentialDmg: Int, val dmgDone: Int, val totalDmgDone: Int)

/**
 * @param newDefense defense after the attack
 * @param dmgDone real damage inflicted to playerCard
 */
data class CombatResultOpponent(val newDefense: Int, val dmgDone: Int)


data class Card(val number: Int, val id: Int, var location: Int, val type: CardType, val cost: Int, val attack: Int,
                var defense: Int, val abilities: Abilities, val hpChange: Int, val opponentHpChange: Int,
                val additionalCardsDraw: Int, var hasPlayed: Boolean = false) {


    fun attack(opponentCard: Card): AttackAction {
        this.location = Location.UNUSABLE

        if (this.abilities.hasLethal()) {
            opponentCard.defense = 0

        } else if (opponentCard.abilities.hasWard()) {
            // nothing happens
        } else {
            opponentCard.defense -= this.attack
        }

        return AttackAction(this, opponentCard)
    }

    fun attackPlayer(opponent: Player): AttackAction {
        this.location = Location.UNUSABLE
        opponent.hp -= this.attack

        return AttackAction(this.id, -1)
    }


    fun isCreature(): Boolean {
        return type.cardType == CardType.CREATURE
    }

    fun isGreenItem(): Boolean {
        return type.cardType == CardType.GREEN_ITEM
    }

    fun isRedItem(): Boolean {
        return type.cardType == CardType.RED_ITEM
    }

    fun isBlueItem(): Boolean {
        return type.cardType == CardType.BLUE_ITEM
    }
}


data class Player(var hp: Int, val mana: Int, val remainingCards: Int, val rune: Int, var remainingMana: Int = mana)


