/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.methvin.watchservice;

import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import io.methvin.watcher.PathUtils;
import io.methvin.watcher.hashing.FileHasher;
import io.methvin.watcher.hashing.HashCode;
import io.methvin.watchservice.jna.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.*;

import static java.nio.file.StandardWatchEventKinds.*;


/**
 * This class contains the bulk of my implementation of the Watch Service API. It hooks into Carbon's
 * File System Events API.
 *
 * @author Steve McLeod
 * @author Greg Methvin
 */
public class MacOSXListeningWatchService extends AbstractWatchService {

  /**
   * Configuration for the watch service.
   */
  public interface Config {

    double DEFAULT_LATENCY = 0.5;
    int DEFAULT_QUEUE_SIZE = 1024;

    /**
     * The maximum number of seconds to wait after hearing about an event
     */
    default double latency() {
      return DEFAULT_LATENCY;
    }

    /**
     * The size of the queue used for each WatchKey
     */
    default int queueSize() {
      return DEFAULT_QUEUE_SIZE;
    }

    /**
     * The file hasher to use to verify files. If null, this will use the default directory-watcher hasher.
     */
    default FileHasher fileHasher() { return null; }
  }

  // need to keep reference to callbacks to prevent garbage collection
  @SuppressWarnings({"MismatchedQueryAndUpdateOfCollection"})
  private final List<CarbonAPI.FSEventStreamCallback> callbackList = new ArrayList<>();
  private final List<CFRunLoopThread> threadList = new ArrayList<>();
  private final Set<Path> pathsWatching = new HashSet<>();

  private final double latency;
  private final int queueSize;
  private final FileHasher fileHasher;

  public MacOSXListeningWatchService(Config config) {
    this.latency = config.latency();
    this.queueSize = config.queueSize();

    // File hasher cannot be null, because this watch service depends on hashes to work properly.
    FileHasher fileHasher = config.fileHasher();
    this.fileHasher = fileHasher == null ? FileHasher.DEFAULT_FILE_HASHER : fileHasher;
  }

  public MacOSXListeningWatchService() {
    this(new Config() {});
  }

  @Override
  public AbstractWatchKey register(WatchablePath watchable, Iterable<? extends WatchEvent.Kind<?>> events) throws IOException {
    checkOpen();
    final MacOSXWatchKey watchKey = new MacOSXWatchKey(this, events, queueSize);
    final Path file = watchable.getFile();
    // if we are already watching a parent of this directory, do nothing.
    for (Path watchedPath: pathsWatching) {
      if (file.startsWith(watchedPath)) return watchKey;
    }
    final Map<Path, HashCode> hashCodeMap = PathUtils.createHashCodeMap(file, fileHasher);
    final String s = file.toFile().getAbsolutePath();
    final Pointer[] values = {CFStringRef.toCFString(s).getPointer()};
    final CFArrayRef pathsToWatch = CarbonAPI.INSTANCE.CFArrayCreate(null, values, CFIndex.valueOf(1), null);
    final long kFSEventStreamEventIdSinceNow = -1; //  this is 0xFFFFFFFFFFFFFFFF
    final int kFSEventStreamCreateFlagNoDefer = 0x00000002;
    final CarbonAPI.FSEventStreamCallback callback = new MacOSXListeningCallback(watchKey, fileHasher, hashCodeMap);
    callbackList.add(callback);
    final FSEventStreamRef stream = CarbonAPI.INSTANCE.FSEventStreamCreate(
      Pointer.NULL,
      callback,
      Pointer.NULL,
      pathsToWatch,
      kFSEventStreamEventIdSinceNow,
      latency,
      kFSEventStreamCreateFlagNoDefer);

    final CFRunLoopThread thread = new CFRunLoopThread(stream, file.toFile());
    thread.setDaemon(true);
    thread.start();
    threadList.add(thread);
    pathsWatching.add(file);
    return watchKey;
  }

  public static class CFRunLoopThread extends Thread {
    private final FSEventStreamRef streamRef;
    private CFRunLoopRef runLoop;

    public CFRunLoopThread(FSEventStreamRef streamRef, File file) {
      super("WatchService for " + file);
      this.streamRef = streamRef;
    }

    @Override
    public void run() {
      runLoop = CarbonAPI.INSTANCE.CFRunLoopGetCurrent();
      final CFStringRef runLoopMode = CFStringRef.toCFString("kCFRunLoopDefaultMode");
      CarbonAPI.INSTANCE.FSEventStreamScheduleWithRunLoop(streamRef, runLoop, runLoopMode);
      CarbonAPI.INSTANCE.FSEventStreamStart(streamRef);
      CarbonAPI.INSTANCE.CFRunLoopRun();
    }

    public CFRunLoopRef getRunLoop() {
      return runLoop;
    }

    public FSEventStreamRef getStreamRef() {
      return streamRef;
    }
  }

  @Override
  public void close() {
    super.close();
    for (CFRunLoopThread thread : threadList) {
      CarbonAPI.INSTANCE.CFRunLoopStop(thread.getRunLoop());
      CarbonAPI.INSTANCE.FSEventStreamStop(thread.getStreamRef());
    }
    threadList.clear();
    callbackList.clear();
    pathsWatching.clear();
  }

  private static class MacOSXListeningCallback implements CarbonAPI.FSEventStreamCallback {
    private final MacOSXWatchKey watchKey;
    private final Map<Path, HashCode> hashCodeMap;
    private final FileHasher fileHasher;

    private MacOSXListeningCallback(MacOSXWatchKey watchKey, FileHasher fileHasher, Map<Path, HashCode> hashCodeMap) {
      this.watchKey = watchKey;
      this.hashCodeMap = hashCodeMap;
      this.fileHasher = fileHasher;
    }

    @Override
    public void invoke(FSEventStreamRef streamRef, Pointer clientCallBackInfo, NativeLong numEvents, Pointer eventPaths, Pointer /* array of unsigned int */ eventFlags,
      /* array of unsigned long */ Pointer eventIds) {
      final int length = numEvents.intValue();

      for (String folderName : eventPaths.getStringArray(0, length)) {
        final Set<Path> filesOnDisk = PathUtils.recursiveListFiles(new File(folderName).toPath());
        /*
         * We collect and process all actions for each category of created, modified and deleted as it appears a first thread
         * can start while a second thread can get through faster. If we do the collection for each category in a second
         * thread can get to the processing of modifications before the first thread is finished processing creates.
         * In this case the modification will not be reported correctly.
         *
         * NOTE: We are now using a hash to determine if a file is different because if modifications happens closely
         * together the last modified time is not granular enough to be seen as a modification. This likely mitigates
         * the issue I originally saw where the ordering was incorrect but I will leave the collection and processing
         * of each category together.
         */

        for (Path file : findCreatedFiles(filesOnDisk)) {
          if (watchKey.isReportCreateEvents()) {
            watchKey.signalEvent(ENTRY_CREATE, file);
          }
        }

        for (Path file : findModifiedFiles(filesOnDisk)) {
          if (watchKey.isReportModifyEvents()) {
            watchKey.signalEvent(ENTRY_MODIFY, file);
          }
        }

        for (Path file : findDeletedFiles(folderName, filesOnDisk)) {
          if (watchKey.isReportDeleteEvents()) {
            watchKey.signalEvent(ENTRY_DELETE, file);
          }
        }
      }
    }

    private List<Path> findModifiedFiles(Set<Path> filesOnDisk) {
      List<Path> modifiedFileList = new ArrayList<Path>();
      for (Path file : filesOnDisk) {
        HashCode storedHashCode = hashCodeMap.get(file);
        HashCode newHashCode = PathUtils.hash(fileHasher, file);
        if (storedHashCode != null && !storedHashCode.equals(newHashCode) && newHashCode != null) {
          modifiedFileList.add(file);
          hashCodeMap.put(file, newHashCode);
        }
      }
      return modifiedFileList;
    }

    private List<Path> findCreatedFiles(Set<Path> filesOnDisk) {
      List<Path> createdFileList = new ArrayList<Path>();
      for (Path file : filesOnDisk) {
        if (!hashCodeMap.containsKey(file)) {
          HashCode hashCode = PathUtils.hash(fileHasher, file);
          if (hashCode != null) {
            createdFileList.add(file);
            hashCodeMap.put(file, hashCode);
          }
        }
      }
      return createdFileList;
    }

    private List<Path> findDeletedFiles(String folderName, Set<Path> filesOnDisk) {
      List<Path> deletedFileList = new ArrayList<Path>();
      for (Path file : hashCodeMap.keySet()) {
        if (file.toFile().getAbsolutePath().startsWith(folderName) && !filesOnDisk.contains(file)) {
          deletedFileList.add(file);
          hashCodeMap.remove(file);
        }
      }
      return deletedFileList;
    }
  }
}
