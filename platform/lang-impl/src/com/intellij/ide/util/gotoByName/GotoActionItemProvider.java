/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.ide.util.gotoByName;

import com.intellij.ide.DataManager;
import com.intellij.ide.actions.ApplyIntentionAction;
import com.intellij.ide.ui.search.ActionFromOptionDescriptorProvider;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.ide.ui.search.SearchableOptionsRegistrar;
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.impl.ActionManagerImpl;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.Function;
import com.intellij.util.Processor;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.util.*;

import static com.intellij.ide.util.gotoByName.GotoActionModel.*;

/**
 * @author peter
 */
public class GotoActionItemProvider implements ChooseByNameItemProvider {
  private final ActionManager myActionManager = ActionManager.getInstance();
  protected final SearchableOptionsRegistrar myIndex = SearchableOptionsRegistrar.getInstance();
  private final GotoActionModel myModel;

  public GotoActionItemProvider(GotoActionModel model) {
    myModel = model;
  }

  @NotNull
  @Override
  public List<String> filterNames(@NotNull ChooseByNameBase base, @NotNull String[] names, @NotNull String pattern) {
    return Collections.emptyList(); // no common prefix insertion in goto action
  }

  @Override
  public boolean filterElements(@NotNull final ChooseByNameBase base,
                                @NotNull final String pattern,
                                boolean everywhere,
                                @NotNull ProgressIndicator cancelled,
                                @NotNull final Processor<Object> consumer) {
    return filterElements(pattern, everywhere, new Processor<MatchedValue>() {
      @Override
      public boolean process(MatchedValue value) {
        return consumer.process(value);
      }
    });
  }

  public boolean filterElements(String pattern, boolean everywhere, Processor<MatchedValue> consumer) {
    DataContext dataContext = DataManager.getInstance().getDataContext(myModel.getContextComponent());

    if (!processIntentions(pattern, consumer, dataContext)) return false;
    if (!processActions(pattern, everywhere, consumer, dataContext)) return false;
    if (!processOptions(pattern, consumer, dataContext)) return false;

    return true;
  }

  private boolean processOptions(String pattern, Processor<MatchedValue> consumer, DataContext dataContext) {
    List<Comparable> options = ContainerUtil.newArrayList();
    final Set<String> words = myIndex.getProcessedWords(pattern);
    Set<OptionDescription> optionDescriptions = null;
    final String actionManagerName = myActionManager.getComponentName();
    for (String word : words) {
      final Set<OptionDescription> descriptions = ((SearchableOptionsRegistrarImpl)myIndex).getAcceptableDescriptions(word);
      if (descriptions != null) {
        for (Iterator<OptionDescription> iterator = descriptions.iterator(); iterator.hasNext(); ) {
          OptionDescription description = iterator.next();
          if (actionManagerName.equals(description.getPath())) {
            iterator.remove();
          }
        }
        if (!descriptions.isEmpty()) {
          if (optionDescriptions == null) {
            optionDescriptions = descriptions;
          }
          else {
            optionDescriptions.retainAll(descriptions);
          }
        }
      } else {
        optionDescriptions = null;
        break;
      }
    }
    if (optionDescriptions != null && !optionDescriptions.isEmpty()) {
      Set<String> currentHits = new HashSet<String>();
      for (Iterator<OptionDescription> iterator = optionDescriptions.iterator(); iterator.hasNext(); ) {
        OptionDescription description = iterator.next();
        final String hit = description.getHit();
        if (hit == null || !currentHits.add(hit.trim())) {
          iterator.remove();
        }
      }
      for (OptionDescription description : optionDescriptions) {
        for (ActionFromOptionDescriptorProvider converter : ActionFromOptionDescriptorProvider.EP.getExtensions()) {
          AnAction action = converter.provide(description);
          if (action != null) options.add(new ActionWrapper(action, null, MatchMode.NAME, dataContext));
          options.add(description);
        }
      }
    }
    return processItems(pattern, options, consumer);
  }

  private boolean processActions(String pattern, boolean everywhere, Processor<MatchedValue> consumer, DataContext dataContext) {
    List<AnAction> actions = ContainerUtil.newArrayList();
    if (everywhere) {
      for (String id : ((ActionManagerImpl)myActionManager).getActionIds()) {
        ContainerUtil.addIfNotNull(actions, myActionManager.getAction(id));
      }
    } else {
      actions.addAll(myModel.myActionsMap.keySet());
    }

    List<ActionWrapper> actionWrappers = ContainerUtil.newArrayList();
    for (AnAction action : actions) {
      MatchMode mode = myModel.actionMatches(pattern, action);
      if (mode != MatchMode.NONE) {
        actionWrappers.add(new ActionWrapper(action, myModel.myActionsMap.get(action), mode, dataContext));
      }
    }
    return processItems(pattern, actionWrappers, consumer);
  }

  private boolean processIntentions(String pattern, Processor<MatchedValue> consumer, DataContext dataContext) {
    List<ActionWrapper> intentions = ContainerUtil.newArrayList();
    for (String intentionText : myModel.myIntentions.keySet()) {
      final ApplyIntentionAction intentionAction = myModel.myIntentions.get(intentionText);
      if (myModel.actionMatches(pattern, intentionAction) != MatchMode.NONE) {
        intentions.add(new ActionWrapper(intentionAction, intentionText, MatchMode.INTENTION, dataContext));
      }
    }
    return processItems(pattern, intentions, consumer);
  }

  private static boolean processItems(final String pattern, List<? extends Comparable> items, Processor<MatchedValue> consumer) {
    List<MatchedValue> matched = ContainerUtil.map(items, new Function<Comparable, MatchedValue>() {
      @Override
      public MatchedValue fun(Comparable comparable) {
        return new MatchedValue(comparable, pattern);
      }
    });
    Collections.sort(matched);
    return ContainerUtil.process(matched, consumer);
  }

}
