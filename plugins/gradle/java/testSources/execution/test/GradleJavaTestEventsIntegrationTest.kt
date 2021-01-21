// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.execution.test

import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskId
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskNotificationListenerAdapter
import com.intellij.openapi.externalSystem.model.task.ExternalSystemTaskType
import com.intellij.openapi.util.Pair
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.plugins.gradle.GradleManager
import org.jetbrains.plugins.gradle.importing.GradleBuildScriptBuilderEx
import org.jetbrains.plugins.gradle.importing.GradleImportingTestCase
import org.jetbrains.plugins.gradle.importing.withMavenCentral
import org.jetbrains.plugins.gradle.service.task.GradleTaskManager
import org.jetbrains.plugins.gradle.settings.GradleExecutionSettings
import org.jetbrains.plugins.gradle.util.GradleConstants
import org.junit.Test

open class GradleJavaTestEventsIntegrationTest: GradleImportingTestCase() {

  @Test
  fun test() {
    val gradleSupportsJunitPlatform = isGradleNewerOrSameAs("4.6")
    createProjectSubFile("src/main/java/my/pack/AClass.java",
                         "package my.pack;\n" +
                         "public class AClass {\n" +
                         "  public int method() { return 42; }" +
                         "}")

    createProjectSubFile("src/test/java/my/pack/AClassTest.java",
                         "package my.pack;\n" +
                         "import org.junit.Test;\n" +
                         "import static org.junit.Assert.*;\n" +
                         "public class AClassTest {\n" +
                         "  @Test\n" +
                         "  public void testSuccess() {\n" +
                         "    assertEquals(42, new AClass().method());\n" +
                         "  }\n" +
                         "  @Test\n" +
                         "  public void testFail() {\n" +
                         "    fail(\"failure message\");\n" +
                         "  }\n" +
                         "}")

    createProjectSubFile("src/test/java/my/otherpack/AClassTest.java",
                         "package my.otherpack;\n" +
                         "import my.pack.AClass;\n" +
                         "import org.junit.Test;\n" +
                         "import static org.junit.Assert.*;\n" +
                         "public class AClassTest {\n" +
                         "  @Test\n" +
                         "  public void testSuccess() {\n" +
                         "    assertEquals(42, new AClass().method());\n" +
                         "  }\n" +
                         "}")

    createProjectSubFile("src/junit5test/java/my/otherpack/ADisplayNamedTest.java",
                         """
                           package my.otherpack;
                           import org.junit.jupiter.api.DisplayNameGeneration;
                           import org.junit.jupiter.api.DisplayNameGenerator;
                           import org.junit.jupiter.api.Test;
                           import static org.junit.jupiter.api.Assertions.assertTrue;
                           @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
                           public class ADisplayNamedTest {
                               @Test
                               void successful_test() {
                                   assertTrue(true);
                               }
                           }
                         """.trimIndent())

    importProject(
      GradleBuildScriptBuilderEx()
        .withMavenCentral()
        .applyPlugin("'java'")
        .addPostfix("dependencies {",
                    "  testCompile 'junit:junit:4.12'",
                    "}",
                    if (gradleSupportsJunitPlatform) {
                    """
                      sourceSets {
                        junit5test
                      }
                      
                      dependencies {
                        junit5testImplementation 'org.junit.jupiter:junit-jupiter-api:5.7.0'
                        junit5testRuntimeOnly 'org.junit.jupiter:junit-jupiter-engine:5.7.0'
                      }
                      
                      task junit5test(type: Test) {
                        useJUnitPlatform()
                         testClassesDirs = sourceSets.junit5test.output.classesDirs
                         classpath = sourceSets.junit5test.runtimeClasspath
                      }
                    """.trimIndent() } else { "" },
                    "test { filter { includeTestsMatching 'my.pack.*' } }")
        .generate()
    )

    RunAll(
      ThrowableRunnable { `call test task produces test events`() },
      ThrowableRunnable { `call build task does not produce test events`() },
      ThrowableRunnable { `call task for specific test overrides existing filters`() },
      ThrowableRunnable { if (gradleSupportsJunitPlatform) `test events use display name`() }
    ).run()
  }

  private fun `call test task produces test events`() {
    val testListener = LoggingESNotificationListener()

    val settings = createSettings { putUserData(GradleConstants.RUN_TASK_AS_TEST, true) }

    assertThatThrownBy {
      GradleTaskManager().executeTasks(createId(),
                                       listOf(":test"),
                                       projectPath,
                                       settings,
                                       null,
                                       testListener)
    }
      .hasMessageContaining("There were failing tests")

    assertThat(testListener.eventLog)
      .contains(
        "<descriptor name='testFail' className='my.pack.AClassTest' />",
        "<descriptor name='testSuccess' className='my.pack.AClassTest' />")
      .doesNotContain(
        "<descriptor name='testSuccess' className='my.otherpack.AClassTest' />")
  }

  private fun `call build task does not produce test events`() {
    val testListener = LoggingESNotificationListener()
    val settings = createSettings()

    assertThatThrownBy {
      GradleTaskManager().executeTasks(createId(),
                                       listOf("clean", "build"),
                                       projectPath,
                                       settings,
                                       null,
                                       testListener)
    }
      .hasMessageContaining("There were failing tests")
    assertThat(testListener.eventLog).noneMatch { it.contains("<ijLogEol/>") }
  }

  private fun `call task for specific test overrides existing filters`() {
    val testListener = LoggingESNotificationListener()

    val settings = createSettings {
      putUserData(GradleConstants.RUN_TASK_AS_TEST, true)
      withArguments("--tests","my.otherpack.*")
    }

      GradleTaskManager().executeTasks(createId(),
                                       listOf(":cleanTest", ":test"),
                                       projectPath,
                                       settings,
                                       null,
                                       testListener)

    assertThat(testListener.eventLog)
      .contains("<descriptor name='testSuccess' className='my.otherpack.AClassTest' />")
      .doesNotContain("<descriptor name='testFail' className='my.pack.AClassTest' />",
                      "<descriptor name='testSuccess' className='my.pack.AClassTest' />")
  }

  private fun `test events use display name`() {
    val testListener = LoggingESNotificationListener()

    val settings = createSettings { putUserData(GradleConstants.RUN_TASK_AS_TEST, true) }

    GradleTaskManager().executeTasks(createId(),
                                     listOf(":junit5test"),
                                     projectPath,
                                     settings,
                                     null,
                                     testListener)

    assertThat(testListener.eventLog)
      .contains("<descriptor name='successful test' className='my.otherpack.ADisplayNamedTest' />")
  }

  private fun createSettings(config: GradleExecutionSettings.() -> Unit = {}) = GradleManager()
    .executionSettingsProvider
    .`fun`(Pair.create(myProject, projectPath))
    .apply { config() }

  private fun createId() = ExternalSystemTaskId.create(GradleConstants.SYSTEM_ID, ExternalSystemTaskType.EXECUTE_TASK, myProject)

  class LoggingESNotificationListener : ExternalSystemTaskNotificationListenerAdapter() {
    val eventLog = mutableListOf<String>()

    override fun onTaskOutput(id: ExternalSystemTaskId, text: String, stdOut: Boolean) {
      addEventLogLines(text, eventLog)
    }

    private fun addEventLogLines(text: String, eventLog: MutableList<String>) {
      text.split("<ijLogEol/>").mapTo(eventLog) { it.trim('\r', '\n', ' ') }
    }
  }

}
