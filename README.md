# DirectoryWatcher

A recursive directory watcher with a native OSX implementation of the WatchService.

```
package io.takari.watchservice;

import java.io.IOException;
import java.nio.file.Path;

import io.takari.watcher.DirectoryChangeListener;
import io.takari.watcher.DirectoryWatcher;

public class MyDirectoryWatcher {

  private final Path pathToWatch;

  public MyDirectoryWatcher(Path pathToWatch) {
    this.pathToWatch = pathToWatch;
  }

  public void watch() throws Exception {
    DirectoryWatcher watcher = DirectoryWatcher.builder().directory(pathToWatch).listener(new DirectoryChangeListener() {
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
    watcher.processEvents();
  }
}
```
