/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.server;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.ThrowableComputable;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.PlatformTestUtil;
import org.jetbrains.idea.maven.MavenTestCase;
import org.jetbrains.idea.maven.project.MavenWorkspaceSettingsComponent;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class MavenServerManagerTest extends MavenTestCase {
  public void testInitializingDoesntTakeReadAction() throws Exception {
    //make sure all components are initialized to prevent deadlocks
    MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath());

    Future result = ApplicationManager.getApplication().runWriteAction(
      (ThrowableComputable<Future, Exception>)() -> ApplicationManager.getApplication().executeOnPooledThread(() -> {
        MavenServerManager.getInstance().shutdown(true);
        MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath());
      }));


    long start = System.currentTimeMillis();
    long end = TimeUnit.SECONDS.toMillis(10) + start;
    boolean ok = false;
    while (System.currentTimeMillis() < end) {
      EdtTestUtil.runInEdtAndWait(() -> {
        PlatformTestUtil.dispatchAllEventsInIdeEventQueue();
      });
      try {
        result.get(0, TimeUnit.MILLISECONDS);
        ok = true;
        break;
      }
      catch (InterruptedException | java.util.concurrent.ExecutionException e) {
        throw new RuntimeException(e);
      }
      catch (TimeoutException ignore) {
      }
    }
    if (!ok) {
      printThreadDump();
      fail();
    }
    result.cancel(true);
  }
  public void testConnectorRestartAfterVMChanged() {
    MavenWorkspaceSettingsComponent settingsComponent = MavenWorkspaceSettingsComponent.getInstance(myProject);
    String vmOptions = settingsComponent.getSettings().importingSettings.getVmOptionsForImporter();
    try {
      MavenServerConnector connector = MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath());
      settingsComponent.getSettings().importingSettings.setVmOptionsForImporter(vmOptions + " -DtestVm=test");
      assertNotSame(connector, MavenServerManager.getInstance().getConnector(myProject, myProjectRoot.getPath()));
    }
    finally {
      settingsComponent.getSettings().importingSettings.setVmOptionsForImporter(vmOptions);
    }
  }
}
