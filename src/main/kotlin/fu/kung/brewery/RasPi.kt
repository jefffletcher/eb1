package fu.kung.brewery

import com.pi4j.io.gpio.*
import io.kweb.state.KVar
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging
import kotlin.math.roundToInt

class RasPi {
    private val logger = KotlinLogging.logger {}

    private val on = "On "
    private val off = "Off"

    private var initialized = false
    private lateinit var gpio: GpioController
    private lateinit var hltPin: GpioPinDigitalOutput
    private lateinit var boilPin: GpioPinDigitalOutput
    private lateinit var waterPumpPin: GpioPinDigitalOutput
    private lateinit var wortPumpPin: GpioPinDigitalOutput

    private lateinit var hltMax31865: AdafruitMax31865
    private lateinit var mashMax31865: AdafruitMax31865
    private lateinit var boilMax31865: AdafruitMax31865

    var hltTemperature: KVar<String> = KVar("")
    var mashTemperature: KVar<String> = KVar("")
    var boilTemperature: KVar<String> = KVar("")
    var hltTarget: KVar<String> = KVar("")
    var boilTarget: KVar<String> = KVar("")
    private var hltEnabled = false
    private var boilEnabled = false
    var hltEnabledText: KVar<String> = KVar(off)
    var boilEnabledText: KVar<String> = KVar(off)

    private var waterPumpEnabled = false
    var waterPumpEnabledText: KVar<String> = KVar(off)
    private var wortPumpEnabled = false
    var wortPumpEnabledText: KVar<String> = KVar(off)

    // Pins:
    // See https://pinout.xyz/pinout/wiringpi for a mapping of pins.
    // Physical pin 40 is BCM 21 (what's on the breadboard) and WiringPi 29 (GPIO_29)

    init {
        try {
            gpio = GpioFactory.getInstance()
            hltPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_22, "HLT", PinState.LOW)
            hltPin.setShutdownOptions(true, PinState.LOW)
            boilPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_30, "BOIL", PinState.LOW)
            boilPin.setShutdownOptions(true, PinState.LOW)
            waterPumpPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_00, "WaterPump", PinState.LOW)
            waterPumpPin.setShutdownOptions(true, PinState.LOW)
            wortPumpPin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_03, "WortPump", PinState.LOW)
            wortPumpPin.setShutdownOptions(true, PinState.LOW)

            // TODO: Use RaspiPin.<whatever> here instead of ints
            // CS: RaspiPin.GPIO_23, RaspiPin.GPIO_24 RaspiPin.GPIO_25
            // MOSI: RaspiPin.GPIO_12
            // MISO: RaspiPin.GPIO_13
            // SCLK: RaspiPin.GPIO_14
            hltMax31865 = AdafruitMax31865(23, 12, 13, 14, AdafruitMax31865.Wires.THREE, 100.0, 431.0)
            mashMax31865 = AdafruitMax31865(24, 12, 13, 14, AdafruitMax31865.Wires.THREE, 100.0, 431.0)
            boilMax31865 = AdafruitMax31865(25, 12, 13, 14, AdafruitMax31865.Wires.THREE, 100.0, 431.0)

            initialized = true
        } catch (t: Throwable) {
            // This happens on the development machine
            logger.error("Couldn't initialize Raspberry Pi GPIO pins")
        }

        GlobalScope.launch {
            while (true) {
                hltTemperature.value = tempToDegreesF(hltMax31865.temperature()).toString()
                mashTemperature.value = tempToDegreesF(mashMax31865.temperature()).toString()
                boilTemperature.value = tempToDegreesF(boilMax31865.temperature()).toString()
                delay(1000L)
            }
        }
    }

    fun toggleHlt() {
        val newHltValue = !hltEnabled
        if (newHltValue && boilEnabled) {
            shutdownBoil()
        }
        hltEnabled = newHltValue
        if (hltEnabled) {
            hltPin.state = PinState.HIGH
        } else {
            hltPin.state = PinState.LOW
        }
        updateHltText()
    }

    fun toggleBoil() {
        val newBoilValue = !boilEnabled
        if (newBoilValue && hltEnabled) {
            shutdownHlt()
        }
        boilEnabled = newBoilValue
        if (boilEnabled) {
            boilPin.state = PinState.HIGH
        } else {
            boilPin.state = PinState.LOW
        }
        updateText(boilEnabled, boilEnabledText)
//        updateBoilText()
    }

    private fun shutdownHlt() {
        hltEnabled = false
        hltPin.state = PinState.LOW
        updateHltText()
    }

    private fun shutdownBoil() {
        boilEnabled = false
        boilPin.state = PinState.LOW
        updateText(boilEnabled, boilEnabledText)
//        updateBoilText()
    }

    private fun updateText(enabled: Boolean, textVar: KVar<String>) {
        if (enabled) {
            textVar.value = on
        } else {
            textVar.value = off
        }
    }

    private fun updateHltText() {
        if (hltEnabled) {
            hltEnabledText.value = on
        } else {
            hltEnabledText.value = off
        }
    }

    private fun updateBoilText() {
        if (boilEnabled) {
            boilEnabledText.value = on
        } else {
            boilEnabledText.value = off
        }
    }

    fun toggleWaterPump() {
        waterPumpEnabled = !waterPumpEnabled
        waterPumpPin.toggle()
        updateWaterPumpText()
    }

    fun toggleWortPump() {
        wortPumpEnabled = !wortPumpEnabled
        wortPumpPin.toggle()
        updateWortPumpText()
    }

    private fun updateWaterPumpText() {
        if (waterPumpEnabled) {
            waterPumpEnabledText.value = on
        } else {
            waterPumpEnabledText.value = off
        }
    }

    private fun updateWortPumpText() {
        if (wortPumpEnabled) {
            wortPumpEnabledText.value = on
        } else {
            wortPumpEnabledText.value = off
        }
    }

    fun shutdown() {
        if (!initialized) {
            return
        }
        logger.info("Shutting down RasPi controller.")
        gpio.shutdown()
        hltMax31865.reset()
    }

    private fun tempToDegreesF(temp: Double): Int {
        return (temp * 9.0 / 5.0 + 32.0).roundToInt()
    }
}