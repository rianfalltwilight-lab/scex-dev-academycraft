package com.mohistmc.academy.skill.ability.aerohand;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class AeroBehaviorMathTest {
    private static final float EPSILON = 0.0001f;

    @Test
    void airCoolingUsesTheUpstreamProficiencyEndpoints() {
        assertEquals(200.0f, AeroBehaviorMath.coolingReduction(0), EPSILON);
        assertEquals(800.0f, AeroBehaviorMath.coolingReduction(1), EPSILON);
    }

    @Test
    void airJetAndSeparatorProgressionStayBounded() {
        assertEquals(2.0, AeroBehaviorMath.airJetSpeed(-1.0f), 0.0001);
        assertEquals(4.0, AeroBehaviorMath.airJetSpeed(2.0f), 0.0001);
        assertEquals(3.0f, AeroBehaviorMath.separatorRadius(0.0f), EPSILON);
        assertEquals(3.0f, AeroBehaviorMath.separatorRadius(1.0f), EPSILON);
        assertEquals(40.0f, AeroBehaviorMath.separatorDamage(0.0f), EPSILON);
        assertEquals(60.0f, AeroBehaviorMath.separatorDamage(1.0f), EPSILON);
    }

    @Test
    void volcanicBallDamageFallsWithLegacyAgeButRetainsMinimum() {
        assertEquals(10.0f, AeroBehaviorMath.volcanicDamage(10.0f, 0, 20), EPSILON);
        assertEquals(7.5f, AeroBehaviorMath.volcanicDamage(10.0f, 10, 20), EPSILON);
        assertEquals(5.0f, AeroBehaviorMath.volcanicDamage(10.0f, 20, 20), EPSILON);
        assertEquals(5.0f, AeroBehaviorMath.volcanicDamage(10.0f, 200, 20), EPSILON);
        assertEquals(0.0f, AeroBehaviorMath.volcanicDamage(10.0f, 1, 0), EPSILON);
    }

    @Test
    void ascendingAirCapsRawFallDamageMoreStronglyAtMastery() {
        assertEquals(10.0f, AeroBehaviorMath.ascendingAirDamageCap(0), EPSILON);
        assertEquals(5.0f, AeroBehaviorMath.ascendingAirDamageCap(1), EPSILON);
    }

    @Test
    void defensiveAndCruisePoliciesHaveAuditableEndpoints() {
        assertEquals(0.10f, AeroBehaviorMath.offenseArmourDamageMultiplier(0), EPSILON);
        assertEquals(0.05f, AeroBehaviorMath.offenseArmourDamageMultiplier(1), EPSILON);
        assertEquals(4, AeroBehaviorMath.cruiseBombOrbCount(0));
        assertEquals(8, AeroBehaviorMath.cruiseBombOrbCount(1));
        assertEquals(4.0f, AeroBehaviorMath.cruiseBombDamage(0), EPSILON);
        assertEquals(8.0f, AeroBehaviorMath.cruiseBombDamage(1), EPSILON);
    }
}
