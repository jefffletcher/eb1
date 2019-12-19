package fu.kung.brewery

import com.pi4j.io.gpio.GpioFactory
import com.pi4j.io.gpio.PinState
import com.pi4j.io.gpio.RaspiPin

class RasPi {
    private val gpio = GpioFactory.getInstance()
    private var pin = gpio.provisionDigitalOutputPin(RaspiPin.GPIO_29, "LED", PinState.LOW)

    init {
        pin.setShutdownOptions(true, PinState.LOW)
    }

    fun toggle() {
        pin.toggle()
    }

    fun shutdown() {
        gpio.shutdown()
    }
}