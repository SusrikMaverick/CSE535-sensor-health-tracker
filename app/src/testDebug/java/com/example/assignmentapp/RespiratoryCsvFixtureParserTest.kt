package com.example.assignmentapp

import com.example.assignmentapp.sensor.AccelerometerSample
import com.example.assignmentapp.sensor.SampleRespiratoryRateProcessor
import com.example.assignmentapp.ui.parseRespiratoryCsvFixtures
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RespiratoryCsvFixtureParserTest {
    @Test
    fun validCsvCreatesAccelerometerSamples() {
        val samples = parseRespiratoryCsvFixtures(
            xCsv = "1.0\n2.0\n",
            yCsv = "3.0,4.0",
            zCsv = "5.0\n6.0"
        )

        assertEquals(
            listOf(
                AccelerometerSample(0L, 1f, 3f, 5f),
                AccelerometerSample(1L, 2f, 4f, 6f)
            ),
            samples
        )
    }

    @Test
    fun suppliedCsvFixturesRunThroughProductionProcessor() = runBlocking {
        val fixtureDirectory = File("src/debug/assets/fixtures")
        val samples = parseRespiratoryCsvFixtures(
            xCsv = fixtureDirectory.resolve("respiratory_x.csv").readText(),
            yCsv = fixtureDirectory.resolve("respiratory_y.csv").readText(),
            zCsv = fixtureDirectory.resolve("respiratory_z.csv").readText()
        )

        assertEquals(3_840, samples.size)
        assertTrue(SampleRespiratoryRateProcessor().calculate(samples) >= 0)
    }

    @Test
    fun nonnumericCsvIsRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parseRespiratoryCsvFixtures(
                xCsv = "1.0\nnot-a-number",
                yCsv = "2.0\n3.0",
                zCsv = "4.0\n5.0"
            )
        }
    }

    @Test
    fun mismatchedCsvLengthsAreRejected() {
        assertThrows(IllegalArgumentException::class.java) {
            parseRespiratoryCsvFixtures(
                xCsv = "1.0\n2.0",
                yCsv = "3.0",
                zCsv = "4.0\n5.0"
            )
        }
    }
}
