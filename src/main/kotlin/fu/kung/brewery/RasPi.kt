package fu.kung.brewery

import com.pi4j.io.gpio.*
import mu.KotlinLogging

class RasPi {
    private val logger = KotlinLogging.logger {}

    private var initialized = false
    private lateinit var gpio: GpioController
    private lateinit var pin: GpioPinDigitalOutput

    init {
        try {
            gpio = GpioFactory.getInstance()
            initialized = true
            pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_29, "LED", PinState.LOW)
            pin.setShutdownOptions(true, PinState.LOW)
        } catch (t: Throwable) {
            logger.error("Couldn't initialize Raspberry Pi")
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
        gpio.shutdown()
    }
}