// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.learn.lesson.kimpl

import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.JBPopupListener
import com.intellij.openapi.ui.popup.LightweightWindowEvent
import com.intellij.ui.JBColor
import com.intellij.ui.UIBundle
import com.intellij.ui.awt.RelativePoint
import com.intellij.util.Alarm
import training.commands.kotlin.TaskContext
import training.commands.kotlin.TaskRuntimeContext
import training.commands.kotlin.TaskTestContext
import training.learn.ActionsRecorder
import training.learn.LearnBundle
import training.learn.lesson.LessonManager
import training.ui.LearningUiHighlightingManager
import training.ui.LessonMessagePane
import training.ui.MessageFactory
import java.awt.Color
import java.awt.Component
import java.awt.Point
import java.awt.event.ActionEvent
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import javax.swing.*

data class TaskProperties(var hasDetection: Boolean = false, var messagesNumber: Int = 0)

internal object LessonExecutorUtil {
  /** This task is a real task with some event required and corresponding text. Used for progress indication. */
  fun taskProperties(taskContent: TaskContext.() -> Unit, project: Project): TaskProperties {
    val fakeTaskContext = ExtractTaskPropertiesContext(project)
    taskContent(fakeTaskContext)
    return TaskProperties(fakeTaskContext.hasDetection, fakeTaskContext.textCount)
  }

  fun textMessages(taskContent: TaskContext.() -> Unit, project: Project): List<String> {
    val fakeTaskContext = ExtractTextTaskContext(project)
    taskContent(fakeTaskContext)
    return fakeTaskContext.messages
  }

  fun showBalloonMessage(text: String, ui: JComponent, balloonConfig: LearningBalloonConfig, actionsRecorder: ActionsRecorder, project: Project) {
    val messages = MessageFactory.convert(text)
    val messagesPane = LessonMessagePane()
    messagesPane.addMessage(messages)
    messagesPane.toolTipText = LearnBundle.message("learn.stop.hint")
    val balloonPanel = JPanel()
    balloonPanel.layout = BoxLayout(balloonPanel, BoxLayout.Y_AXIS)
    balloonPanel.preferredSize = balloonConfig.dimension
    balloonPanel.add(messagesPane)
    val gotItCallBack = balloonConfig.gotItCallBack
    if (gotItCallBack != null) {
      val stopButton = JButton()
      balloonPanel.add(stopButton)
      stopButton.action = object : AbstractAction(UIBundle.message("got.it")) {
        override fun actionPerformed(e: ActionEvent?) {
          gotItCallBack()
        }
      }
    }

    val balloon = JBPopupFactory.getInstance().createBalloonBuilder(balloonPanel)
      .setCloseButtonEnabled(false)
      .setAnimationCycle(0)
      .setHideOnClickOutside(false)
      .setBlockClicksThroughBalloon(true)
      .setBorderColor(JBColor(Color.BLACK, Color.WHITE))
      .setHideOnCloseClick(false)
      .setDisposable(actionsRecorder)
      .setCloseButtonEnabled(true)
      .createBalloon()


    balloon.addListener(object : JBPopupListener {
      override fun onClosed(event: LightweightWindowEvent) {
        val checkStopLesson = {
          invokeLater {
            if (actionsRecorder.disposed) return@invokeLater
            val yesNo = Messages.showYesNoDialog(project, LearnBundle.message("learn.stop.lesson.question"), LearnBundle.message("learn.stop.lesson"), null)
            if (yesNo == Messages.YES) {
              LessonManager.instance.stopLesson()
            }
            else {
              if (actionsRecorder.disposed) return@invokeLater
              showBalloonMessage(text, ui, balloonConfig, actionsRecorder, project)
            }
          }
        }
        Alarm().addRequest(checkStopLesson, 500) // it is a hacky a little bit
      }
    })
    balloon.show(getPosition(ui, balloonConfig.side), balloonConfig.side)
  }

  private fun getPosition(component: JComponent, side: Balloon.Position): RelativePoint {
    val visibleRect = LearningUiHighlightingManager.getRectangle(component) ?: component.visibleRect

    val xShift = when (side) {
      Balloon.Position.atLeft -> 0
      Balloon.Position.atRight -> visibleRect.width
      Balloon.Position.above, Balloon.Position.below -> visibleRect.width / 2
      else -> error("Unexpected balloon position")
    }

    val yShift = when (side) {
      Balloon.Position.above -> 0
      Balloon.Position.below -> visibleRect.height
      Balloon.Position.atLeft, Balloon.Position.atRight -> visibleRect.height / 2
      else -> error("Unexpected balloon position")
    }
    val point = Point(visibleRect.x + xShift, visibleRect.y + yShift)
    return RelativePoint(component, point)
  }
}



private class ExtractTaskPropertiesContext(override val project: Project) : TaskContext() {
  var textCount = 0
  var hasDetection = false

  override fun text(text: String, useBalloon: LearningBalloonConfig?) {
    textCount++
  }

  override fun trigger(actionId: String) {
    hasDetection = true
  }

  override fun trigger(checkId: (String) -> Boolean) {
    hasDetection = true
  }
  override fun <T> trigger(actionId: String, calculateState: TaskRuntimeContext.() -> T, checkState: TaskRuntimeContext.(T, T) -> Boolean) {
    hasDetection = true
  }

  override fun triggerStart(actionId: String, checkState: TaskRuntimeContext.() -> Boolean) {
    hasDetection = true
  }

  override fun triggers(vararg actionIds: String) {
    hasDetection = true
  }

  override fun stateCheck(checkState: TaskRuntimeContext.() -> Boolean): CompletableFuture<Boolean> {
    hasDetection = true
    return CompletableFuture<Boolean>()
  }

  override fun <T : Any> stateRequired(requiredState: TaskRuntimeContext.() -> T?): Future<T> {
    hasDetection = true
    return CompletableFuture()
  }

  override fun addFutureStep(p: DoneStepContext.() -> Unit) {
    hasDetection = true
  }

  override fun addStep(step: CompletableFuture<Boolean>) {
    hasDetection = true
  }

  @Suppress("OverridingDeprecatedMember")
  override fun triggerByUiComponentAndHighlight(findAndHighlight: TaskRuntimeContext.() -> () -> Component)  {
    hasDetection = true
  }

  override fun test(action: TaskTestContext.() -> Unit) = Unit // do nothing

  override fun action(actionId: String): String = "" //Doesn't matter what to return

  override fun code(sourceSample: String): String = "" //Doesn't matter what to return

  override fun strong(text: String): String = "" //Doesn't matter what to return

  override fun icon(icon: Icon): String = "" //Doesn't matter what to return
}

private class ExtractTextTaskContext(override val project: Project) : TaskContext() {
  val messages = ArrayList<String>()
  override fun text(text: String, useBalloon: LearningBalloonConfig?) {
    messages.add(text)
  }
}
