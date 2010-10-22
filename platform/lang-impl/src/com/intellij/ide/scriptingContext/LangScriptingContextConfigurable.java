/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ide.scriptingContext;

import com.intellij.ide.scriptingContext.ui.ScriptingLibrariesPanel;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.scripting.ScriptingLibraryManager;
import org.jetbrains.annotations.Nls;

import javax.swing.*;

/**
 * @author Rustam Vishnyakov
 */
public class LangScriptingContextConfigurable implements Configurable {
  private ScriptingLibrariesPanel myPanel;
  private ScriptingLibraryManager myLibManager;
  private LangScriptingContextProvider myProvider;

  public LangScriptingContextConfigurable(Project project, LangScriptingContextProvider provider) {
    myLibManager = new ScriptingLibraryManager(project, provider.getLibraryType());
    myPanel = new ScriptingLibrariesPanel(provider, project, myLibManager);
    myProvider = provider;
  }

  @Nls
  @Override
  public String getDisplayName() {
    return myProvider.getLanguage().getDisplayName();
  }

  @Override
  public Icon getIcon() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public String getHelpTopic() {
    return null;  //To change body of implemented methods use File | Settings | File Templates.
  }

  @Override
  public JComponent createComponent() {
    return myPanel.getPanel();
  }

  @Override
  public boolean isModified() {
    return myPanel.isModified();
  }

  @Override
  public void apply() throws ConfigurationException {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        myLibManager.commitChanges();
        myPanel.resetTable();
      }
    });
  }

  @Override
  public void reset() {
    myLibManager.dropChanges();
    myPanel.resetTable();
  }

  @Override
  public void disposeUIResources() {

  }
}
