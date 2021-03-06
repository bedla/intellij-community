// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch;

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase;
import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LanguageLevelProjectExtension;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.PsiElement;
import com.intellij.structuralsearch.impl.matcher.CompiledPattern;
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler;
import com.intellij.structuralsearch.plugin.ui.UIUtil;
import com.intellij.util.SmartList;

import java.io.File;
import java.io.IOException;
import java.util.List;

public abstract class StructuralSearchTestCase extends LightQuickFixTestCase {
  protected MatchOptions options;
  protected Matcher testMatcher;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    testMatcher = new Matcher(getProject());
    options = new MatchOptions();
    options.setRecursiveSearch(true);
    LanguageLevelProjectExtension.getInstance(getProject()).setLanguageLevel(LanguageLevel.JDK_1_5);
  }

  @Override
  protected void tearDown() throws Exception {
    testMatcher = null;
    options = null;
    super.tearDown();
  }

  protected int findMatchesCount(String in, String pattern, LanguageFileType fileType) {
    return findMatches(in, pattern, fileType).size();
  }

  protected List<MatchResult> findMatches(String in,
                                          String pattern,
                                          LanguageFileType patternFileType,
                                          Language patternLanguage,
                                          LanguageFileType sourceFileType,
                                          boolean physicalSourceFile) {
    options.fillSearchCriteria(pattern);
    options.setFileType(patternFileType);
    options.setDialect(patternLanguage);

    final String message = checkApplicableConstraints(options, getProject());
    assertNull(message, message);
    return testMatcher.testFindMatches(in, options, true, sourceFileType, physicalSourceFile);
  }

  public static String checkApplicableConstraints(MatchOptions options, Project project) {
    final CompiledPattern compiledPattern = PatternCompiler.compilePattern(project, options, true, false);
    final StructuralSearchProfile profile = StructuralSearchUtil.getProfileByFileType(options.getFileType());
    assert profile != null;
    for (String varName : options.getVariableConstraintNames()) {
      final List<PsiElement> nodes = compiledPattern.getVariableNodes(varName);
      final MatchVariableConstraint constraint = options.getVariableConstraint(varName);
      final List<String> usedConstraints = new SmartList<>();
      if (!StringUtil.isEmpty(constraint.getRegExp())) {
        usedConstraints.add(UIUtil.TEXT);
      }
      if (constraint.isWithinHierarchy()) {
        usedConstraints.add(UIUtil.TEXT_HIERARCHY);
      }
      if (constraint.getMinCount() == 0) {
        usedConstraints.add(UIUtil.MINIMUM_ZERO);
      }
      if (constraint.getMaxCount() > 1) {
        usedConstraints.add(UIUtil.MAXIMUM_UNLIMITED);
      }
      if (!StringUtil.isEmpty(constraint.getNameOfExprType())) {
        usedConstraints.add(UIUtil.TYPE);
      }
      if (!StringUtil.isEmpty(constraint.getNameOfFormalArgType())) {
        usedConstraints.add(UIUtil.EXPECTED_TYPE);
      }
      if (!StringUtil.isEmpty(constraint.getReferenceConstraint())) {
        usedConstraints.add(UIUtil.REFERENCE);
      }
      for (String usedConstraint : usedConstraints) {
        if (!profile.isApplicableConstraint(usedConstraint, nodes, false, constraint.isPartOfSearchResults())) {
          return usedConstraint + " not applicable for " + varName;
        }
      }
    }
    return null;
  }

  protected List<MatchResult> findMatches(String in, String pattern, LanguageFileType patternFileType) {
    return findMatches(in, pattern, patternFileType, null, patternFileType, false);
  }

  protected int findMatchesCount(String in, String pattern) {
    return findMatchesCount(in, pattern, StdFileTypes.JAVA);
  }

  protected String loadFile(String fileName) throws IOException {
    return FileUtilRt.loadFile(new File(getTestDataPath() + fileName), CharsetToolkit.UTF8, true);
  }
}
