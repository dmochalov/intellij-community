// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.util.ui.JBUI
import javax.swing.JPanel

class ToolwindowRightToolbar : ToolwindowToolbar() {
  private val topPane: JPanel

  init {
    border = JBUI.Borders.customLine(JBUI.CurrentTheme.ToolWindow.borderColor(), 1, 1, 0, 0)

    topPane = JPanel(VerticalFlowLayout(0, 0))
    add(topPane)
  }

  override fun removeStripeButton(project: Project, toolWindow: ToolWindow, anchor: ToolWindowAnchor) {
    if (anchor == ToolWindowAnchor.RIGHT) {
      topPane.components.find { (it as SquareStripeButton).button.id == toolWindow.id }?.let { topPane.remove(it) }
    }
  }

  override fun addStripeButton(project: Project, anchor: ToolWindowAnchor, comparator: Comparator<ToolWindow>, toolWindow: ToolWindow) {
    if (anchor == ToolWindowAnchor.RIGHT) {
      rebuildStripe(project, toolwindowPane, topPane, toolWindow, comparator)
    }
  }

  override fun updateButtons() {
    topPane.components.filterIsInstance(SquareStripeButton::class.java).forEach { it.update() }
  }
}