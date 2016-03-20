# Directory Watcher

A recursive directory watcher with a native OSX implementation of the WatchService.

``` java
package io.takari.watchservice;

import java.io.IOException;
import java.nio.file.Path;

import io.takari.watcher.DirectoryChangeListener;
import io.takari.watcher.DirectoryWatcher;

public class DirectoryWatchingUtility {

  private final Path pathToWatch;

  public DirectoryWatchingUtility(Path directoryToWatch) {
    this.directoryToWatch = directoryToWatch;
  }

  public void watch() throws Exception {
    DirectoryWatcher watcher = DirectoryWatcher.builder()
      .directory(directoryToWatch)
      .listener(new DirectoryChangeListener() {
      
      @Override
      public void onCreate(Path path) throws IOException {
        // process create
      }

      @Override
      public void onModify(Path path) throws IOException {
        // process modifiy
      }

      @Override
      public void onDelete(Path path) throws IOException {
        // process delete
      }
    }).build();
    watcher.watch();
  }
}
```

# Implementation Differences

The implementations of the OSX and JDK version of the `WatchService`, and the resulting event loop processing, are slightly different. The OSX implementation uses the Carbon File System Events API and only one `WatchKey` is created for a whole directory structure being watched and the path returned in the `WatchEvent` context is fully resolved. In the JDK implementation the path returned by the `WatchEvent` must be resolved against the `WatchKey` taken from the `WatchService`. It's likely not hard to make the OSX implementation exhibit the same behaviour so that the event processing loop can be identical. Right now you'll see there is an OSX specific event processing loop and a JDK specific event processing loop.

# Other Known Implementations

- https://github.com/longkerdandy/jpathwatch-osgi
- https://github.com/levelsbeyond/jpoller
- https://github.com/aerofs/jnotify
- https://github.com/java-native-access/jna/blob/master/contrib/platform/src/com/sun/jna/platform/win32/W32FileMonitor.java
- Eclipse must have one