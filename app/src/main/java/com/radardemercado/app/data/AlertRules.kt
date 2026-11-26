package com.radardemercado.app.data

object AlertRules {
    enum class Action { NONE, TRIGGER, REARM }

    fun isTargetHit(direction: AlertDirection, targetPrice: Double, observedPrice: Double): Boolean =
        when (direction) {
            AlertDirection.ABOVE -> observedPrice >= targetPrice
            AlertDirection.BELOW -> observedPrice <= targetPrice
        }

    fun action(
        direction: AlertDirection,
        targetPrice: Double,
        observedPrice: Double,
        waitingForReset: Boolean
    ): Action {
        val hit = isTargetHit(direction, targetPrice, observedPrice)
        return when {
            waitingForReset && !hit -> Action.REARM
            waitingForReset -> Action.NONE
            hit -> Action.TRIGGER
            else -> Action.NONE
        }
    }
}
