package fu.kung.brewery

import io.kweb.Kweb
import io.kweb.dom.BodyElement
import io.kweb.dom.element.creation.ElementCreator
import io.kweb.dom.element.creation.tags.*
import io.kweb.dom.element.new
import io.kweb.plugins.fomanticUI.fomantic
import io.kweb.plugins.fomanticUI.fomanticUIPlugin
import mu.KotlinLogging
import kotlin.system.exitProcess

// Written specifically for a Raspberry Pi 3 Model B+

fun main() {
    BreweryApp()
}

class BreweryApp {
    private val logger = KotlinLogging.logger {}

    private val control = RasPi()
    private val plugins = listOf(fomanticUIPlugin)
    private val server: Kweb

    private var oneClicked = false

    private var enabledClasses = "ui red button"
    private var disabledClasses = "ui red inverted button"


    init {
        server = Kweb(port = 7777, debug = true, plugins = plugins, buildPage = {
            doc.body.new {
                pageBorderAndTitle("Electric Brewery") {
                    div(fomantic.column).new {
                        div(fomantic.ui.top.attached.segment).new {
                            val button1 = button(fomantic.ui.toggle.button).text("Off")
                            button1.on.click {
                                if (oneClicked) {
                                    oneClicked = false
                                    button1.text("Off")
                                    control.toggle()
                                } else {
                                    oneClicked = true
                                    button1.text("On")
                                    control.toggle()
                                }
                            }
                        }
                        div(fomantic.ui.attached.segment).new {
                            div(fomantic.ui.horizontal.segments).new {
                                div(fomantic.ui.segment).new {
                                    p().text(control.boilTemperature.map { "Boil: $it °F" })
                                    div(fomantic.ui.labeled.input).new {
                                        a(fomantic.ui.label).text("Target")
                                        val boilTarget = input(type = InputType.text)
                                        boilTarget.value = control.boilTarget
                                        val boilEnable =
                                            button(fomantic.ui.red.inverted.button).text(control.boilEnabledText)
                                        control.boilEnabledText.addListener { _, new ->
                                            if (new == "Off") {
                                                boilEnable.setClasses(disabledClasses)
                                            } else {
                                                boilEnable.setClasses(enabledClasses)
                                            }
                                        }
                                        boilEnable.on.click {
                                            control.toggleBoil()
                                            boilEnable.blur()
                                        }
                                    }
                                }
                                div(fomantic.ui.segment).new {
                                    p().text(control.mashTemperature.map { "Mash: $it °F" })
                                }
                                div(fomantic.ui.segment).new {
                                    p().text(control.hltTemperature.map { "HLT: $it °F" })
                                    div(fomantic.ui.labeled.input).new {
                                        a(fomantic.ui.label).text("Target")
                                        val hltTarget = input(type = InputType.text)
                                        hltTarget.value = control.hltTarget
                                        val hltEnable =
                                            button(fomantic.ui.red.inverted.button).text(control.hltEnabledText)
                                        control.hltEnabledText.addListener { _, new ->
                                            if (new == "Off") {
                                                hltEnable.setClasses(disabledClasses)
                                            } else {
                                                hltEnable.setClasses(enabledClasses)
                                            }
                                        }
                                        hltEnable.on.click {
                                            control.toggleHlt()
                                            hltEnable.blur()
                                        }
                                    }
                                }
                            }
                            div(fomantic.ui.horizontal.segments).new {
                                centeredContent() {
                                    h2().text("Wort Pump")
                                    val wortPumpEnable =
                                        button(fomantic.ui.red.inverted.button).text(control.wortPumpEnabledText)
                                    control.wortPumpEnabledText.addListener { _, new ->
                                        if (new == "Off") {
                                            wortPumpEnable.setClasses(disabledClasses)
                                        } else {
                                            wortPumpEnable.setClasses(enabledClasses)
                                        }
                                    }
                                    wortPumpEnable.on.click {
                                        control.toggleWortPump()
                                        wortPumpEnable.blur()
                                    }
                                }
                                centeredContent() {
                                    h2().text("Water Pump") // <i>H<sub>2</sub>O</i>
                                    val waterPumpEnable =
                                        button(fomantic.ui.red.inverted.button).text(control.waterPumpEnabledText)
                                    control.waterPumpEnabledText.addListener { _, new ->
                                        if (new == "Off") {
                                            waterPumpEnable.setClasses(disabledClasses)
                                        } else {
                                            waterPumpEnable.setClasses(enabledClasses)
                                        }
                                    }
                                    waterPumpEnable.on.click {
                                        control.toggleWaterPump()
                                        waterPumpEnable.blur()
                                    }
                                }
                            }
                        }
                        shutdownContent()
                    }
                }
            }
        })
    }

    private fun ElementCreator<DivElement>.shutdownContent(
    ) {
        div(fomantic.ui.horizontal.segments).new { }
        div(fomantic.ui.attached.segment).new {
            div(fomantic.ui.grid).new {
                div(fomantic.ui.one.column.row).new {
                    div(fomantic.ui.right.aligned.column).new {
                        val shutdownButton = button(fomantic.ui.button).text("Shutdown")
                        shutdownButton.on.click {
                            control.shutdown()
                            logger.info("User initiated shutdown. Exiting.")
                            exitProcess(0)
                        }
                    }
                }
            }
        }
    }

    private fun ElementCreator<BodyElement>.pageBorderAndTitle(
        title: String,
        content: ElementCreator<DivElement>.() -> Unit
    ) {
        div(fomantic.ui.main.container).new {
            div(fomantic.column).new {
                div(fomantic.ui.vertical.segment).new {
                    h1(fomantic.ui.dividing.header).text(title)
                    content(this)
                }
            }
        }
    }

    private fun ElementCreator<DivElement>.centeredContent(
        content: ElementCreator<DivElement>.() -> Unit
    ) {
        div(fomantic.ui.segment).new {
            div(fomantic.ui.grid).new {
                div(fomantic.ui.one.column.row).new {
                    div(fomantic.ui.center.aligned.column).new {
                        content(this)
                    }
                }
            }
        }
    }
}
