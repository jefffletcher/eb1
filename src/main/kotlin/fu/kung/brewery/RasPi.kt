package fu.kung.brewery

import com.pi4j.io.gpio.*
import io.kweb.state.KVar
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.streams.toList

class RasPi {
    private val logger = KotlinLogging.logger {}

    private val tempNamingMap = mapOf(
        "28-021317ddaaaa" to "HLT",
        "28-0000042b50fd" to "Mash"
    )

    private var initialized = false
    private lateinit var gpio: GpioController
    private lateinit var pin: GpioPinDigitalOutput

    private lateinit var tempSensorMap: Map<String, Path>
    var hltTemperature: KVar<String> = KVar("")
    var mashTemperature: KVar<String> = KVar("")

    // Pins:
    // See https://pinout.xyz/pinout/wiringpi for a mapping of pins.
    // Physical pin 40 is BCM 21 (what's on the breadboard) and WiringPi 29 (GPIO_29)

    init {
        try {
            gpio = GpioFactory.getInstance()
            initialized = true
            pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_29, "LED", PinState.LOW)
            pin.setShutdownOptions(true, PinState.LOW)
        } catch (t: Throwable) {
            logger.error("Couldn't initialize Raspberry Pi GPIO pins")
        }

        val tempSensorPath = Files.list(Paths.get("/sys/bus/w1/devices"))
//        val tempSensorPath = Files.list(Paths.get("/home/jeff/devices"))
        tempSensorPath.use {
            tempSensorMap = tempSensorPath
                .filter { it.fileName.toString().startsWith("28") }
                .toList()
                .associate { path ->
                    path.fileName.toString() to path.resolve("w1_slave")
                }
            logger.info("Found temperature sensors: $tempSensorMap")
        }

        GlobalScope.launch {
            while (true) {
                tempSensorMap.keys.forEach { key ->
                    val temp = fileTempToDegreesF(readTemp(tempSensorMap[key]))

                    if (tempNamingMap[key] == "HLT") {
                        hltTemperature.value = temp.toString()
                    } else if (tempNamingMap[key] == "Mash") {
                        mashTemperature.value = temp.toString()
                    }

                }
                delay(1000L)
            }
        }
    }

    fun toggle() {
        if (!initialized) {
            return
        }
        pin.toggle()
    }

    fun shutdown() {
        if (!initialized) {
            return
        }
        logger.info("Shutting down RasPi controller.")
        gpio.shutdown()
    }

    private fun readTemp(path: Path?): Int {
        val linesInFile = Files.lines(path).toList()
        if (linesInFile.size != 2) {
            return -1
        }

        if (!linesInFile[0].endsWith("YES")) {
            return -2
        }

        val index = linesInFile[1].indexOf("t=")
        val substring = linesInFile[1].substring(index + 2)
        return substring.toInt()
    }

    private fun fileTempToDegreesF(temp: Int): Double {
        return (temp / 1000) * 9.0 / 5.0 + 32.0
    }
}