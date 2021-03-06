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

package com.intellij.testFramework.fixtures.impl;

import com.intellij.analysis.AnalysisScope;
import com.intellij.codeHighlighting.HighlightDisplayLevel;
import com.intellij.codeInsight.TargetElementUtilBase;
import com.intellij.codeInsight.completion.CodeCompletionHandlerBase;
import com.intellij.codeInsight.completion.CompletionProgressIndicator;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzerSettings;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.daemon.impl.*;
import com.intellij.codeInsight.folding.CodeFoldingManager;
import com.intellij.codeInsight.highlighting.actions.HighlightUsagesAction;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.ShowIntentionActionsHandler;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupManager;
import com.intellij.codeInsight.lookup.impl.LookupImpl;
import com.intellij.codeInspection.*;
import com.intellij.codeInspection.ex.*;
import com.intellij.facet.Facet;
import com.intellij.facet.FacetManager;
import com.intellij.find.FindManager;
import com.intellij.find.findUsages.FindUsagesHandler;
import com.intellij.find.findUsages.FindUsagesOptions;
import com.intellij.find.impl.FindManagerImpl;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.ide.startup.impl.StartupManagerImpl;
import com.intellij.ide.structureView.StructureViewBuilder;
import com.intellij.ide.structureView.newStructureView.StructureViewComponent;
import com.intellij.injected.editor.DocumentWindow;
import com.intellij.injected.editor.EditorWindow;
import com.intellij.internal.DumpLookupElementWeights;
import com.intellij.lang.LanguageStructureViewBuilder;
import com.intellij.lang.annotation.HighlightSeverity;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionManagerEx;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.actionSystem.DocCommandGroupId;
import com.intellij.openapi.editor.actionSystem.EditorActionManager;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.editor.ex.util.EditorUtil;
import com.intellij.openapi.editor.impl.DocumentImpl;
import com.intellij.openapi.editor.impl.DocumentMarkupModel;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.extensions.ExtensionPoint;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.extensions.ExtensionsArea;
import com.intellij.openapi.fileEditor.*;
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess;
import com.intellij.profile.codeInspection.InspectionProfileManager;
import com.intellij.profile.codeInspection.InspectionProjectProfileManager;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.PsiManagerImpl;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.psi.impl.cache.CacheManager;
import com.intellij.psi.impl.cache.impl.todo.TodoIndex;
import com.intellij.psi.impl.source.PsiFileImpl;
import com.intellij.psi.impl.source.tree.FileElement;
import com.intellij.psi.impl.source.tree.injected.InjectedLanguageUtil;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.psi.stubs.StubUpdatingIndex;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.refactoring.move.moveFilesOrDirectories.MoveFilesOrDirectoriesProcessor;
import com.intellij.refactoring.rename.*;
import com.intellij.rt.execution.junit.FileComparisonFailure;
import com.intellij.testFramework.*;
import com.intellij.testFramework.fixtures.*;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.FileBasedIndex;
import com.intellij.util.ui.UIUtil;
import gnu.trove.THashMap;
import junit.framework.ComparisonFailure;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import javax.swing.*;
import java.io.File;
import java.io.IOException;
import java.util.*;

/**
 * @author Dmitry Avdeev
 */
@SuppressWarnings({"TestMethodWithIncorrectSignature", "JUnitTestCaseWithNoTests", "JUnitTestClassNamingConvention", "TestOnlyProblems"})
public class CodeInsightTestFixtureImpl extends BaseFixture implements CodeInsightTestFixture {

  @NonNls private static final String PROFILE = "Configurable";

  private static final Function<IntentionAction,String> INTENTION_NAME_FUN = new Function<IntentionAction, String>() {
    @Override
    public String fun(final IntentionAction intentionAction) {
      return "\"" + intentionAction.getText() + "\"";
    }
  };

  private PsiManagerImpl myPsiManager;
  private VirtualFile myFile;
  private Editor myEditor;
  private String myTestDataPath;
  private boolean myEmptyLookup;

  private InspectionProfileEntry[] myInspections;
  private final Map<String, InspectionToolWrapper> myAvailableTools = new THashMap<String, InspectionToolWrapper>();

  private final TempDirTestFixture myTempDirFixture;
  protected final IdeaProjectTestFixture myProjectFixture;
  @NonNls private static final String XXX = "XXX";
  private final FileTreeAccessFilter myJavaFilesFilter = new FileTreeAccessFilter();
  private boolean myAllowDirt;
  private boolean myCaresAboutInjection = true;

  @SuppressWarnings("JUnitTestCaseWithNonTrivialConstructors")
  public CodeInsightTestFixtureImpl(@NotNull IdeaProjectTestFixture projectFixture, @NotNull TempDirTestFixture tempDirTestFixture) {
    myProjectFixture = projectFixture;
    myTempDirFixture = tempDirTestFixture;
  }

  @Override
  public void setTestDataPath(@NotNull String dataPath) {
    myTestDataPath = dataPath;
  }

  @NotNull
  @Override
  public String getTempDirPath() {
    return myTempDirFixture.getTempDirPath();
  }

  @NotNull
  @Override
  public TempDirTestFixture getTempDirFixture() {
    return myTempDirFixture;
  }

  @NotNull
  @Override
  public VirtualFile copyFileToProject(@NotNull @NonNls final String sourceFilePath, @NotNull @NonNls final String targetPath) {
    final String testDataPath = getTestDataPath();
    assert testDataPath != null : "test data path not specified";

    File fromFile = new File(testDataPath + "/" + sourceFilePath);
    if (!fromFile.exists()) {
      fromFile = new File(sourceFilePath);
    }

    VirtualFile result;
    final String path = fromFile.getPath();
    if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
      VfsRootAccess.allowRootAccess(path);
      Disposer.register(myTestRootDisposable, new Disposable() {
        @Override
        public void dispose() {
          VfsRootAccess.disallowRootAccess(path);
        }
      });
      VirtualFile fromVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(fromFile);
      if (fromVFile == null) {
        fromVFile = myTempDirFixture.getFile(sourceFilePath);
      }
      assert fromVFile != null : "can't find test data file " + sourceFilePath + " (" + testDataPath + ")";
      result = myTempDirFixture.copyFile(fromVFile, targetPath);
    }
    else {

      final File targetFile = new File(getTempDirPath() + "/" + targetPath);
      if (!targetFile.exists()) {
        if (fromFile.isDirectory()) {
          assert targetFile.mkdirs() : targetFile;
        }
        else {
          if (!fromFile.exists()) {
            Assert.fail("Cannot find source file: '" + sourceFilePath + "'. getTestDataPath()='" + testDataPath + "'. ");
          }
          try {
            FileUtil.copy(fromFile, targetFile);
          }
          catch (IOException e) {
            throw new RuntimeException("Cannot copy " + fromFile + " to " + targetFile, e);
          }
        }
      }

      final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile);
      assert file != null : targetFile;
      result = file;
    }
    result.putUserData(VfsTestUtil.TEST_DATA_FILE_PATH, path);
    return result;
  }

  @NotNull
  @Override
  public VirtualFile copyDirectoryToProject(@NotNull @NonNls final String sourceFilePath, @NotNull @NonNls final String targetPath) {
    final String testDataPath = getTestDataPath();
    assert testDataPath != null : "test data path not specified";

    final File fromFile = new File(testDataPath + "/" + sourceFilePath);
    if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
      return myTempDirFixture.copyAll(fromFile.getPath(), targetPath);
    }
    final File targetFile = new File(getTempDirPath() + "/" + targetPath);
    try {
      FileUtil.copyDir(fromFile, targetFile);
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

    final VirtualFile file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(targetFile);
    Assert.assertNotNull(file);
    file.refresh(false, true);
    return file;
  }

  @NotNull
  @Override
  public VirtualFile copyFileToProject(@NotNull @NonNls final String sourceFilePath) {
    return copyFileToProject(sourceFilePath, sourceFilePath);
  }

  @Override
  public void enableInspections(@NotNull InspectionProfileEntry... inspections) {
    myInspections = inspections;
    if (isInitialized()) {
      configureInspections(myInspections);
    }
  }

  private boolean isInitialized() {
    return myPsiManager != null;
  }

  @Override
  public void enableInspections(@NotNull final Class<? extends LocalInspectionTool>... inspections) {
    enableInspections(Arrays.asList(inspections));
  }

  @Override
  public void enableInspections(@NotNull final Collection<Class<? extends LocalInspectionTool>> inspections) {
    final ArrayList<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>();
    for (Class<? extends LocalInspectionTool> clazz : inspections) {
      try {
        LocalInspectionTool inspection = clazz.getConstructor().newInstance();
        tools.add(inspection);
      }
      catch (Exception e) {
        throw new RuntimeException("Cannot instantiate " + clazz);
      }
    }
    enableInspections(tools.toArray(new LocalInspectionTool[tools.size()]));
  }

  @Override
  public void disableInspections(@NotNull InspectionProfileEntry... inspections) {
    myAvailableTools.clear();
    List<InspectionProfileEntry> tools = new ArrayList<InspectionProfileEntry>(Arrays.asList(myInspections));
    for (Iterator<InspectionProfileEntry> i = tools.iterator(); i.hasNext();) {
      final InspectionProfileEntry tool = i.next();
      for (InspectionProfileEntry toRemove : inspections) {
        if (tool.getShortName().equals(toRemove.getShortName())) {
          i.remove();
          break;
        }
      }
    }
    myInspections = tools.toArray(new InspectionProfileEntry[tools.size()]);
    configureInspections(myInspections);
  }

  @Override
  public void enableInspections(@NotNull InspectionToolProvider... providers) {
    final ArrayList<LocalInspectionTool> tools = new ArrayList<LocalInspectionTool>();
    for (InspectionToolProvider provider : providers) {
      for (Class clazz : provider.getInspectionClasses()) {
        try {
          Object o = clazz.getConstructor().newInstance();
          if (o instanceof LocalInspectionTool) {
            LocalInspectionTool inspection = (LocalInspectionTool)o;
            tools.add(inspection);
          }
        }
        catch (Exception e) {
          throw new RuntimeException("Cannot instantiate " + clazz, e);
        }
      }
    }
    myInspections = tools.toArray(new LocalInspectionTool[tools.size()]);
    configureInspections(myInspections);
  }

  @Override
  public long testHighlighting(final boolean checkWarnings,
                               final boolean checkInfos,
                               final boolean checkWeakWarnings,
                               @NotNull final String... filePaths) {
    if (filePaths.length > 0) {
      configureByFilesInner(filePaths);
    }
    try {
      return collectAndCheckHighlighting(checkWarnings, checkInfos, checkWeakWarnings);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public long testHighlightingAllFiles(final boolean checkWarnings,
                                       final boolean checkInfos,
                                       final boolean checkWeakWarnings,
                                       @NotNull @NonNls final String... filePaths) {
    final ArrayList<VirtualFile> files = new ArrayList<VirtualFile>();
    for (String path : filePaths) {
      files.add(copyFileToProject(path));
    }
    return testHighlightingAllFiles(checkWarnings, checkInfos, checkWeakWarnings, VfsUtilCore.toVirtualFileArray(files));
  }

  @Override
  public long testHighlightingAllFiles(final boolean checkWarnings,
                                       final boolean checkInfos,
                                       final boolean checkWeakWarnings,
                                       @NotNull @NonNls final VirtualFile... files) {
    return collectAndCheckHighlightings(checkWarnings, checkInfos, checkWeakWarnings, files);
  }

  private long collectAndCheckHighlightings(final boolean checkWarnings,
                                            final boolean checkInfos,
                                            final boolean checkWeakWarnings,
                                            final VirtualFile[] files) {
    final List<Trinity<PsiFile, Editor, ExpectedHighlightingData>> datas =
      ContainerUtil.map2List(files, new Function<VirtualFile, Trinity<PsiFile, Editor, ExpectedHighlightingData>>() {
        @Override
        public Trinity<PsiFile, Editor, ExpectedHighlightingData> fun(final VirtualFile file) {
          final PsiFile psiFile = myPsiManager.findFile(file);
          Assert.assertNotNull(psiFile);
          final Document document = PsiDocumentManager.getInstance(getProject()).getDocument(psiFile);
          Assert.assertNotNull(document);
          ExpectedHighlightingData data = new ExpectedHighlightingData(document, checkWarnings, checkWeakWarnings, checkInfos, psiFile);
          data.init();
          return Trinity.create(psiFile, createEditor(file), data);
        }
      });
    long elapsed = 0;
    for (Trinity<PsiFile, Editor, ExpectedHighlightingData> trinity : datas) {
      myEditor = trinity.second;
      myFile = trinity.first.getVirtualFile();
      elapsed += collectAndCheckHighlighting(trinity.third);
    }
    return elapsed;
  }

  @Override
  public long checkHighlighting(final boolean checkWarnings, final boolean checkInfos, final boolean checkWeakWarnings) {
    try {
      return collectAndCheckHighlighting(checkWarnings, checkInfos, checkWeakWarnings);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public long checkHighlighting() {
    return checkHighlighting(true, false, true);
  }

  @Override
  public long testHighlighting(@NotNull final String... filePaths) {
    return testHighlighting(true, false, true, filePaths);
  }

  @Override
  public long testHighlighting(final boolean checkWarnings,
                               final boolean checkInfos,
                               final boolean checkWeakWarnings,
                               @NotNull final VirtualFile file) {
    openFileInEditor(file);
    try {
      return collectAndCheckHighlighting(checkWarnings, checkInfos, checkWeakWarnings);
    }
    catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @NotNull
  @Override
  public HighlightTestInfo testFile(@NonNls @NotNull String... filePath) {
    return new HighlightTestInfo(getTestRootDisposable(), filePath) {
      @Override
      public HighlightTestInfo doTest() {
        configureByFiles(filePaths);
        ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, getFile());
        if (checkSymbolNames) data.checkSymbolNames();
        data.init();
        collectAndCheckHighlighting(data);
        return this;
      }
    };
  }

  public void openFileInEditor(@NotNull final VirtualFile file) {
    myFile = file;
    myEditor = createEditor(file);
  }

  @Override
  public void testInspection(@NotNull String testDir, @NotNull InspectionToolWrapper toolWrapper) {
    VirtualFile sourceDir = copyDirectoryToProject(new File(testDir, "src").getPath(), "src");
    AnalysisScope scope = new AnalysisScope(getPsiManager().findDirectory(sourceDir));

    scope.invalidate();

    InspectionManagerEx inspectionManager = (InspectionManagerEx)InspectionManager.getInstance(getProject());
    GlobalInspectionContextImpl globalContext = createGlobalContextForTool(scope, getProject(), inspectionManager, toolWrapper);

    InspectionTestUtil.runTool(toolWrapper, scope, globalContext, inspectionManager);
    InspectionTestUtil.compareToolResults(globalContext, toolWrapper, false, new File(getTestDataPath(), testDir).getPath());
  }

  @NotNull
  public static GlobalInspectionContextImpl createGlobalContextForTool(@NotNull AnalysisScope scope,
                                                                       @NotNull final Project project,
                                                                       @NotNull InspectionManagerEx inspectionManager,
                                                                       @NotNull final InspectionToolWrapper ... toolWrappers) {
    final InspectionProfileImpl profile = InspectionProfileImpl.createSimple("test", project, toolWrappers);
    GlobalInspectionContextImpl context = new GlobalInspectionContextImpl(project, inspectionManager.getContentManager()) {
      @Override
      protected List<Tools> getUsedTools() {
        try {
          InspectionProfileImpl.INIT_INSPECTIONS = true;
          for (InspectionToolWrapper tool : toolWrappers) {
            profile.enableTool(tool.getShortName(), project);
          }
          return profile.getAllEnabledInspectionTools(project);
        }
        finally {
          InspectionProfileImpl.INIT_INSPECTIONS = false;
        }
      }
    };
    context.setCurrentScope(scope);

    return context;
  }

  @Override
  @Nullable
  public PsiReference getReferenceAtCaretPosition(@NotNull final String... filePaths) {
    if (filePaths.length > 0) {
      configureByFilesInner(filePaths);
    }
    return getFile().findReferenceAt(myEditor.getCaretModel().getOffset());
  }

  @Override
  @NotNull
  public PsiReference getReferenceAtCaretPositionWithAssertion(@NotNull final String... filePaths) {
    final PsiReference reference = getReferenceAtCaretPosition(filePaths);
    assert reference != null : "no reference found at " + myEditor.getCaretModel().getLogicalPosition();
    return reference;
  }

  @Override
  @NotNull
  public List<IntentionAction> getAvailableIntentions(@NotNull final String... filePaths) {
    if (filePaths.length > 0) {
      configureByFilesInner(filePaths);
    }
    return getAvailableIntentions();
  }

  @Override
  @NotNull
  public List<IntentionAction> getAllQuickFixes(@NotNull @NonNls final String... filePaths) {
    if (filePaths.length != 0) {
      configureByFilesInner(filePaths);
    }
    List<HighlightInfo> infos = doHighlighting();
    ArrayList<IntentionAction> actions = new ArrayList<IntentionAction>();
    for (HighlightInfo info : infos) {
      if (info.quickFixActionRanges != null) {
        for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
          actions.add(pair.getFirst().getAction());
        }
      }
    }
    return actions;
  }

  @Override
  @NotNull
  public List<IntentionAction> getAvailableIntentions() {
    doHighlighting();
    PsiFile file = getFile();
    Editor editor = getEditor();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageUtil.getTopLevelFile(file);
    }
    return getAvailableIntentions(editor, file);
  }

  @NotNull
  @Override
  public List<IntentionAction> filterAvailableIntentions(@NotNull final String hint) {
    final List<IntentionAction> availableIntentions = getAvailableIntentions();
    return ContainerUtil.findAll(availableIntentions, new Condition<IntentionAction>() {
      @Override
      public boolean value(final IntentionAction intentionAction) {
        return intentionAction.getText().startsWith(hint);
      }
    });
  }

  @Override
  public IntentionAction findSingleIntention(@NotNull final String hint) {
    final List<IntentionAction> list = filterAvailableIntentions(hint);
    if (list.isEmpty()) {
      Assert.fail("\"" + hint + "\" not in [" + StringUtil.join(getAvailableIntentions(), INTENTION_NAME_FUN, ", ") + "]");
    }
    else if (list.size() > 1) {
      Assert.fail("Too many intention found for \"" + hint + "\": [" + StringUtil.join(list, INTENTION_NAME_FUN, ", ") + "]");
    }
    return UsefulTestCase.assertOneElement(list);
  }

  @Override
  public IntentionAction getAvailableIntention(@NotNull final String intentionName, @NotNull final String... filePaths) {
    List<IntentionAction> intentions = getAvailableIntentions(filePaths);
    return CodeInsightTestUtil.findIntentionByText(intentions, intentionName);
  }

  @Override
  public void launchAction(@NotNull final IntentionAction action) {
    ShowIntentionActionsHandler.chooseActionAndInvoke(getFile(), getEditor(), action, action.getText());
  }

  @Override
  public void testCompletion(@NotNull @NonNls String[] filesBefore, @NotNull @TestDataFile @NonNls String fileAfter) {
    testCompletionTyping(filesBefore, "", fileAfter);
  }

  @Override
  public void testCompletionTyping(@NotNull final String[] filesBefore, @NotNull String toType, @NotNull final String fileAfter) {
    assertInitialized();
    configureByFiles(filesBefore);
    complete(CompletionType.BASIC);
    type(toType);
    try {
      checkResultByFile(fileAfter);
    }
    catch (RuntimeException e) {
      System.out.println("LookupElementStrings = " + getLookupElementStrings());
      throw e;
    }
  }

  protected void assertInitialized() {
    Assert.assertNotNull("setUp() hasn't been called", myPsiManager);
  }

  @Override
  public void testCompletion(@NotNull String fileBefore, @NotNull String fileAfter, @NotNull final String... additionalFiles) {
    testCompletionTyping(fileBefore, "", fileAfter, additionalFiles);
  }

  @Override
  public void testCompletionTyping(@NotNull @TestDataFile @NonNls String fileBefore,
                                   @NotNull String toType,
                                   @NotNull @TestDataFile @NonNls String fileAfter,
                                   @NotNull String... additionalFiles) {
    testCompletionTyping(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, fileBefore)), toType, fileAfter);
  }

  @Override
  public void testCompletionVariants(@NotNull final String fileBefore, @NotNull final String... expectedItems) {
    assertInitialized();
    final List<String> result = getCompletionVariants(fileBefore);
    Assert.assertNotNull(result);
    UsefulTestCase.assertSameElements(result, expectedItems);
  }

  @Override
  public List<String> getCompletionVariants(@NotNull final String... filesBefore) {
    assertInitialized();
    configureByFiles(filesBefore);
    final LookupElement[] items = complete(CompletionType.BASIC);
    Assert.assertNotNull("No lookup was shown, probably there was only one lookup element that was inserted automatically", items);
    return getLookupElementStrings();
  }

  @Override
  @Nullable
  public List<String> getLookupElementStrings() {
    assertInitialized();
    final LookupElement[] elements = getLookupElements();
    if (elements == null) return null;

    return ContainerUtil.map(elements, new Function<LookupElement, String>() {
      @Override
      public String fun(final LookupElement lookupItem) {
        return lookupItem.getLookupString();
      }
    });
  }

  @Override
  public void finishLookup(final char completionChar) {
    CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
      @Override
      public void run() {
        ((LookupImpl)LookupManager.getActiveLookup(getEditor())).finishLookup(completionChar);
      }
    }, null, null);
  }

  @Override
  public void testRename(@NotNull final String fileBefore, @NotNull final String fileAfter, @NotNull final String newName, @NotNull final String... additionalFiles) {
    assertInitialized();
    configureByFiles(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, fileBefore)));
    testRename(fileAfter, newName);
  }

  @Override
  public void testRename(@NotNull final String fileAfter, @NotNull final String newName) {
    renameElementAtCaret(newName);
    checkResultByFile(fileAfter);
  }

  @Override
  @NotNull
  public PsiElement getElementAtCaret() {
    assertInitialized();
    Editor editor = getCompletionEditor();
    int findTargetFlags = TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED | TargetElementUtilBase.ELEMENT_NAME_ACCEPTED;
    PsiElement element = TargetElementUtilBase.findTargetElement(editor, findTargetFlags);
    
    // if no references found in injected fragment, try outer document
    if (element == null && editor instanceof EditorWindow) {
      element = TargetElementUtilBase.findTargetElement(((EditorWindow)editor).getDelegate(), findTargetFlags);
    }
    
    assert element != null : "element not found in file " + myFile.getName() +
                             " at caret position, offset " + myEditor.getCaretModel().getOffset() + "\"" +
                             " psi structure: " + DebugUtil.psiToString(getFile(), true, true);
    return element;
  }

  @Override
  public void renameElementAtCaret(@NotNull final String newName) {
    renameElement(getElementAtCaret(), newName);
  }

  @Override
  public void renameElementAtCaretUsingHandler(@NotNull final String newName) {
      final DataContext editorContext = ((EditorEx)myEditor).getDataContext();
      final DataContext context = new DataContext() {
        @Override
        public Object getData(@NonNls final String dataId) {
          return PsiElementRenameHandler.DEFAULT_NAME.getName().equals(dataId)
                 ? newName
                 : editorContext.getData(dataId);
        }
      };
      final RenameHandler renameHandler = RenameHandlerRegistry.getInstance().getRenameHandler(context);
      assert renameHandler != null: "No handler for this context";

      renameHandler.invoke(getProject(), myEditor, getFile(), context);
  }

  @Override
  public void renameElement(@NotNull final PsiElement element, @NotNull final String newName) {
    final boolean searchInComments = false;
    final boolean searchTextOccurrences = false;
    renameElement(element, newName, searchInComments, searchTextOccurrences);
  }

  @Override
  public void renameElement(@NotNull final PsiElement element,
                            @NotNull final String newName,
                            final boolean searchInComments,
                            final boolean searchTextOccurrences) {
    final PsiElement substitution = RenamePsiElementProcessor.forElement(element).substituteElementToRename(element, myEditor);
    if (substitution == null) return;
    new RenameProcessor(getProject(), substitution, newName, searchInComments, searchTextOccurrences).run();
  }

  @Override
  public <T extends PsiElement> T findElementByText(@NotNull String text, @NotNull Class<T> elementClass) {
    int pos = PsiDocumentManager.getInstance(getProject()).getDocument(getFile()).getText().indexOf(text);
    assert pos >= 0 : text;
    return PsiTreeUtil.getParentOfType(getFile().findElementAt(pos), elementClass);
  }

  @Override
  public void type(final char c) {
    assertInitialized();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        final EditorActionManager actionManager = EditorActionManager.getInstance();
        if (c == '\b') {
          performEditorAction(IdeActions.ACTION_EDITOR_BACKSPACE);
          return;
        }
        if (c == '\n') {
          if (_performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM)) {
            return;
          }

          performEditorAction(IdeActions.ACTION_EDITOR_ENTER);
          return;
        }
        if (c == '\t') {
          if (_performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_REPLACE)) {
            return;
          }
          if (_performEditorAction(IdeActions.ACTION_EXPAND_LIVE_TEMPLATE_BY_TAB)) {
            return;
          }
          if (_performEditorAction(IdeActions.ACTION_EDITOR_NEXT_TEMPLATE_VARIABLE)) {
            return;
          }
          if (_performEditorAction(IdeActions.ACTION_EDITOR_TAB)) {
            return;
          }
        }
        if (c == Lookup.COMPLETE_STATEMENT_SELECT_CHAR) {
          if (_performEditorAction(IdeActions.ACTION_CHOOSE_LOOKUP_ITEM_COMPLETE_STATEMENT)) {
            return;
          }
        }

        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          @Override
          public void run() {
            CommandProcessor.getInstance().setCurrentCommandGroupId(myEditor.getDocument());
            ActionManagerEx.getInstanceEx().fireBeforeEditorTyping(c, getEditorDataContext());
            actionManager.getTypedAction().actionPerformed(getEditor(), c, getEditorDataContext());
          }
        }, null, DocCommandGroupId.noneGroupId(myEditor.getDocument()));
      }
    });
  }

  private DataContext getEditorDataContext() {
    return ((EditorEx)myEditor).getDataContext();
  }

  @Override
  public void type(String s) {
    for (int i = 0; i < s.length(); i++) {
      type(s.charAt(i));
    }
  }

  @Override
  public void performEditorAction(@NotNull final String actionId) {
    assertInitialized();
    _performEditorAction(actionId);
  }

  private boolean _performEditorAction(String actionId) {
    final DataContext dataContext = getEditorDataContext();

    final ActionManagerEx managerEx = ActionManagerEx.getInstanceEx();
    final AnAction action = managerEx.getAction(actionId);
    final AnActionEvent event = new AnActionEvent(null, dataContext, ActionPlaces.UNKNOWN, new Presentation(), managerEx, 0);

    return WriteCommandAction.runWriteCommandAction(getProject(), new Computable<Boolean>() {
      @Override
      public Boolean compute() {
        action.update(event);

        if (!event.getPresentation().isEnabled()) {
          return false;
        }

        managerEx.fireBeforeActionPerformed(action, dataContext, event);

        action.actionPerformed(event);

        managerEx.fireAfterActionPerformed(action, dataContext, event);
        return true;
      }
    });
  }

  @NotNull
  @Override
  public Presentation testAction(@NotNull AnAction action) {
    TestActionEvent e = new TestActionEvent(action);
    action.beforeActionPerformedUpdate(e);
    if (e.getPresentation().isEnabled() && e.getPresentation().isVisible()) {
      action.actionPerformed(e);
    }
    return e.getPresentation();
  }

  @NotNull
  @Override
  public Collection<UsageInfo> testFindUsages(@NotNull @NonNls final String... fileNames) {
    assertInitialized();
    configureByFiles(fileNames);
    final PsiElement targetElement = TargetElementUtilBase
      .findTargetElement(getEditor(), TargetElementUtilBase.ELEMENT_NAME_ACCEPTED | TargetElementUtilBase.REFERENCED_ELEMENT_ACCEPTED);
    assert targetElement != null : "Cannot find referenced element";
    return findUsages(targetElement);
  }

  @NotNull
  @Override
  public Collection<UsageInfo> findUsages(@NotNull final PsiElement targetElement) {
    final Project project = getProject();
    final FindUsagesHandler handler =
      ((FindManagerImpl)FindManager.getInstance(project)).getFindUsagesManager().getFindUsagesHandler(targetElement, false);

    final CommonProcessors.CollectProcessor<UsageInfo> processor = new CommonProcessors.CollectProcessor<UsageInfo>();
    assert handler != null : "Cannot find handler for: " + targetElement;
    final PsiElement[] psiElements = ArrayUtil.mergeArrays(handler.getPrimaryElements(), handler.getSecondaryElements());
    final FindUsagesOptions options = handler.getFindUsagesOptions(null);
    for (PsiElement psiElement : psiElements) {
      handler.processElementUsages(psiElement, processor, options);
    }
    return processor.getResults();
  }

  @NotNull
  @Override
  public RangeHighlighter[] testHighlightUsages(@NotNull final String... files) {
    configureByFiles(files);
    testAction(new HighlightUsagesAction());
    final Editor editor = getEditor();
    //final Editor editor = com.intellij.openapi.actionSystem.CommonDataKeys.EDITOR.getData(DataManager.getInstance().getDataContext());
    //assert editor != null;
    //HighlightUsagesHandler.invoke(getProject(), editor, getFile());
    return editor.getMarkupModel().getAllHighlighters();
  }

  @Override
  public void moveFile(@NotNull @NonNls final String filePath, @NotNull @NonNls final String to, @NotNull final String... additionalFiles) {
    assertInitialized();
    final Project project = getProject();
    new WriteCommandAction.Simple(project) {
      @Override
      protected void run() throws Exception {
        configureByFiles(ArrayUtil.reverseArray(ArrayUtil.append(additionalFiles, filePath)));
        final VirtualFile file = findFileInTempDir(to);
        assert file != null : "Directory " + to + " not found";
        assert file.isDirectory() : to + " is not a directory";
        final PsiDirectory directory = myPsiManager.findDirectory(file);
        new MoveFilesOrDirectoriesProcessor(project, new PsiElement[]{getFile()}, directory,
                                            false, false, null, null).run();
      }
    }.execute().throwException();
  }

  @Override
  @Nullable
  public GutterMark findGutter(@NotNull final String filePath) {
    configureByFilesInner(filePath);
    int offset = myEditor.getCaretModel().getOffset();

    final Collection<HighlightInfo> infos = doHighlighting();
    for (HighlightInfo info : infos) {
      if (info.endOffset >= offset && info.startOffset <= offset) {
        final GutterMark renderer = info.getGutterIconRenderer();
        if (renderer != null) {
          return renderer;
        }
      }
    }
    RangeHighlighter[] highlighters = DocumentMarkupModel.forDocument(myEditor.getDocument(), getProject(), true).getAllHighlighters();
    for (RangeHighlighter highlighter : highlighters) {
      if (highlighter.getEndOffset() >= offset && highlighter.getStartOffset() <= offset) {
        GutterMark renderer = highlighter.getGutterIconRenderer();
        if (renderer != null) {
          return renderer;
        }
      }
    }
    return null;
  }

  @Override
  @NotNull
  public Collection<GutterMark> findAllGutters(@NotNull final String filePath) {
    final Project project = getProject();
    final SortedMap<Integer, List<GutterMark>> result = new TreeMap<Integer, List<GutterMark>>();
    configureByFilesInner(filePath);

    List<HighlightInfo> infos = doHighlighting();
    for (HighlightInfo info : infos) {
      addGutterIconRenderer(info.getGutterIconRenderer(), info.startOffset, result);
    }

    RangeHighlighter[] highlighters = DocumentMarkupModel.forDocument(myEditor.getDocument(), project, true).getAllHighlighters();
    for (final RangeHighlighter highlighter : highlighters) {
      if (!highlighter.isValid()) continue;
      addGutterIconRenderer(highlighter.getGutterIconRenderer(), highlighter.getStartOffset(), result);
    }
    return ContainerUtil.concat(result.values());
  }

  private static void addGutterIconRenderer(final GutterMark renderer,
                                            final int offset,
                                            SortedMap<Integer, List<GutterMark>> result) {
    if (renderer == null) return;

    List<GutterMark> renderers = result.get(offset);
    if (renderers == null) {
      result.put(offset, renderers = new SmartList<GutterMark>());
    }
    renderers.add(renderer);
  }


  @Override
  public PsiFile addFileToProject(@NotNull @NonNls final String relativePath, @NotNull @NonNls final String fileText) {
    assertInitialized();
    return addFileToProject(getTempDirPath(), relativePath, fileText);
  }

  protected PsiFile addFileToProject(final String rootPath, final String relativePath, final String fileText) {
    return new WriteCommandAction<PsiFile>(getProject()) {
      @Override
      protected void run(@NotNull Result<PsiFile> result) throws Throwable {
        try {
          if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
            final VirtualFile file = myTempDirFixture.createFile(relativePath, fileText);
            result.setResult(PsiManager.getInstance(getProject()).findFile(file));
          }
          else {
            result.setResult(((HeavyIdeaTestFixture)myProjectFixture).addFileToProject(rootPath, relativePath, fileText));
          }
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        finally {
          ((PsiModificationTrackerImpl)PsiManager.getInstance(getProject()).getModificationTracker()).incCounter();
        }
      }
    }.execute().getResultObject();
  }

  public <T> void registerExtension(final ExtensionsArea area, final ExtensionPointName<T> epName, final T extension) {
    assertInitialized();
    final ExtensionPoint<T> extensionPoint = area.getExtensionPoint(epName);
    extensionPoint.registerExtension(extension);
    disposeOnTearDown(new Disposable() {
      @Override
      public void dispose() {
        extensionPoint.unregisterExtension(extension);
      }
    });
  }

  @NotNull
  @Override
  public PsiManager getPsiManager() {
    return myPsiManager;
  }

  @Override
  public LookupElement[] complete(@NotNull CompletionType type) {
    return complete(type, 1);
  }

  @Override
  public LookupElement[] complete(@NotNull final CompletionType type, final int invocationCount) {
    assertInitialized();
    myEmptyLookup = false;
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        CommandProcessor.getInstance().executeCommand(getProject(), new Runnable() {
          @Override
          public void run() {
            final CodeCompletionHandlerBase handler = new CodeCompletionHandlerBase(type) {

              @Override
              protected void completionFinished(int offset1,
                                                int offset2,
                                                CompletionProgressIndicator indicator,
                                                LookupElement[] items,
                                                boolean hasModifiers) {
                myEmptyLookup = items.length == 0;
                super.completionFinished(offset1, offset2, indicator, items, hasModifiers);
              }
            };
            Editor editor = getCompletionEditor();
            handler.invokeCompletion(getProject(), editor, invocationCount);
            PsiDocumentManager.getInstance(getProject()).commitAllDocuments(); // to compare with file text
          }
        }, null, null);
      }
    });

    return getLookupElements();
  }

  @Nullable
  protected Editor getCompletionEditor() {
    return InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, getFile());
  }

  @Override
  @Nullable
  public LookupElement[] completeBasic() {
    return complete(CompletionType.BASIC);
  }


  @Override
  public void completeBasicAllCarets() {
    final CaretModel caretModel = myEditor.getCaretModel();
    final List<Caret> carets = caretModel.getAllCarets();

    final List<Integer> originalOffsets = new ArrayList<Integer>(carets.size());

    for (final Caret caret : carets) {
      originalOffsets.add(caret.getOffset());
    }
    caretModel.removeSecondaryCarets();

    // We do it in reverse order because completions would affect offsets
    // i.e.: when you complete "spa" to "spam", next caret offset increased by 1
    Collections.reverse(originalOffsets);
    for (final int originalOffset : originalOffsets) {
      caretModel.moveToOffset(originalOffset);
      completeBasic();
    }
  }

  @Override
  @Nullable
  public LookupElement[] getLookupElements() {
    LookupImpl lookup = getLookup();
    if (lookup == null) {
      return myEmptyLookup ? LookupElement.EMPTY_ARRAY : null;
    }
    else {
      final List<LookupElement> list = lookup.getItems();
      return list.toArray(new LookupElement[list.size()]);
    }
  }

  @Override
  public void checkResult(@NotNull final String text) {
    checkResult(text, false);
  }

  @Override
  public void checkResult(@NotNull final String text, final boolean stripTrailingSpaces) {
    new WriteCommandAction(getProject()) {
      @Override
      protected void run(@NotNull Result result) throws Throwable {
        PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
        EditorUtil.fillVirtualSpaceUntilCaret(myEditor);
        checkResult("TEXT", stripTrailingSpaces, SelectionAndCaretMarkupLoader.fromText(text), getHostFile().getText());
      }
    }.execute();
  }

  @Override
  public void checkResultByFile(@NotNull final String expectedFile) {
    checkResultByFile(expectedFile, false);
  }

  @Override
  public void checkResultByFile(@NotNull final String expectedFile, final boolean ignoreTrailingWhitespaces) {
    assertInitialized();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          checkResultByFile(expectedFile, getHostFile(), ignoreTrailingWhitespaces);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  public void checkResultByFile(@NotNull final String filePath, @NotNull final String expectedFile, final boolean ignoreTrailingWhitespaces) {
    assertInitialized();

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        final String path = filePath.replace(File.separatorChar, '/');
        final VirtualFile copy = findFileInTempDir(path);
        if (copy == null) {
          throw new IllegalArgumentException("could not find results file " + path);
        }
        final PsiFile psiFile = myPsiManager.findFile(copy);
        assert psiFile != null;
        try {
          checkResultByFile(expectedFile, psiFile, ignoreTrailingWhitespaces);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    });
  }

  @Override
  public void setUp() throws Exception {
    super.setUp();

    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        try {
          myProjectFixture.setUp();
          myTempDirFixture.setUp();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
        myPsiManager = (PsiManagerImpl)PsiManager.getInstance(getProject());
        configureInspections(myInspections == null ? LocalInspectionTool.EMPTY_ARRAY : myInspections);

        DaemonCodeAnalyzerImpl daemonCodeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(getProject());
        daemonCodeAnalyzer.prepareForTest();

        DaemonCodeAnalyzerSettings.getInstance().setImportHintEnabled(false);
        ensureIndexesUpToDate(getProject());
        ((StartupManagerImpl)StartupManagerEx.getInstanceEx(getProject())).runPostStartupActivities();
      }
    });
  }

  @Override
  public void tearDown() throws Exception {
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        FileEditorManager editorManager = FileEditorManager.getInstance(getProject());
        VirtualFile[] openFiles = editorManager.getOpenFiles();
        for (VirtualFile openFile : openFiles) {
          editorManager.closeFile(openFile);
        }

        myEditor = null;
        myFile = null;
        myPsiManager = null;

        myInspections = null;
        myAvailableTools.clear();

        try {
          myProjectFixture.tearDown();
          myTempDirFixture.tearDown();
        }
        catch (Exception e) {
          throw new RuntimeException(e);
        }
      }
    });

    super.tearDown();
  }

  private void enableInspectionTool(@NotNull InspectionProfileEntry tool) {
    InspectionToolWrapper toolWrapper = InspectionToolRegistrar.wrapTool(tool);
    final String shortName = tool.getShortName();
    final HighlightDisplayKey key = HighlightDisplayKey.find(shortName);

    if (key == null) {
      String id = tool instanceof LocalInspectionTool ? ((LocalInspectionTool)tool).getID() : shortName;
      HighlightDisplayKey.register(shortName, toolWrapper.getDisplayName(), id);
    }
    myAvailableTools.put(shortName, toolWrapper);
  }

  private void configureInspections(@NotNull InspectionProfileEntry[] tools) {
    for (InspectionProfileEntry tool : tools) {
      enableInspectionTool(tool);
    }

    final InspectionProfileImpl profile = new InspectionProfileImpl(PROFILE) {
      @Override
      @NotNull
      public ModifiableModel getModifiableModel() {
        mySource = this;
        return this;
      }

      @Override
      @NotNull
      public InspectionToolWrapper[] getInspectionTools(PsiElement element) {
        final Collection<InspectionToolWrapper> tools = myAvailableTools.values();
        return tools.toArray(new InspectionToolWrapper[tools.size()]);
      }

      @Override
      public InspectionToolWrapper getToolById(@NotNull String id, @NotNull PsiElement element) {
        if (myAvailableTools.containsKey(id)) {
          return myAvailableTools.get(id);
        }

        return super.getToolById(id, element);
      }

      @NotNull
      @Override
      public List<Tools> getAllEnabledInspectionTools(Project project) {
        List<Tools> result = new ArrayList<Tools>();
        for (InspectionToolWrapper toolWrapper : getInspectionTools(getFile())) {
          result.add(new ToolsImpl(toolWrapper, toolWrapper.getDefaultLevel(), true));
        }
        return result;
      }

      @Override
      public boolean isToolEnabled(HighlightDisplayKey key, PsiElement element) {
        return key != null && key.toString() != null && myAvailableTools.containsKey(key.toString());
      }

      @Override
      public InspectionToolWrapper getInspectionTool(@NotNull String shortName, Project project) {
        return myAvailableTools.get(shortName);
      }

      @Override
      public HighlightDisplayLevel getErrorLevel(@NotNull HighlightDisplayKey key, PsiElement element) {
        final InspectionToolWrapper toolWrapper = myAvailableTools.get(key.toString());
        return toolWrapper == null ? HighlightDisplayLevel.WARNING : toolWrapper.getDefaultLevel();
      }

      @Override
      public InspectionToolWrapper getInspectionTool(@NotNull String shortName, @NotNull PsiElement element) {
        return myAvailableTools.get(shortName);
      }
    };
    final InspectionProfileManager inspectionProfileManager = InspectionProfileManager.getInstance();
    inspectionProfileManager.addProfile(profile);
    Disposer.register(getTestRootDisposable(), new Disposable() {
      @Override
      public void dispose() {
        inspectionProfileManager.deleteProfile(PROFILE);
      }
    });
    inspectionProfileManager.setRootProfile(profile.getName());
    InspectionProjectProfileManager.getInstance(getProject()).updateProfile(profile);
    InspectionProjectProfileManager.getInstance(getProject()).setProjectProfile(profile.getName());
  }

  @NotNull
  private PsiFile[] configureByFilesInner(@NonNls @NotNull String... filePaths) {
    assertInitialized();
    myFile = null;
    myEditor = null;
    PsiFile[] psiFiles = new PsiFile[filePaths.length];
    for (int i = filePaths.length - 1; i >= 0; i--) {
      psiFiles[i] = configureByFileInner(filePaths[i]);
    }
    return psiFiles;
  }

  @Override
  public PsiFile configureByFile(@NotNull final String file) {
    configureByFilesInner(file);
    return getFile();
  }

  @NotNull
  @Override
  public PsiFile[] configureByFiles(@NotNull @NonNls final String... files) {
    return configureByFilesInner(files);
  }

  @Override
  public PsiFile configureByText(@NotNull final FileType fileType, @NotNull @NonNls final String text) {
    assertInitialized();
    final String extension = fileType.getDefaultExtension();
    final FileTypeManager fileTypeManager = FileTypeManager.getInstance();
    if (fileTypeManager.getFileTypeByExtension(extension) != fileType) {
      new WriteCommandAction(getProject()) {
        @Override
        protected void run(@NotNull Result result) throws Exception {
          fileTypeManager.associateExtension(fileType, extension);
        }
      }.execute();
    }
    final String fileName = "aaa." + extension;
    return configureByText(fileName, text);
  }

  @Override
  public PsiFile configureByText(@NotNull final String fileName, @NotNull @NonNls final String text) {
    assertInitialized();
    return new WriteCommandAction<PsiFile>(getProject()) {
      @Override
      protected void run(@NotNull Result<PsiFile> result) throws Throwable {
        final VirtualFile vFile;
        if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
          final VirtualFile root = LightPlatformTestCase.getSourceRoot();
          root.refresh(false, false);
          vFile = root.findOrCreateChildData(this, fileName);
        }
        else if (myTempDirFixture instanceof TempDirTestFixtureImpl) {
          final File tempFile = ((TempDirTestFixtureImpl)myTempDirFixture).createTempFile(fileName);
          vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(tempFile);
        }
        else {
          vFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(new File(getTempDirPath(), fileName));
        }

        prepareVirtualFile(vFile);

        final Document document = FileDocumentManager.getInstance().getCachedDocument(vFile);
        if (document != null) {
          PsiDocumentManager.getInstance(getProject()).doPostponedOperationsAndUnblockDocument(document);
          FileDocumentManager.getInstance().saveDocument(document);
        }

        VfsUtil.saveText(vFile, text);
        configureInner(vFile, SelectionAndCaretMarkupLoader.fromFile(vFile));
        result.setResult(getFile());
      }
    }.execute().getResultObject();
  }

  @Override
  public Document getDocument(@NotNull final PsiFile file) {
    assertInitialized();
    return PsiDocumentManager.getInstance(getProject()).getDocument(file);
  }

  private PsiFile configureByFileInner(@NonNls String filePath) {
    assertInitialized();
    final VirtualFile file = copyFileToProject(filePath);
    return configureByFileInner(file);
  }

  @Override
  public PsiFile configureFromTempProjectFile(@NotNull final String filePath) {
    final VirtualFile fileInTempDir = findFileInTempDir(filePath);
    if (fileInTempDir == null) {
      throw new IllegalArgumentException("Could not find file in temp dir: " + filePath);
    }
    return configureByFileInner(fileInTempDir);
  }

  @Override
  public void configureFromExistingVirtualFile(@NotNull VirtualFile virtualFile) {
    configureByFileInner(virtualFile);
  }

  private PsiFile configureByFileInner(@NotNull VirtualFile copy) {
    return configureInner(copy, SelectionAndCaretMarkupLoader.fromFile(copy));
  }

  private PsiFile configureInner(@NotNull final VirtualFile copy, final SelectionAndCaretMarkupLoader loader) {
    assertInitialized();
    new WriteCommandAction.Simple(getProject()) {
      @Override
      public void run() {
        try {
          copy.setBinaryContent(loader.newFileText.getBytes(copy.getCharset()));
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
        myFile = copy;
        myEditor = createEditor(copy);
        assert myEditor != null : "editor couldn't be created for: " + copy.getPath() + ", use copyFileToProject() instead of configureByFile()";

        EditorTestUtil.setCaretsAndSelection(myEditor, loader.caretState);

        Module module = getModule();
        if (module != null) {
          for (Facet facet : FacetManager.getInstance(module).getAllFacets()) {
            module.getMessageBus().syncPublisher(FacetManager.FACETS_TOPIC).facetConfigurationChanged(facet);
          }
        }
        if (myCaresAboutInjection) {
          setupEditorForInjectedLanguage();
        }
      }
    }.execute().throwException();


    return getFile();
  }

  protected void prepareVirtualFile(@NotNull VirtualFile file) {
  }

  private void setupEditorForInjectedLanguage() {
    Editor editor = InjectedLanguageUtil.getEditorForInjectedLanguageNoCommit(myEditor, getFile());
    if (editor instanceof EditorWindow) {
      myFile = ((EditorWindow)editor).getInjectedFile().getViewProvider().getVirtualFile();
      myEditor = editor;
    }
  }


  @Override
  public VirtualFile findFileInTempDir(@NotNull final String filePath) {
    if (myTempDirFixture instanceof LightTempDirTestFixtureImpl) {
      return myTempDirFixture.getFile(filePath);
    }
    String fullPath = getTempDirPath() + "/" + filePath;

    final VirtualFile copy = LocalFileSystem.getInstance().refreshAndFindFileByPath(fullPath.replace(File.separatorChar, '/'));
    assert copy != null : "file " + fullPath + " not found";
    return copy;
  }

  @Nullable
  private Editor createEditor(VirtualFile file) {
    final Project project = getProject();
    final FileEditorManager instance = FileEditorManager.getInstance(project);
    if (file.getFileType().isBinary()) {
      return null;
    }
    return instance.openTextEditor(new OpenFileDescriptor(project, file, 0), false);
  }

  private long collectAndCheckHighlighting(boolean checkWarnings, boolean checkInfos, boolean checkWeakWarnings) throws Exception {
    ExpectedHighlightingData data = new ExpectedHighlightingData(myEditor.getDocument(), checkWarnings, checkWeakWarnings, checkInfos, getHostFile());
    data.init();
    return collectAndCheckHighlighting(data);
  }

  private PsiFile getHostFile() {
    return InjectedLanguageUtil.getTopLevelFile(getFile());
  }

  private long collectAndCheckHighlighting(@NotNull ExpectedHighlightingData data) {
    final Project project = getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFileImpl file = (PsiFileImpl)getHostFile();
    FileElement hardRefToFileElement = file.calcTreeElement();//to load text

    //to initialize caches
    if (!DumbService.isDumb(project)) {
      CacheManager.SERVICE.getInstance(project).getFilesWithWord(XXX, UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(project), true);
    }

    List<HighlightInfo> infos;
    final long start = System.currentTimeMillis();
    ((PsiManagerImpl)PsiManager.getInstance(project)).setAssertOnFileLoadingFilter(myJavaFilesFilter, myTestRootDisposable);

    //    ProfilingUtil.startCPUProfiling();
    try {
      infos = doHighlighting();
      removeDuplicatedRangesForInjected(infos);
    }
    finally {
      ((PsiManagerImpl)PsiManager.getInstance(project)).setAssertOnFileLoadingFilter(VirtualFileFilter.NONE, myTestRootDisposable);
    }
    //    ProfilingUtil.captureCPUSnapshot("testing");
    final long elapsed = System.currentTimeMillis() - start;

    data.checkResult(infos, file.getText());
    hardRefToFileElement.hashCode(); // use it so gc won't collect it
    return elapsed;
  }

  private static void removeDuplicatedRangesForInjected(List<HighlightInfo> infos) {
    Collections.sort(infos, new Comparator<HighlightInfo>() {
      @Override
      public int compare(HighlightInfo o1, HighlightInfo o2) {
        final int i = o2.startOffset - o1.startOffset;
        return i != 0 ? i : o1.getSeverity().myVal - o2.getSeverity().myVal;
      }
    });
    HighlightInfo prevInfo = null;
    for (Iterator<HighlightInfo> it = infos.iterator(); it.hasNext();) {
      final HighlightInfo info = it.next();
      if (prevInfo != null &&
          info.getSeverity() == HighlightInfoType.SYMBOL_TYPE_SEVERITY &&
          info.getDescription() == null &&
          info.startOffset == prevInfo.startOffset &&
          info.endOffset == prevInfo.endOffset) {
        it.remove();
      }
      prevInfo = info.type == HighlightInfoType.INJECTED_LANGUAGE_FRAGMENT ? info : null;
    }
  }

  @Override
  @NotNull
  public List<HighlightInfo> doHighlighting() {
    final Project project = getProject();
    PsiDocumentManager.getInstance(project).commitAllDocuments();

    PsiFile file = getFile();
    Editor editor = getEditor();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
      file = InjectedLanguageUtil.getTopLevelFile(file);
    }
    return instantiateAndRun(file, editor, ArrayUtil.EMPTY_INT_ARRAY, myAllowDirt);
  }

  @NotNull
  @Override
  public List<HighlightInfo> doHighlighting(@NotNull final HighlightSeverity minimalSeverity) {
    return ContainerUtil.filter(doHighlighting(), new Condition<HighlightInfo>() {
      @Override
      public boolean value(HighlightInfo info) {
        return info.getSeverity().compareTo(minimalSeverity) >= 0;
      }
    });
  }

  @NotNull
  public static List<HighlightInfo> instantiateAndRun(@NotNull PsiFile file,
                                                      @NotNull Editor editor,
                                                      @NotNull int[] toIgnore,
                                                      boolean canChangeDocument) {
    Project project = file.getProject();
    ensureIndexesUpToDate(project);
    DaemonCodeAnalyzerImpl codeAnalyzer = (DaemonCodeAnalyzerImpl)DaemonCodeAnalyzer.getInstance(project);
    TextEditor textEditor = TextEditorProvider.getInstance().getTextEditor(editor);
    ProcessCanceledException exception = null;
    for (int i = 0; i < 100; i++) {
      try {
        List<HighlightInfo> infos = codeAnalyzer.runPasses(file, editor.getDocument(), textEditor, toIgnore, canChangeDocument, null);
        infos.addAll(DaemonCodeAnalyzerEx.getInstanceEx(project).getFileLevelHighlights(project, file));
        return infos;
      }
      catch (ProcessCanceledException e) {
        exception = e;
      }
    }
    // unable to highlight after 100 retries
    throw exception;
  }

  public static void ensureIndexesUpToDate(Project project) {
    if (!DumbService.isDumb(project)) {
      FileBasedIndex.getInstance().ensureUpToDate(StubUpdatingIndex.INDEX_ID, project, null);
      FileBasedIndex.getInstance().ensureUpToDate(TodoIndex.NAME, project, null);
    }
  }

  @NotNull
  @Override
  public String getTestDataPath() {
    return myTestDataPath;
  }

  @Override
  public Project getProject() {
    return myProjectFixture.getProject();
  }

  @Override
  public Module getModule() {
    return myProjectFixture.getModule();
  }

  @Override
  public Editor getEditor() {
    return myEditor;
  }

  @Override
  public int getCaretOffset() {
    return myEditor.getCaretModel().getOffset();
  }

  @Override
  public PsiFile getFile() {
    return myFile == null ? null : ApplicationManager.getApplication().runReadAction(new Computable<PsiFile>() {
      @Override
      public PsiFile compute() {
        return PsiManager.getInstance(getProject()).findFile(myFile);
      }
    });
  }

  public static List<IntentionAction> getAvailableIntentions(@NotNull final Editor editor, @NotNull final PsiFile file) {
    return ApplicationManager.getApplication().runReadAction(new Computable<List<IntentionAction>>() {
      @Override
      public List<IntentionAction> compute() {
        return doGetAvailableIntentions(editor, file);
      }
    });
  }

  private static List<IntentionAction> doGetAvailableIntentions(@NotNull Editor editor, @NotNull PsiFile file) {
    ShowIntentionsPass.IntentionsInfo intentions = new ShowIntentionsPass.IntentionsInfo();
    ShowIntentionsPass.getActionsToShow(editor, file, intentions, -1);
    List<HighlightInfo.IntentionActionDescriptor> descriptors = new ArrayList<HighlightInfo.IntentionActionDescriptor>();
    descriptors.addAll(intentions.intentionsToShow);
    descriptors.addAll(intentions.errorFixesToShow);
    descriptors.addAll(intentions.inspectionFixesToShow);
    descriptors.addAll(intentions.guttersToShow);

    PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
    List<IntentionAction> result = new ArrayList<IntentionAction>();

    List<HighlightInfo> infos = DaemonCodeAnalyzerEx.getInstanceEx(file.getProject()).getFileLevelHighlights(file.getProject(), file);
    for (HighlightInfo info : infos) {
      for (Pair<HighlightInfo.IntentionActionDescriptor, TextRange> pair : info.quickFixActionRanges) {
        HighlightInfo.IntentionActionDescriptor actionInGroup = pair.first;
        if (actionInGroup.getAction().isAvailable(file.getProject(), editor, file)) {
          descriptors.add(actionInGroup);
        }
      }
    }

    // add all intention options for simplicity
    for (HighlightInfo.IntentionActionDescriptor descriptor : descriptors) {
      result.add(descriptor.getAction());
      List<IntentionAction> options = descriptor.getOptions(element,editor);
      if (options != null) {
        for (IntentionAction option : options) {
          if (option.isAvailable(file.getProject(), editor, file)) {
            result.add(option);
          }
        }
      }
    }
    return result;
  }

  @Override
  public void allowTreeAccessForFile(@NotNull final VirtualFile file) {
    myJavaFilesFilter.allowTreeAccessForFile(file);
  }

  @Override
  public void allowTreeAccessForAllFiles() {
    myJavaFilesFilter.allowTreeAccessForAllFiles();
  }

  static class SelectionAndCaretMarkupLoader {
    final String filePath;
    final String newFileText;
    final EditorTestUtil.CaretAndSelectionState caretState;

    static SelectionAndCaretMarkupLoader fromFile(String path, String charset) throws IOException {
      return new SelectionAndCaretMarkupLoader(StringUtil.convertLineSeparators(FileUtil.loadFile(new File(path), charset)), path);
    }

    static SelectionAndCaretMarkupLoader fromFile(@NotNull VirtualFile file) {
      final String text;
      try {
        text = VfsUtilCore.loadText(file);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
      return new SelectionAndCaretMarkupLoader(StringUtil.convertLineSeparators(text), file.getPath());
    }

    static SelectionAndCaretMarkupLoader fromText(String text) {
      return new SelectionAndCaretMarkupLoader(text, null);
    }

    private SelectionAndCaretMarkupLoader(String fileText, String filePath) {
      this.filePath = filePath;
      final Document document = EditorFactory.getInstance().createDocument(fileText);
      caretState = EditorTestUtil.extractCaretAndSelectionMarkers(document);
      newFileText = document.getText();
    }
  }

  private void checkResultByFile(@NonNls @NotNull String expectedFile,
                                 @NotNull PsiFile originalFile,
                                 boolean stripTrailingSpaces) throws IOException {
    if (!stripTrailingSpaces) {
      EditorUtil.fillVirtualSpaceUntilCaret(myEditor);
    }
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();

    final String fileText = originalFile.getText();
    final String path = getTestDataPath() + "/" + expectedFile;

    /*final VirtualFile result = LocalFileSystem.getInstance().findFileByPath(path);
    final int caret = myEditor.getCaretModel().getOffset();
    final String newText = myFile == originalFile ? fileText.substring(0, caret) + "<caret>" + fileText.substring(caret) : fileText;
    VfsUtil.saveText(result, newText);*/

    VirtualFile virtualFile = originalFile.getVirtualFile();
    String charset = virtualFile == null? null : virtualFile.getCharset().name();
    checkResult(expectedFile, stripTrailingSpaces, SelectionAndCaretMarkupLoader.fromFile(path, charset), fileText);

  }

  @SuppressWarnings("ConstantConditions")
  private void checkResult(@NotNull String expectedFile,
                           final boolean stripTrailingSpaces,
                           final SelectionAndCaretMarkupLoader loader,
                           String actualText) {
    assertInitialized();
    Project project = getProject();
    Editor editor = getEditor();
    if (editor instanceof EditorWindow) {
      editor = ((EditorWindow)editor).getDelegate();
    }

    UsefulTestCase.doPostponedFormatting(getProject());
    if (stripTrailingSpaces) {
      actualText = stripTrailingSpaces(actualText);
    }

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    String newFileText1 = loader.newFileText;
    if (stripTrailingSpaces) {
      newFileText1 = stripTrailingSpaces(newFileText1);
    }

    actualText = StringUtil.convertLineSeparators(actualText);

    if (!Comparing.equal(newFileText1, actualText)) {
      if (loader.filePath != null) {
        throw new FileComparisonFailure(expectedFile, newFileText1, actualText, loader.filePath);
      }
      else {
        throw new ComparisonFailure(expectedFile, newFileText1, actualText);
      }
    }

    EditorTestUtil.verifyCaretAndSelectionState(editor, loader.caretState, expectedFile);
  }

  private String stripTrailingSpaces(String actualText) {
    final Document document = EditorFactory.getInstance().createDocument(actualText);
    ((DocumentImpl)document).stripTrailingSpaces(getProject());
    actualText = document.getText();
    return actualText;
  }

  public void canChangeDocumentDuringHighlighting(boolean canI) {
    myAllowDirt = canI;
  }

  private static final String START_FOLD = "<fold\\stext=\'[^\']*\'(\\sexpand=\'[^\']*\')*>";
  private static final String END_FOLD = "</fold>";

  private static class Border implements Comparable<Border> {
    public static final boolean LEFT = true;
    public static final boolean RIGHT = false;
    public boolean mySide;
    public int myOffset;
    public String myText;
    public boolean myIsExpanded;

    private Border(boolean side, int offset, String text, boolean isExpanded) {
      mySide = side;
      myOffset = offset;
      myText = text;
      myIsExpanded = isExpanded;
    }

    public boolean isExpanded() {
      return myIsExpanded;
    }

    public boolean isSide() {
      return mySide;
    }

    public int getOffset() {
      return myOffset;
    }

    public String getText() {
      return myText;
    }

    @Override
    public int compareTo(@NotNull Border o) {
      return getOffset() < o.getOffset() ? 1 : -1;
    }
  }

  public String getFoldingDescription(boolean withCollapseStatus) {
    CodeFoldingManager.getInstance(getProject()).buildInitialFoldings(myEditor);

    final FoldingModel model = myEditor.getFoldingModel();
    final FoldRegion[] foldingRegions = model.getAllFoldRegions();
    final List<Border> borders = new LinkedList<Border>();

    for (FoldRegion region : foldingRegions) {
      borders.add(new Border(Border.LEFT, region.getStartOffset(), region.getPlaceholderText(), region.isExpanded()));
      borders.add(new Border(Border.RIGHT, region.getEndOffset(), "", region.isExpanded()));
    }
    Collections.sort(borders);

    StringBuilder result = new StringBuilder(myEditor.getDocument().getText());
    for (Border border : borders) {
      result.insert(border.getOffset(), border.isSide() == Border.LEFT ? "<fold text=\'" + border.getText() + "\'" +
                                                                         (withCollapseStatus ? " expand=\'" +
                                                                                                    border.isExpanded() +
                                                                                                    "\'" : "") +
                                                                          ">" : END_FOLD);
    }

    return result.toString();
  }

  private void testFoldingRegions(final String verificationFileName, boolean doCheckCollapseStatus) {
    String expectedContent;
    try {
      expectedContent = FileUtil.loadFile(new File(verificationFileName));
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
    Assert.assertNotNull(expectedContent);

    expectedContent = StringUtil.replace(expectedContent, "\r", "");
    final String cleanContent = expectedContent.replaceAll(START_FOLD, "").replaceAll(END_FOLD, "");

    configureByText(FileTypeManager.getInstance().getFileTypeByFileName(verificationFileName), cleanContent);
    final String actual = getFoldingDescription(doCheckCollapseStatus);

    Assert.assertEquals(expectedContent, actual);
  }

  @Override
  public void testFoldingWithCollapseStatus(@NotNull final String verificationFileName) {
    testFoldingRegions(verificationFileName, true);
  }

  @Override
  public void testFolding(@NotNull final String verificationFileName) {
    testFoldingRegions(verificationFileName, false);
  }

  @Override
  public void assertPreferredCompletionItems(final int selected, @NotNull @NonNls final String... expected) {
    final LookupImpl lookup = getLookup();
    Assert.assertNotNull(lookup);

    final JList list = lookup.getList();
    List<String> strings = getLookupElementStrings();
    assert strings != null;
    final List<String> actual = strings.subList(0, Math.min(expected.length, strings.size()));
    if (!actual.equals(Arrays.asList(expected))) {
      UsefulTestCase.assertOrderedEquals(DumpLookupElementWeights.getLookupElementWeights(lookup), expected);
    }
    if (selected != list.getSelectedIndex()) {
      System.out.println(DumpLookupElementWeights.getLookupElementWeights(lookup));
    }
    Assert.assertEquals(selected, list.getSelectedIndex());
  }

  @Override
  public void testStructureView(@NotNull Consumer<StructureViewComponent> consumer) {
    assert myFile != null : "configure first";

    final FileEditor fileEditor = FileEditorManager.getInstance(getProject()).getSelectedEditor(myFile);
    assert fileEditor != null : "editor not opened for " + myFile;

    final StructureViewBuilder builder = LanguageStructureViewBuilder.INSTANCE.getStructureViewBuilder(getFile());
    assert builder != null : "no builder for " + myFile;

    StructureViewComponent component = null;
    try {
      component = (StructureViewComponent)builder.createStructureView(fileEditor, myProjectFixture.getProject());
      consumer.consume(component);
    }
    finally {
      if (component != null) Disposer.dispose(component);
    }
  }

  @Override
  public void setCaresAboutInjection(boolean caresAboutInjection) {
    myCaresAboutInjection = caresAboutInjection;
  }

  @Override
  public LookupImpl getLookup() {
    return (LookupImpl)LookupManager.getActiveLookup(myEditor);
  }

  protected void bringRealEditorBack() {
    PsiDocumentManager.getInstance(getProject()).commitAllDocuments();
    if (myEditor instanceof EditorWindow) {
      Document document = ((DocumentWindow)myEditor.getDocument()).getDelegate();
      myFile = FileDocumentManager.getInstance().getFile(document);
      myEditor = ((EditorWindow)myEditor).getDelegate();
    }
  }
}
