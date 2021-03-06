package fu.kung.brewery

import com.pi4j.io.gpio.*
import io.kweb.state.KVar
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import mu.KotlinLogging

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

    val hltTemperature: KVar<String> = KVar("")
    val mashTemperature: KVar<String> = KVar("")
    val boilTemperature: KVar<String> = KVar("")
    val hltTarget: KVar<String> = KVar("")
    val mashDelta: KVar<String> = KVar("1.0")
    val boilTarget: KVar<String> = KVar("")
    private var hltEnabled = false
    private var boilEnabled = false
    val hltEnabledText: KVar<String> = KVar(off)
    val boilEnabledText: KVar<String> = KVar(off)

    val waterPumpEnabledText: KVar<String> = KVar(off)
    val wortPumpEnabledText: KVar<String> = KVar(off)

    private val dutyTimeMillis = 2000L
    private val targetDutyPercent = .65
    private val rampUpTargetPercent = .98

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
                hltTemperature.value = tempToDegF(hltMax31865.temperature())
                mashTemperature.value = tempToDegF(mashMax31865.temperature())
                boilTemperature.value = tempToDegF(boilMax31865.temperature())
                delay(1000L)
            }
        }

        // cycle HLT element
        GlobalScope.launch {
            while (true) {
                var actualDutyPercent = 0.0
                var targetTemp: Double
                try {
                    targetTemp = hltTarget.value.toDouble() + mashDelta.value.toDouble()
                } catch (e: Exception) {
                    delay(dutyTimeMillis)
                    continue
                }
                if (hltEnabled) {
                    actualDutyPercent =
                        getDutyPercent(hltTemperature.value.toDouble(), targetTemp)

                    if (actualDutyPercent > 0) {
                        // Fire element
                        hltPin.state = PinState.HIGH
                        delay((dutyTimeMillis * actualDutyPercent).toLong())
                    }
                }

                if (actualDutyPercent < 1.0) {
                    if (hltPin.isHigh) {
                        // Shutdown element
                        hltPin.state = PinState.LOW
                    }
                    delay((dutyTimeMillis * (1.0 - actualDutyPercent)).toLong())
                }
            }
        }

        // cycle Boil element
        GlobalScope.launch {
            while (true) {
                var actualDutyPercent = 0.0
                var targetTemp: Double
                try {
                    targetTemp = boilTarget.value.toDouble()
                } catch (e: Exception) {
                    if (boilTarget.value.isNotEmpty()) {
                        logger.info("Invalid boil target temperature '$boilTarget.value'.")
                    }
                    delay(dutyTimeMillis)
                    continue
                }
                if (boilEnabled) {
                    actualDutyPercent =
                        getDutyPercent(boilTemperature.value.toDouble(), targetTemp)

                    if (actualDutyPercent > 0) {
                        // Fire element
                        boilPin.state = PinState.HIGH
                        delay((dutyTimeMillis * actualDutyPercent).toLong())
                    }
                }

                if (actualDutyPercent < 1.0) {
                    if (boilPin.isHigh) {
                        // Shutdown element
                        boilPin.state = PinState.LOW
                    }
                    delay((dutyTimeMillis * (1.0 - actualDutyPercent)).toLong())
                }
            }
        }
    }

    private fun getDutyPercent(currentTemp: Double, targetTemp: Double): Double {
        val tempPercent = currentTemp / targetTemp
        if (tempPercent < rampUpTargetPercent) {
            // ramping up, full power
            return 1.0
        } else if (currentTemp < targetTemp) {
            // done ramping up, maintain temp
            return targetDutyPercent
        }

        // over target
        return 0.0
    }

    fun toggleHltEnabled() {
        shutdownBoil()
        val newHltValue = !hltEnabled
        hltEnabled = newHltValue
        updateText(hltEnabled, hltEnabledText)
    }

    fun toggleBoilEnabled() {
        shutdownHlt()
        val newBoilValue = !boilEnabled
        boilEnabled = newBoilValue
        if (boilEnabled) {
            boilPin.state = PinState.HIGH
        } else {
            boilPin.state = PinState.LOW
        }
        updateText(boilEnabled, boilEnabledText)
    }

    private fun shutdownHlt() {
        hltEnabled = false
        hltPin.state = PinState.LOW
        updateText(hltEnabled, hltEnabledText)
    }

    private fun shutdownBoil() {
        boilEnabled = false
        boilPin.state = PinState.LOW
        updateText(boilEnabled, boilEnabledText)
    }

    fun toggleWaterPump() {
        waterPumpPin.toggle()
        updateText(waterPumpPin.isHigh, waterPumpEnabledText)
    }

    fun toggleWortPump() {
        wortPumpPin.toggle()
        updateText(wortPumpPin.isHigh, wortPumpEnabledText)
    }

    private fun updateText(enabled: Boolean, textVar: KVar<String>) {
        if (enabled) {
            textVar.value = on
        } else {
            textVar.value = off
        }
    }

    fun shutdown() {
        if (!initialized) {
            return
        }
        logger.info("Shutting down RasPi controller.")
        hltPin.state = PinState.LOW
        boilPin.state = PinState.LOW
        wortPumpPin.state = PinState.LOW
        waterPumpPin.state = PinState.LOW
        hltMax31865.reset()
        mashMax31865.reset()
        boilMax31865.reset()
        gpio.shutdown()
    }

    private fun tempToDegF(temp: Double): String {
        return "%.1f".format(temp * 9.0 / 5.0 + 32.0)
    }
}