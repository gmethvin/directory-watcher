# Directory Watcher

[![Travis CI](https://travis-ci.org/gmethvin/directory-watcher.svg?branch=master)](https://travis-ci.org/gmethvin/directory-watcher) [![AppVeyor CI](https://ci.appveyor.com/api/projects/status/j8u639uf2iovtf15/branch/master?svg=true)](https://ci.appveyor.com/project/gmethvin/directory-watcher/branch/master) [![Maven](https://img.shields.io/maven-central/v/io.methvin/directory-watcher.svg)](https://mvnrepository.com/artifact/io.methvin/directory-watcher)

A directory watcher utility for JDK 8+ that aims to provide accurate and efficient recursive watching for Linux, macOS and Windows. In particular, this library provides a JNA-based `WatchService` for Mac OS X to replace the default polling-based JDK implementation.

## Getting started

First add the dependency for your preferred build system.

For sbt:

```scala
libraryDependencies += "io.methvin" % "directory-watcher" % directoryWatcherVersion
```

For maven:

```xml
<dependency>
    <groupId>io.methvin</groupId>
    <artifactId>directory-watcher</artifactId>
    <version>${directoryWatcherVersion}</version>
</dependency>
```

Replace the `directoryWatcherVersion` with the latest version ([![Maven](https://img.shields.io/maven-central/v/io.methvin/directory-watcher.svg)](https://mvnrepository.com/artifact/io.methvin/directory-watcher)), or any older version you wish to use.

## Java API

Use `DirectoryWatcher.create` to create a new watcher, then use either `watch()` to block the current thread while watching or `watchAsync()` to watch in another thread. This will automatically detect Mac OS X and provide a native implementation based on the Carbon File System Events API.

### Java Example

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

## Configuration

By default, DirectoryWatcher will try to prevent duplicate events (e.g. Windows will emit duplicate modify events when a file is changed). This is done by creating a hash for every file encountered and keeping that hash in memory.
This might result in slower performance, because the library has to calculate the hash of the entire file. In addition, some events may not be emitted if DirectoryWatcher encounters a file that is locked by another process while computing the hash.

To disable hashing, you can set `enableHashing` parameter to `false` when calling `DirectoryWatcher.create`
```
DirectoryWatcher watcher = DirectoryWatcher.create(path, listener, false);
```

## better-files integration (Scala)

While the core `directory-watcher` library is Java only, we also provide `better-files` integration, which is the recommended API for Scala 2.12 users. To add the library:

```scala
libraryDependencies += "io.methvin" %% "directory-watcher-better-files" % directoryWatcherVersion
```

The API is the same as in better-files, but using a different abstract class, `RecursiveFileMonitor`.

```scala
import better.files._
import io.methvin.better.files._

val myDir = File("/directory/to/watch/")
val watcher = new RecursiveFileMonitor(myDir) {
  override def onCreate(file: File, count: Int) = println(s"$file got created")
  override def onModify(file: File, count: Int) = println(s"$file got modified $count times")
  override def onDelete(file: File, count: Int) = println(s"$file got deleted")
}

import scala.concurrent.ExecutionContext.Implicits.global
watcher.start()
```
It also supports overriding `onEvent`, for example:
```scala
import java.nio.file.{Path, StandardWatchEventKinds => EventType, WatchEvent}

val watcher = new RecursiveFileMonitor(myDir) {
  override def onEvent(eventType: WatchEvent.Kind[Path], file: File, count: Int) = eventType match {
    case EventType.ENTRY_CREATE => println(s"$file got created")
    case EventType.ENTRY_MODIFY => println(s"$file got modified $count")
    case EventType.ENTRY_DELETE => println(s"$file got deleted")
  }
}
```

Note that unlike the better-files `FileMonitor` implementation, this implementation only supports fully recursive watching.

## Implementation differences from standard WatchService

The Mac OS X implementation returns the full absolute path of the file in its change notifications, so the returned path does not need to be resolved against the `WatchKey`. This implementation also only watches recursively, so be aware of that if you choose to use it in another context.

On platforms that support it (Windows), the `DirectoryWatcher` utility and better-files watcher use `ExtendedWatchEventModifier.FILE_TREE` to watch recursively. On other platforms (e.g. Linux) it will watch the current directory and register a new `WatchKey` for subdirectories as they are added.

In addition to forwarding events from the underlying `WatchService` implementation, `DirectoryWatcher` also hashes files to determine if changes actually occurred. This tends to reduce the likelihood of duplicate or useless events and helps provide a more consistent experience across platforms.

## Credits

Large parts of the Java directory-watcher code, particularly the `MacOSXListeningWatchService` implementation, are taken from https://github.com/takari/directory-watcher/, which is also licensed under the Apache 2 license.

## License

This library is licensed under the Apache 2 license. See LICENSE for more information.
