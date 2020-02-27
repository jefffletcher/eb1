package fu.kung.brewery;

import com.pi4j.wiringpi.Gpio;

/*
# The MIT License (MIT)
#
# Copyright (c) 2019 Uros Krkic for Adafruit Industries
#
# Permission is hereby granted, free of charge, to any person obtaining a copy
# of this software and associated documentation files (the "Software"), to deal
# in the Software without restriction, including without limitation the rights
# to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
# copies of the Software, and to permit persons to whom the Software is
# furnished to do so, subject to the following conditions:
#
# The above copyright notice and this permission notice shall be included in
# all copies or substantial portions of the Software.
#
# THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
# IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
# FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
# AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
# LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
# OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
# THE SOFTWARE.
 */

/**
 * This is a library for the <b>Adafruit MAX31865</b> amplifier for PT100/P1000 RTD Sensor.
 * <br><br>
 * Designed specifically to work with the Adafruit RTD Sensor:
 * <br>
 * <a href="https://www.adafruit.com/product/3328">https://www.adafruit.com/product/3328</a>
 * <br>
 * <a href="https://learn.adafruit.com/adafruit-max31865-rtd-pt100-amplifier/overview">https://learn.adafruit.com/adafruit-max31865-rtd-pt100-amplifier/overview</a>
 * <br><br>
 * This sensor uses SPI to communicate, 4/3/2 pins are required to interface.
 * <br><br>
 * The implementation supposes to use <b>WiringPi</b> pin numbering.
 * <br><br>
 * For <b>Raspberry Pi 3 B+</b> model, it is:
 * <br>
 * <table border="1">
 * <tr>
 * <th>Type</th>
 * <th>Physical</th>
 * <th>BCM</th>
 * <th>WiringPi (Pi4J)</th>
 * </tr>
 * <tr>
 * <td>MOSI</td>
 * <td>19</td>
 * <td>10</td>
 * <td>12</td>
 * </tr>
 * <tr>
 * <td>MISO</td>
 * <td>21</td>
 * <td>9</td>
 * <td>13</td>
 * </tr>
 * <tr>
 * <td>SCLK</td>
 * <td>23</td>
 * <td>11</td>
 * <td>14</td>
 * </tr>
 * </table>
 *
 * <br>
 * <b>Note: </b>If you want to connect multiple MAX31865's to one microcontroller, have them share the SDI, SDO and SCK pins. Then assign each one a unique CS pin.
 * <i>For instance, CS pin can be 10 (WiringPi) (24 physical pin).</i>
 * <br><br>
 *
 * <b>Note:</b> Once done using the sensor, call {@link AdafruitMax31865#reset()} to reset the system to default state.
 * <br><br>
 *
 * <b>Dependencies: </b> <a href="https://pi4j.com/1.2/index.html">Pi4J (WiringPi)</a>
 * <ul>
 * <li>Pi4J Core</li>
 * <li>Pi4J GPIO Extension</li>
 * <li>Pi4J Device</li>
 * </ul>
 * <br>
 *
 * <b>References:</b>
 * <ul>
 * <li><a href="https://pi4j.com/1.2/index.html">Pi4J</a></li>
 * <li><a href="http://wiringpi.com/">WiringPi</a></li>
 * </ul>
 * <br>
 *
 * <b>Other Forks:</b>
 * <ul>
 * <li><a href="https://github.com/adafruit/Adafruit_CircuitPython_MAX31865">Adafruit CircuitPython MAX31865 (Python)</a></li>
 * <li><a href="https://github.com/adafruit/Adafruit_MAX31865">Adafruit MAX31865 (C++)</a></li>
 * </ul>
 *
 * <br>
 * <b>License: </b>The MIT License (MIT)
 * <br><br>
 *
 * @author Uros Krkic
 */
public final class AdafruitMax31865 {

    private static final int MAX31865_CONFIG_REG			= 0x00;
    private static final int MAX31865_CONFIG_3WIRE			= 0x10;
    private static final int MAX31865_CONFIG_FAULTSTAT		= 0x02;
    private static final int MAX31865_CONFIG_BIAS			= 0x80;
    private static final int MAX31865_CONFIG_1SHOT			= 0x20;
    private static final int MAX31865_RTDMSB_REG			= 0x01;
    private static final int MAX31856_CONFIG_MODEAUTO		= 0x40;

    private static final int MAX31856_FAULTSTAT_REG			= 0x07;
    private static final int MAX31865_FAULT_HIGHTHRESH		= 0x80;
    private static final int MAX31865_FAULT_LOWTHRESH		= 0x40;
    private static final int MAX31865_FAULT_REFINLOW		= 0x20;
    private static final int MAX31865_FAULT_REFINHIGH		= 0x10;
    private static final int MAX31865_FAULT_RTDINLOW		= 0x08;
    private static final int MAX31865_FAULT_OVUV			= 0x04;

    private static final double RTD_A = 3.9083e-3;
    private static final double RTD_B = -5.775e-7;

    private int cs;		// CS
    private int mosi;	// MOSI (SDI)
    private int miso;	// MISO (SDO)
    private int sclk;	// SCLK (SCK)

    private Wires wires;
    private double rtdNominal;
    private double refResistor;

    public enum Wires {
        TWO(2),
        THREE(3),
        FOUR(4);

        private int value;

        Wires(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }
    }

    /**
     * Setup RPi pins, nominal values and initialize the module.
     * <br><br>
     * <b>IMPORTANT: Use WiringPi (Pi4J) pin numbering.</b>
     * <br><br>
     * Check class level documentation for more details.
     *
     * @param cs Chip Select pin. Drop it to low to start an SPI transaction. It's an input to the chip.
     * @param mosi MOSI (Master Out Slave In) / SDI (Serial Data In) pin, for data sent from your processor to MAX31865.
     * @param miso MISO (Master In Slave Out) / SDO (Serial Data Out) pin, for data sent from MAX31865 to your processor.
     * @param sclk SCLK (SCK) (SPI Clock) pin. It's an input to the chip.
     * @param wires Number of wires between MAX31865 and PT100/PT1000 sensor
     * @param rtdNominal The 'nominal' 0-degrees-C resistance of the sensor. Use 100.0 for PT100 or 1000.0 for PT1000.
     * @param refResistor The value of the Rref resistor. Use 430.0 for PT100 or 4300.0 for PT1000.
     */
    public AdafruitMax31865(int cs, int mosi, int miso, int sclk, Wires wires, double rtdNominal, double refResistor) {
        if (Gpio.wiringPiSetup() != 0) {
            new RuntimeException("Initialization of the GPIO (WiringPi) has failed");
        }

        this.cs = cs;
        this.mosi = mosi;
        this.miso = miso;
        this.sclk = sclk;

        this.wires = wires;
        this.rtdNominal = rtdNominal;
        this.refResistor = refResistor;

        init();
    }

    /**
     * Initialize pins mode and values.
     */
    private void init() {
        Gpio.pinMode(cs, Gpio.OUTPUT);
        Gpio.digitalWrite(cs, Gpio.HIGH);

        Gpio.pinMode(sclk, Gpio.OUTPUT);
        Gpio.digitalWrite(sclk, Gpio.LOW);

        Gpio.pinMode(mosi, Gpio.OUTPUT);
        Gpio.pinMode(miso, Gpio.INPUT);

        setWires();
        setBias(false);
        setAutoconvert(false);
        clearFault();
    }

    /**
     * Reset PIN values and mode to system default, so other client can use the chip (sensor).
     * It's mandatory to call this method when stop using the sensor.
     */
    public void reset() {
        Gpio.pinModeAlt(sclk, Gpio.ALT0);
        Gpio.digitalWrite(sclk, Gpio.LOW);

        Gpio.pinModeAlt(mosi, Gpio.ALT0);
        Gpio.digitalWrite(mosi, Gpio.HIGH);

        Gpio.pinModeAlt(miso, Gpio.ALT0);
        Gpio.digitalWrite(miso, Gpio.LOW);

        Gpio.pinMode(cs, Gpio.OUTPUT);
        Gpio.digitalWrite(cs, Gpio.HIGH);
    }

    /**
     * Reset PIN values and mode to system default, so other client can use the chip (sensor).
     * It's mandatory to call this method when stop using the sensor.
     * <br><br>
     * In compare to {@link AdafruitMax31865#reset()}, this method allows custom reset of CS (Chip Select) pin default mode and state.
     * <br><br>
     * Usually, it is {@code Gpio.OUTPUT} for mode, and {@code Gpio.HIGH} for state.
     * @param csDefaultMode Use {@link Gpio} constants
     * @param csDefaultState Use {@link Gpio} constants
     */
    public void reset(int csDefaultMode, int csDefaultState) {
        Gpio.pinModeAlt(sclk, Gpio.ALT0);
        Gpio.digitalWrite(sclk, Gpio.LOW);

        Gpio.pinModeAlt(mosi, Gpio.ALT0);
        Gpio.digitalWrite(mosi, Gpio.HIGH);

        Gpio.pinModeAlt(miso, Gpio.ALT0);
        Gpio.digitalWrite(miso, Gpio.LOW);

        Gpio.pinMode(cs, csDefaultMode);
        Gpio.digitalWrite(cs, csDefaultState);
    }

    /**
     * Read RTD (Resistance Temperature Detector) value form sensor.
     * <br><br>
     * <i>This method sleeps the thread for 75 milliseconds in total.</i>
     *
     * @see AdafruitMax31865#resistance()
     *
     * @see AdafruitMax31865#temperature()
     *
     * @return raw RTD value
     */
    public int readRTD() {
        clearFault();

        setBias(true);

        sleep(10);

        int config = readRegister8(MAX31865_CONFIG_REG);
        config |= MAX31865_CONFIG_1SHOT;
        writeRegister8(MAX31865_CONFIG_REG, config);

        sleep(65);

        int rtd = readRegister16(MAX31865_RTDMSB_REG);

        rtd >>= 1;	// Remove fault bit.

        return rtd;
    }

    /**
     * Convert RTD value to resistance.
     *
     * @see AdafruitMax31865#temperature()
     *
     * @return resistance value
     */
    public double resistance() {
        double resistance = readRTD();
        resistance /= 32768;
        resistance *= refResistor;
        return resistance;
    }

    /**
     * Read the temperature in Celsius degrees.
     *
     * @return temperature in C
     */
    public double temperature() {
        double Z1, Z2, Z3, Z4, temp;
        Z1 = Z2 = Z3 = Z4 = 0.0;
        temp = 0.0;

        double rawReading = resistance();

        Z1 = -RTD_A;
        Z2 = RTD_A * RTD_A - (4 * RTD_B);
        Z3 = (4 * RTD_B) / rtdNominal;
        Z4 = 2 * RTD_B;

        temp = Z2 + (Z3 * rawReading);
        temp = (Math.sqrt(temp) + Z1) / Z4;

        if (temp >= 0)
            return temp;

        double rpoly = rawReading;

        temp = -242.02;
        temp += 2.2228 * rpoly;
        rpoly *= rawReading; // square
        temp += 2.5859e-3 * rpoly;
        rpoly *= rawReading; // ^3
        temp -= 4.8260e-6 * rpoly;
        rpoly *= rawReading; // ^4
        temp -= 2.8183e-8 * rpoly;
        rpoly *= rawReading; // ^5
        temp += 1.5243e-10 * rpoly;

        return temp;
    }

    /**
     * The fault state of the sensor in raw value.
     * <br>
     * Use {@link AdafruitMax31865#clearFault()} to clear the fault state of the sensor.
     *
     * @see Fault
     *
     * @return raw fault value
     */
    public int readFault() {
        return readRegister8(MAX31856_FAULTSTAT_REG);
    }

    /**
     * The fault state of the sensor in structured representation.
     * <br>
     * Use {@link AdafruitMax31865#clearFault()} to clear the fault state of the sensor.
     *
     * @return {@link Fault} instance with 6 boolean values which indicates faults.
     */
    public Fault getFault() {
        int rawFault = readFault();

        boolean highthresh	= bool(rawFault & MAX31865_FAULT_HIGHTHRESH);
        boolean lowthresh	= bool(rawFault & MAX31865_FAULT_LOWTHRESH);
        boolean refinlow	= bool(rawFault & MAX31865_FAULT_REFINLOW);
        boolean refinhigh	= bool(rawFault & MAX31865_FAULT_REFINHIGH);
        boolean rtdinlow	= bool(rawFault & MAX31865_FAULT_RTDINLOW);
        boolean ovuv		= bool(rawFault & MAX31865_FAULT_OVUV);

        return new Fault(highthresh, lowthresh, refinlow, refinhigh, rtdinlow, ovuv);
    }

    /**
     * Fault structure.
     */
    public static final class Fault {
        private boolean highThresh;
        private boolean lowThresh;
        private boolean refInLow;
        private boolean refInHigh;
        private boolean rtdInLow;
        private boolean ovuv;

        private Fault(boolean highThresh, boolean lowThresh, boolean refInLow, boolean refInHigh, boolean rtdInLow, boolean ovuv) {
            this.highThresh = highThresh;
            this.lowThresh = lowThresh;
            this.refInLow = refInLow;
            this.refInHigh = refInHigh;
            this.rtdInLow = rtdInLow;
            this.ovuv = ovuv;
        }

        public boolean isHighThresh() {
            return highThresh;
        }

        public boolean isLowThresh() {
            return lowThresh;
        }

        public boolean isRefInLow() {
            return refInLow;
        }

        public boolean isRefInHigh() {
            return refInHigh;
        }

        public boolean isRtdInLow() {
            return rtdInLow;
        }

        public boolean isOvuv() {
            return ovuv;
        }

        @Override
        public String toString() {
            String newLine = "\n";
            StringBuilder builder = new StringBuilder();
            builder.append("High Thersh: " + highThresh + newLine);
            builder.append("Low Thersh: " + lowThresh + newLine);
            builder.append("Ref IN Low: " + refInLow + newLine);
            builder.append("Ref IN High: " + refInHigh + newLine);
            builder.append("RTD IN Low: " + rtdInLow + newLine);
            builder.append("OV UV: " + ovuv + newLine);
            return builder.toString();
        }
    }

    /**
     * Clear fault state of the sensor.
     */
    private void clearFault() {
        int config = readRegister8(MAX31865_CONFIG_REG);
        config &= ~0x2C;
        config |= MAX31865_CONFIG_FAULTSTAT;
        writeRegister8(MAX31865_CONFIG_REG, config);
    }


    /* PRIVATE SETUP */

    private void setWires() {
        int config = readRegister8(MAX31865_CONFIG_REG);

        switch (wires) {
            case TWO:
                config &= ~MAX31865_CONFIG_3WIRE;
                break;
            case THREE:
                config |= MAX31865_CONFIG_3WIRE;
                break;
            case FOUR:
                config &= ~MAX31865_CONFIG_3WIRE;
                break;
        }

        writeRegister8(MAX31865_CONFIG_REG, config);
    }

    private void setBias(boolean bias) {
        int config = readRegister8(MAX31865_CONFIG_REG);
        if (bias) {
            config |= MAX31865_CONFIG_BIAS;
        } else {
            config &= ~MAX31865_CONFIG_BIAS;
        }
        writeRegister8(MAX31865_CONFIG_REG, config);
    }

    private void setAutoconvert(boolean autoconvert) {
        int config = readRegister8(MAX31865_CONFIG_REG);
        if (autoconvert) {
            config |= MAX31856_CONFIG_MODEAUTO;
        } else {
            config &= ~MAX31856_CONFIG_MODEAUTO;
        }
        writeRegister8(MAX31865_CONFIG_REG, config);
    }


    /* PRIVATE READ / WRITE */

    private int spixfer(int x) {
        int reply = 0;

        for (int i = 7; i >= 0; i--) {
            reply <<= 1;
            Gpio.digitalWrite(sclk, Gpio.HIGH);
            Gpio.digitalWrite(mosi, x & (1 << i));
            Gpio.digitalWrite(sclk, Gpio.LOW);
            if (Gpio.digitalRead(miso) == 1) {
                reply |= 1;
            }
        }

        return reply;
    }

    private void readRegisterN(int addr, int[] buffer, int n) {
        addr &= 0x7F;	// make sure top bit is not set

        Gpio.digitalWrite(sclk, Gpio.LOW);

        Gpio.digitalWrite(cs, Gpio.LOW);

        spixfer(addr);

        for (int i = 0; i < n; i++) {
            buffer[i] = spixfer(0xFF);
        }

        Gpio.digitalWrite(cs, Gpio.HIGH);
    }

    private int readRegister8(int addr) {
        int[] buffer = new int[] {0};
        readRegisterN(addr, buffer, 1);
        return buffer[0];
    }

    private int readRegister16(int addr) {
        int[] buffer = new int[] {0, 0};
        readRegisterN(addr, buffer, 2);

        int ret = buffer[0];
        ret <<= 8;
        ret |= buffer[1];

        return ret;
    }

    private void writeRegister8(int addr, int data) {
        Gpio.digitalWrite(sclk, Gpio.LOW);

        Gpio.digitalWrite(cs, Gpio.LOW);

        spixfer(addr | 0x80);   // make sure top bit is set
        spixfer(data);

        Gpio.digitalWrite(cs, Gpio.HIGH);
    }


    /* PRIVATE UTILS */

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) { }
    }

    private boolean bool(int value) {
        if (value == 0)
            return false;
        return true;
    }
}

