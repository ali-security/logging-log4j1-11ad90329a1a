/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.log4j.nt;

import java.io.File;
import java.io.IOException;
import java.util.StringTokenizer;

import junit.framework.TestCase;
import org.apache.log4j.LogManager;


/**
 * Tests of how NTEventLogAppender locates its native library
 * (DLL preloading, sonatype-2010-0053).
 *
 * <p>Must run in its own JVM with the <code>log4j.nteventlog.dll.path</code>
 * system property set to the absolute path of a loadable native library and
 * with <code>java.library.path</code> holding planted, non-loadable
 * NTEventLogAppender libraries. Searching the library path would pick up
 * a planted library and fail with an UnsatisfiedLinkError.
 */
public class NTEventLogAppenderDllPathTest extends TestCase {

  /**
   * Error raised while initializing NTEventLogAppender, which loads its
   * native library, before any test changes the system property.
   */
  private static final Throwable LOAD_ERROR = loadAppenderClass();

  private String originalDllPath;

  private static Throwable loadAppenderClass() {
    try {
      Class.forName("org.apache.log4j.nt.NTEventLogAppender");
      return null;
    } catch (Throwable t) {
      return t;
    }
  }

  public void setUp() {
    originalDllPath = System.getProperty(NTEventLogAppender.DLL_PATH_PROPERTY);
    System.getProperties().remove(NTEventLogAppender.DLL_PATH_PROPERTY);
  }

  public void tearDown() {
    if (originalDllPath != null) {
      System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, originalDllPath);
    } else {
      System.getProperties().remove(NTEventLogAppender.DLL_PATH_PROPERTY);
    }
    LogManager.shutdown();
  }

  /**
   * The library named by the system property is loaded by absolute path
   * and the planted libraries on the library path are never loaded.
   */
  public void testConfiguredDllPathPreventsLibraryPathSearch() {
    assertNotNull(NTEventLogAppender.DLL_PATH_PROPERTY + " must be set",
        originalDllPath);
    File configured = new File(originalDllPath);
    assertTrue(configured.isAbsolute());
    assertTrue(configured.exists());

    String planted = System.mapLibraryName(
        "NTEventLogAppender." + System.getProperty("os.arch"));
    assertTrue(planted + " must be planted on java.library.path",
        isOnLibraryPath(planted));

    if (LOAD_ERROR != null) {
      fail("NTEventLogAppender library was searched on java.library.path: "
          + LOAD_ERROR);
    }
    System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, originalDllPath);
    assertEquals(configured.getAbsolutePath(),
        NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is not set, resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_notSet() {
    System.getProperties().remove(NTEventLogAppender.DLL_PATH_PROPERTY);
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is an empty string, resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_emptyString() {
    System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, "");
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is whitespace only, resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_whitespaceOnly() {
    System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, "   ");
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is a relative path, resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_relativePath() {
    System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, "relative/path/NTEventLogAppender.dll");
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is an absolute path to a file that does not exist,
   * resolveConfiguredDllPath should return null.
   */
  public void testResolveConfiguredDllPath_absoluteButMissing() {
    String missingPath = new File(System.getProperty("java.io.tmpdir"), "no_such_NTEventLogAppender.dll")
        .getAbsolutePath();
    System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, missingPath);
    assertNull(NTEventLogAppender.resolveConfiguredDllPath());
  }

  /**
   * When the system property is an absolute path to an existing file,
   * resolveConfiguredDllPath should return that absolute path.
   */
  public void testResolveConfiguredDllPath_absoluteAndExists() throws IOException {
    File tempFile = File.createTempFile("NTEventLogAppender", ".dll");
    tempFile.deleteOnExit();
    try {
      String absolutePath = tempFile.getAbsolutePath();
      System.setProperty(NTEventLogAppender.DLL_PATH_PROPERTY, absolutePath);
      String result = NTEventLogAppender.resolveConfiguredDllPath();
      assertNotNull(result);
      assertEquals(absolutePath, result);
    } finally {
      tempFile.delete();
    }
  }

  private static boolean isOnLibraryPath(final String fileName) {
    StringTokenizer dirs = new StringTokenizer(
        System.getProperty("java.library.path"), File.pathSeparator);
    while (dirs.hasMoreTokens()) {
      if (new File(dirs.nextToken(), fileName).exists()) {
        return true;
      }
    }
    return false;
  }
}
