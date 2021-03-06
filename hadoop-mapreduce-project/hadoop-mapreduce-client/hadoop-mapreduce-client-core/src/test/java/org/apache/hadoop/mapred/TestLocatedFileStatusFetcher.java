/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.hadoop.mapred;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FileUtil;
import org.apache.hadoop.fs.LocalFileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.PathFilter;
import org.apache.hadoop.test.GenericTestUtils;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

public class TestLocatedFileStatusFetcher {

  private Configuration conf;
  private FileSystem fileSys;
  private boolean mkdirs;
  private File dir = GenericTestUtils.getTestDir("test-localfs");
  private static long TIME_FOR_SLEEP = 10000;

  @Before
  public void setup() throws Exception {
    conf = new Configuration(false);
    conf.set("fs.file.impl", MockFileSystem.class.getName());
    fileSys = FileSystem.getLocal(conf);
  }

  @After
  public void after() throws IOException {
    if (mkdirs) {
      FileUtil.fullyDelete(dir);
    }
  }

  @Test
  public void testExecutorsShutDown() throws Exception {
    Path scanPath = new Path(dir.getAbsolutePath());
    mkdirs = fileSys.mkdirs(scanPath);
    Path[] dirs = new Path[] {scanPath};
    final LocatedFileStatusFetcher fetcher = new LocatedFileStatusFetcher(conf, dirs, true,
        new PathFilter() {
          @Override
          public boolean accept(Path path) {
            return true;
          }
        }, true);

    Thread t = new Thread() {
      @Override
      public void run() {
        try {
          fetcher.getFileStatuses();
        } catch (Exception e) {
          // This should interrupt condition.await()
          Assert.assertTrue(e instanceof InterruptedException);
        }
      }
    };

    t.start();
    Thread.sleep(TIME_FOR_SLEEP / 2);

    t.interrupt();
    t.join();
    // Check the status for executor service
    Assert.assertTrue(fetcher.getListeningExecutorService().isShutdown());
  }

  static class MockFileSystem extends LocalFileSystem {
    @Override
    public FileStatus[] globStatus(Path pathPattern, PathFilter filter)
        throws IOException {
      try {
        Thread.sleep(TIME_FOR_SLEEP);
      } catch (Exception e) {
        // Ignore this exception as fs waiting on the result
        // might not be interrupted immediately
      }
      return super.globStatus(pathPattern, filter);
    }

  }

}
