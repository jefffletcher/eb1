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


    init {
        server = Kweb(port = 7777, debug = true, plugins = plugins, buildPage = {
            doc.body.new {
                pageBorderAndTitle("Electric Brewery") {
                    div(fomantic.content).new {
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
                        p().text(control.hltTemperature.map { "HLT: $it °F" })
                        p().text(control.mashTemperature.map { "Mash: $it °F" })
                        div(fomantic.ui.bottom.attached).new {
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
        })
    }

    private fun ElementCreator<BodyElement>.pageBorderAndTitle(
        title: String,
        content: ElementCreator<DivElement>.() -> Unit
    ) {
        div(fomantic.ui.main.container).new {
            div(fomantic.column).new {
                //                div(fomantic.ui.vertical.segment).new {
//                    div(fomantic.ui.message).new {
//                        p().innerHTML(
//                            """
//                                You're really doing it!
//                            """
//                                .trimIndent()
//                        )
//                    }
//                }

                div(fomantic.ui.vertical.segment).new {
                    h1(fomantic.ui.dividing.header).text(title)
                    content(this)
                }
            }
        }
    }
}
