package com.radardemercado.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertRulesTest {
    @Test fun aboveTriggersAtOrAboveTarget() {
        assertFalse(AlertRules.isTargetHit(AlertDirection.ABOVE, 100.0, 99.99))
        assertTrue(AlertRules.isTargetHit(AlertDirection.ABOVE, 100.0, 100.0))
        assertTrue(AlertRules.isTargetHit(AlertDirection.ABOVE, 100.0, 101.0))
    }

    @Test fun belowTriggersAtOrBelowTarget() {
        assertFalse(AlertRules.isTargetHit(AlertDirection.BELOW, 100.0, 100.01))
        assertTrue(AlertRules.isTargetHit(AlertDirection.BELOW, 100.0, 100.0))
        assertTrue(AlertRules.isTargetHit(AlertDirection.BELOW, 100.0, 99.0))
    }

    @Test fun recurringAboveWaitsThenRearms() {
        assertTrue(AlertRules.action(AlertDirection.ABOVE, 100.0, 101.0, false) == AlertRules.Action.TRIGGER)
        assertTrue(AlertRules.action(AlertDirection.ABOVE, 100.0, 102.0, true) == AlertRules.Action.NONE)
        assertTrue(AlertRules.action(AlertDirection.ABOVE, 100.0, 99.0, true) == AlertRules.Action.REARM)
        assertTrue(AlertRules.action(AlertDirection.ABOVE, 100.0, 101.0, false) == AlertRules.Action.TRIGGER)
    }

    @Test fun recurringBelowWaitsThenRearms() {
        assertTrue(AlertRules.action(AlertDirection.BELOW, 100.0, 99.0, false) == AlertRules.Action.TRIGGER)
        assertTrue(AlertRules.action(AlertDirection.BELOW, 100.0, 98.0, true) == AlertRules.Action.NONE)
        assertTrue(AlertRules.action(AlertDirection.BELOW, 100.0, 101.0, true) == AlertRules.Action.REARM)
        assertTrue(AlertRules.action(AlertDirection.BELOW, 100.0, 99.0, false) == AlertRules.Action.TRIGGER)
    }
}
