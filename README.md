# Directory Watcher

A recursive directory watcher utility for JDK 8+, along with a native OS X implementation of the WatchService.

```java
package com.example;

import java.io.IOException;
import java.nio.file.Path;

import io.methvin.watcher.DirectoryChangeListener;
import io.methvin.watcher.DirectoryWatcher;

import static io.methvin.watcher.DirectoryChangeEvent.EventType.*;

public class DirectoryWatchingUtility {

  private final Path pathToWatch;
  private final DirectoryWatcher watcher;

  public DirectoryWatchingUtility(Path directoryToWatch) {
    this.directoryToWatch = directoryToWatch;
    this.watcher = DirectoryWatcher.create(directoryToWatch, event -> {
      switch (event.eventType()) {
        case CREATE: /* file created */; break;
        case MODIFY: /* file modified */; break;
        case DELETE: /* file deleted */; break;
      }
    });
  }

  public void stopWatching() {
    watcher.close();
  }

  public CompletableFuture<Void> watch() {
    // you can also use watcher.watch() to block the current thread
    return watcher.watchAsync();
  }
}
```

## Implementation differences

The Mac OS X implementation returns the full absolute path of the file in its change notifications, so the returned path does not need to be resolved against the `WatchKey`. The `DirectoryWatcher` abstracts away the details of that though.

## Credits

Large parts of this code are taken from https://github.com/takari/directory-watcher/, which is also licensed under the Apache 2 license. The library has been updated to make it more idiomatic Java 8 style and to remove some of the special cases.
