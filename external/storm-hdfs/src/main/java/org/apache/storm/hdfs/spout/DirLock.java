/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.storm.hdfs.spout;

import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hdfs.protocol.AlreadyBeingCreatedException;
import org.apache.hadoop.ipc.RemoteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import org.apache.hadoop.fs.FileAlreadyExistsException;

public class DirLock {
  private FileSystem fs;
  private final Path lockFile;
  public static final String DIR_LOCK_FILE = "DIRLOCK";
  private static final Logger log = LoggerFactory.getLogger(DirLock.class);
  private DirLock(FileSystem fs, Path lockFile) throws IOException {
    if( fs.isDirectory(lockFile) )
      throw new IllegalArgumentException(lockFile.toString() + " is not a directory");
    this.fs = fs;
    this.lockFile = lockFile;
  }

  /** Returns null if somebody else has a lock
   *
   * @param fs
   * @param dir  the dir on which to get a lock
   * @return The lock object if it the lock was acquired. Returns null if the dir is already locked.
   * @throws IOException if there were errors
   */
  public static DirLock tryLock(FileSystem fs, Path dir) throws IOException {
    Path lockFile = new Path(dir.toString() + Path.SEPARATOR_CHAR + DIR_LOCK_FILE );
    try {
      FSDataOutputStream os = fs.create(lockFile, false);
      if (log.isInfoEnabled()) {
        log.info("Thread ({}) acquired lock on dir {}", threadInfo(), dir);
      }
      os.close();
      return new DirLock(fs, lockFile);
    } catch (FileAlreadyExistsException e) {
      log.info("Thread ({}) cannot lock dir {} as its already locked.", threadInfo(), dir);
      return null;
    } catch (RemoteException e) {
      if( e.getClassName().contentEquals(AlreadyBeingCreatedException.class.getName()) ) {
        log.info("Thread ({}) cannot lock dir {} as its already locked.", threadInfo(), dir);
        return null;
      } else { // unexpected error
        log.error("Error when acquiring lock on dir " + dir, e);
        throw e;
      }
    }
  }

  private static String threadInfo () {
    return "ThdId=" + Thread.currentThread().getId() + ", ThdName=" + Thread.currentThread().getName();
  }

  /** Release lock on dir by deleting the lock file */
  public void release() throws IOException {
    fs.delete(lockFile, false);
    log.info("Thread {} released dir lock {} ", threadInfo(), lockFile);
  }

  public Path getLockFile() {
    return lockFile;
  }
}
