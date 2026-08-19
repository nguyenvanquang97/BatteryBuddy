package com.batterybuddy.pet

import org.junit.Assert.assertEquals
import org.junit.Test

class PetAIControllerTest {

    @Test
    fun `playground bounds reserve the full pet width`() {
        val controller = PetAIController()
        var reportedX = -1
        controller.onPositionChanged = { x, _ -> reportedX = x }

        controller.updatePlaygroundBounds(minX = 600, maxX = 1000, petWidthPx = 100)
        assertEquals(600, controller.currentX)

        controller.updatePlaygroundBounds(minX = 100, maxX = 500, petWidthPx = 200)

        assertEquals(300, controller.currentX)
        assertEquals(300, reportedX)
    }

    @Test
    fun `stationary pet is moved away from camera cutout`() {
        val controller = PetAIController()
        var reportedX = -1
        controller.onPositionChanged = { x, _ -> reportedX = x }
        controller.updatePlaygroundBounds(minX = 400, maxX = 800, petWidthPx = 100)

        controller.updateNoStopZone(left = 450, right = 550)

        assertEquals(550, controller.currentX)
        assertEquals(550, reportedX)
    }
}
