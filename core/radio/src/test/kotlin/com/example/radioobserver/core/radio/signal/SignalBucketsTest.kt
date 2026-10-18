package com.example.radioobserver.core.radio.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SignalBucketsTest {

    private val defaults = RssiThresholds() // -50 / -70

    @Test
    fun defaultBoundariesAreInclusive() {
        assertEquals(SignalBucket.IMMEDIATE, SignalBuckets.bucketFor(-50, defaults))
        assertEquals(SignalBucket.IMMEDIATE, SignalBuckets.bucketFor(-49, defaults))
        assertEquals(SignalBucket.IMMEDIATE, SignalBuckets.bucketFor(0, defaults))
        assertEquals(SignalBucket.NEAR, SignalBuckets.bucketFor(-51, defaults))
        assertEquals(SignalBucket.NEAR, SignalBuckets.bucketFor(-70, defaults))
        assertEquals(SignalBucket.FAR, SignalBuckets.bucketFor(-71, defaults))
        assertEquals(SignalBucket.FAR, SignalBuckets.bucketFor(-100, defaults))
    }

    @Test
    fun nullRssiIsUnavailable() {
        assertEquals(SignalBucket.UNAVAILABLE, SignalBuckets.bucketFor(null, defaults))
    }

    @Test
    fun implausibleRssiIsUnavailableNotFabricated() {
        // Positive "RSSI" is not a plausible dBm value; neither is below -128.
        assertEquals(SignalBucket.UNAVAILABLE, SignalBuckets.bucketFor(20, defaults))
        assertEquals(SignalBucket.UNAVAILABLE, SignalBuckets.bucketFor(-200, defaults))
        assertFalse(SignalBuckets.isRssiPlausible(20))
        assertFalse(SignalBuckets.isRssiPlausible(-129))
        assertTrue(SignalBuckets.isRssiPlausible(-100))
        assertTrue(SignalBuckets.isRssiPlausible(0))
    }

    @Test
    fun customThresholdsAreRespected() {
        val custom = RssiThresholds(immediateThresholdDbm = -40, nearThresholdDbm = -60)
        // Thresholds are inclusive: -40 hits IMMEDIATE exactly, -60 hits NEAR exactly.
        assertEquals(SignalBucket.IMMEDIATE, SignalBuckets.bucketFor(-39, custom))
        assertEquals(SignalBucket.IMMEDIATE, SignalBuckets.bucketFor(-40, custom))
        assertEquals(SignalBucket.NEAR, SignalBuckets.bucketFor(-41, custom))
        assertEquals(SignalBucket.NEAR, SignalBuckets.bucketFor(-59, custom))
        assertEquals(SignalBucket.NEAR, SignalBuckets.bucketFor(-60, custom))
        assertEquals(SignalBucket.FAR, SignalBuckets.bucketFor(-61, custom))
    }

    @Test(expected = IllegalArgumentException::class)
    fun invertedThresholdConfigurationIsRejected() {
        RssiThresholds(immediateThresholdDbm = -80, nearThresholdDbm = -50)
    }
}
