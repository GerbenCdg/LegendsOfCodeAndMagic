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
            val abilities = input.next()
            val myHealthChange = input.nextInt()
            val opponentHealthChange = input.nextInt()
            val cardDraw = input.nextInt()

            val card = Card(cardNumber, instanceId, location, cardType, cost, attack, defense, abilities,
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
            player.remainingMana -= cardToSummon.cost
            summonedCards++

            when {
                cardToSummon.type == CardType.CREATURE -> summonString += "SUMMON ${cardToSummon.id}; "
                cardToSummon.type == CardType.GREEN_ITEM -> {
                    summonString += "USE ${cardToSummon.id} ${targetForGreenItem(cards).id};"
                }
                cardToSummon.type == CardType.RED_ITEM -> {
                    summonString += "USE ${cardToSummon.id} ${targetForRedItem(cards).id};"
                }
                cardToSummon.type == CardType.BLUE_ITEM -> {
                    summonString += "USE ${cardToSummon.id} -1;"
                }
            }

            summonableCards = cards.summonable(player.remainingMana, summonedCards)
        }

        println(summonString + attackString(cards))
    }
}

class Location {
    companion object {
        const val HANDS = 0
        const val PLAYER = 1
        const val OPPONENT = -1
    }
}

class Ability {
    companion object {
        const val BREAKTHROUGH = "B"
        const val CHARGE = "C"
        const val DRAIN = "D"
        const val GUARD = "G"
        const val LETHAL = "L"
        const val WARD = "W"

        fun hasGuard(card: Card) : Boolean{
            return card.abilities.contains(GUARD)
        }
    }
}

class CardType {
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
    return this.filter { it.location == Location.PLAYER && !it.hasPlayed }
}

fun List<Card>.onBoardOpponent(): List<Card> {
    return this.filter { it.location == Location.OPPONENT && it.defense > 0 }
}

fun List<Card>.guards(): List<Card> {
    return this.filter { Ability.hasGuard(it) }
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
                it.type == CardType.CREATURE
                        || it.type == CardType.BLUE_ITEM
                        || it.type == CardType.GREEN_ITEM && onBoardPlayer.isNotEmpty()
                        || it.type == CardType.RED_ITEM && onBoardOpponent.isNotEmpty()
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
    val playerGuard = playerCards.firstOrNull { it.abilities.contains(Ability.GUARD) }
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

fun attackString(cards: List<Card>): String {


    var attackString = ""

    var guards = cards
            .onBoardOpponent().guards()
            .sortedWith(compareBy({ -it.defense }, { it.attack }))

    val attackingCards = cards.onBoardPlayer()
            .filter { it.attack > 0 }

    for (attackingCard in attackingCards) {

        val attackedCard = cardToAttack(attackingCard, cards)
        var idToAttack: Int

        if (attackedCard != null) {
            idToAttack = attackedCard.id

            attackingCard.hasPlayed = true
            attackedCard.defense -= attackingCard.attack

        } else {
            idToAttack = -1
        }
        attackString += "ATTACK ${attackingCard.id} $idToAttack ;"

    }

    if (attackString == "")
        return "PASS"

    return attackString
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


fun cardToAttack(playerCard: Card, cards: List<Card>): Card? {
    val opponentCards = cards.onBoardOpponent()
    val opponentGuards = cards.onBoardOpponent().guards()

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

    if (opponentCards.isNotEmpty()) {
        return opponentCards[0]
    }
    return null // attack the player
}


// attack in priority a card with defense close to the attackingpoints
// with cards ordered by attack :
// card.attack >=

fun cardAttackPriority(card: Card): Double {

    val abilities = card.abilities
    var priority = 0.0

    when {
        abilities.contains(Ability.GUARD) -> priority += 1
        abilities.contains(Ability.BREAKTHROUGH) -> priority += 1
        abilities.contains(Ability.CHARGE) -> priority += 1
    }

    priority += (card.attack / card.defense)

    return priority
}

fun debugPrint(msg: Any?) {
    System.err.println(msg.toString())
}

data class Card(val number: Int, val id: Int, val location: Int, val type: Int, val cost: Int, val attack: Int,
                var defense: Int, val abilities: String, val hpChange: Int, val opponentHpChange: Int,
                val additionalCardsDraw: Int, var hasPlayed: Boolean = false)

data class Player(val hp: Int, val mana: Int, val remainingCards: Int, val rune: Int, var remainingMana: Int = mana)


