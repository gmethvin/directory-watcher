/**
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
package io.takari.watcher;

import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.Watchable;

import io.takari.watchservice.MacOSXListeningWatchService;
import io.takari.watchservice.WatchablePath;

public class DirectoryWatcher {

  private final WatchService watchService;
  private final DirectoryChangeListener listener;
  private final Path directory;
  private final boolean isMac;
  private DirectoryWatcherJdk jdkWatcher;

  public DirectoryWatcher(Path directory, Watchable directoryToMonitor, WatchService watchService, DirectoryChangeListener listener, boolean isMac) throws IOException {
    this.directory = directory;
    this.watchService = watchService;
    this.listener = listener;
    this.isMac = isMac;

    if (isMac) {
      register(directoryToMonitor);
    } else {
      jdkWatcher = new DirectoryWatcherJdk(directory, listener);
    }
  }

  public void watch() throws IOException {
    if (isMac) {
      processEventsMac();
    } else {
      jdkWatcher.processEventsJdk();
    }
  }

  public void processEventsMac() throws IOException {
    for (;;) {
      if (listener.stopWatching()) {
        return;
      }
      // wait for key to be signaled
      WatchKey key;
      try {
        key = watchService.take();
      } catch (InterruptedException x) {
        return;
      }
      for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == OVERFLOW) {
          continue;
        }
        //
        // The filename is the context of the event.
        //
        @SuppressWarnings({"unchecked"})
        WatchEvent<Path> ev = (WatchEvent<Path>) event;
        Path file = ev.context();
        if (file.toFile().isDirectory()) {
          continue;
        }
        if (!file.isAbsolute()) {
          file = directory.resolve(file);
        }
        if (kind == ENTRY_DELETE) {
          listener.onDelete(file);
        }
        if (kind == ENTRY_MODIFY) {
          listener.onModify(file);
        }
        if (kind == ENTRY_CREATE) {
          listener.onCreate(file);
        }
      }
      // Reset the key -- this step is critical to receive further watch events.
      boolean valid = key.reset();
      if (!valid) {
        break;
      }
    }
  }

  protected boolean stopWatching() {
    return false;
  }

  public DirectoryChangeListener getListener() {
    return listener;
  }

  public void close() throws IOException {
    
    watchService.close();
  }

  private void register(Watchable dir) throws IOException {
    dir.register(watchService, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private Path directory;
    private DirectoryChangeListener listener;

    public Builder directory(Path directory) {
      this.directory = directory;
      return this;
    }

    public Builder listener(DirectoryChangeListener listener) {
      this.listener = listener;
      return this;
    }

    public DirectoryWatcher build() throws IOException {
      String os = System.getProperty("os.name").toLowerCase();
      if (os.contains("mac")) {
        return new DirectoryWatcher(directory, new WatchablePath(directory), new MacOSXListeningWatchService(), listener, true);
      } else {
        return new DirectoryWatcher(directory, directory, FileSystems.getDefault().newWatchService(), listener, false);
      }
    }
  }
}
