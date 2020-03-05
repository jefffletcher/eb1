package fu.kung.brewery

import io.kweb.Kweb
import io.kweb.dom.BodyElement
import io.kweb.dom.element.creation.ElementCreator
import io.kweb.dom.element.creation.tags.*
import io.kweb.dom.element.new
import io.kweb.plugins.fomanticUI.fomantic
import io.kweb.plugins.fomanticUI.fomanticUIPlugin
import io.kweb.random
import io.kweb.state.KVar
import io.kweb.state.render.renderEach
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import mu.KotlinLogging
import java.nio.file.Paths
import java.time.Instant
import kotlin.system.exitProcess

// Written specifically for a Raspberry Pi 3 Model B+

fun main() {
    BreweryApp()
}

class BreweryApp {
    private val appId = "electricBrewery"

    private val logger = KotlinLogging.logger {}

    private val control = RasPi()
    private val plugins = listOf(fomanticUIPlugin)
    private val server: Kweb

    private var shutdownEnabledText: KVar<String> = KVar("Enable Shutdown")
    private var enableShutdown = false

    private var enabledClasses = "ui red button"
    private var disabledClasses = "ui red inverted button"

    private val timerStore = TimerStore(Paths.get("data"))

    init {
        server = Kweb(port = 7777, debug = true, plugins = plugins, buildPage = {
            doc.body.new {
                pageBorderAndTitle("Cervecería Eléctrica del Sótano") {
                    div(fomantic.column).new {
                        div(fomantic.ui.attached.segment).new {
                            div(fomantic.ui.horizontal.segments).new {
                                div(fomantic.ui.segment).new {
                                    h3().text(control.boilTemperature.map { "Boil: $it °F" })
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
                                            control.toggleBoilEnabled()
                                            boilEnable.blur()
                                        }
                                    }
                                }
                                div(fomantic.ui.segment).new {
                                    h3().text(control.mashTemperature.map { "Mash: $it °F" })
                                    div(fomantic.ui.labeled.input).new {
                                        a(fomantic.ui.label).text("Delta")
                                        val mashDelta = input(type = InputType.text)
                                        mashDelta.value = control.mashDelta
                                    }
                                }
                                div(fomantic.ui.segment).new {
                                    h3().text(control.hltTemperature.map { "HLT: $it °F" })
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
                                            control.toggleHltEnabled()
                                            hltEnable.blur()
                                        }
                                    }
                                }
                            }
                            div(fomantic.ui.horizontal.segments).new {
                                centeredContent {
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
                                centeredContent {
                                    h2().text("Water Pump")
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
                            div(fomantic.content).new {
                                renderList()
                            }
                        }
                        div(fomantic.ui.bottom.aligned.divided.list).new {
                            div(fomantic.item).new {
                                div(fomantic.right.floated.content).new {
                                    renderShutdownButton()
                                }
                                val shutdownEnable =
                                    button(fomantic.ui.red.inverted.button).text(shutdownEnabledText)
                                shutdownEnable.on.click {
                                    enableShutdown = !enableShutdown
                                    if (enableShutdown) {
                                        shutdownEnable.setClasses(enabledClasses)
                                    } else {
                                        shutdownEnable.setClasses(disabledClasses)
                                    }
                                    shutdownEnable.blur()
                                }
                            }
                        }
                    }
                }
            }
        })
    }

    private fun ElementCreator<*>.renderList() {
        h3().text("Timers")
        div(fomantic.ui.action.input).new {
            val timerDesc = input(InputType.text, placeholder = "Description")
            val timerDuration = input(InputType.number, placeholder = "Duration (minutes)")
            button(fomantic.ui.button).text("Add").apply {
                on.click {
                    handleAddItem(timerDesc, timerDuration)
                }
            }
        }
        div(fomantic.ui.middle.aligned.divided.list).new {
            renderEach(timerStore.timersList(appId)) { timer ->
                div(fomantic.item).new {
                    div(fomantic.right.floated.content).new {
                        renderRemoveButton(timer)
                    }
                    div(fomantic.content).text(
                        "${timer.value.desc} - ${timer.value.duration}"
                    )
                }
            }
        }
    }

    private fun handleAddItem(timerDesc: InputElement, timerDuration: InputElement) {
        GlobalScope.launch {
            val newTimerDescription = timerDesc.getValue().await()
            val newTimerDuration = timerDuration.getValue().await()
            timerDesc.setValue("")
            timerDuration.setValue("")
            val newTimer = TimerStore.Timer(
                uid = generateNewUid(),
                listUid = appId,
                desc = newTimerDescription,
                duration = newTimerDuration.toInt(),
                created = Instant.now()
            )
            timerStore.timers[newTimer.uid] = newTimer
        }
    }

    private fun generateNewUid() = random.nextInt(100_000_000).toString(16)

    private fun ElementCreator<DivElement>.renderRemoveButton(timer: KVar<TimerStore.Timer>) {
        val button = button(fomantic.mini.ui.icon.button)
        button.new {
            i(fomantic.trash.icon)
        }
        button.on.click {
            timerStore.timers.remove(timer.value.uid)
        }
    }

    private fun ElementCreator<DivElement>.renderShutdownButton(
    ) {
        val button = button(fomantic.ui.icon.button)
        button.new {
            i(fomantic.warning.icon)
        }
        button.on.click {
            if (enableShutdown) {
                control.shutdown()
                logger.info("User initiated shutdown. Exiting.")
                exitProcess(0)
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
                    h1(fomantic.ui.center.aligned.header).text(title)
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
