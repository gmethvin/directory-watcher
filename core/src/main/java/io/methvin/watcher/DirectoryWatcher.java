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

package io.methvin.watcher;

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

import com.sun.nio.file.ExtendedWatchEventModifier;
import io.methvin.watcher.DirectoryChangeEvent.EventType;
import io.methvin.watcher.hashing.FileHash;
import io.methvin.watcher.hashing.FileHasher;
import io.methvin.watcher.visitor.FileTreeVisitor;
import io.methvin.watchservice.MacOSXListeningWatchService;
import io.methvin.watchservice.WatchablePath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DirectoryWatcher {

  /**
   * A builder for a {@link DirectoryWatcher}. Use {@code DirectoryWatcher.builder()} to get a new
   * instance.
   */
  public static final class Builder {
    private List<Path> paths = Collections.emptyList();
    private DirectoryChangeListener listener = (event -> {});
    private Logger logger = null;
    private FileHasher fileHasher = FileHasher.DEFAULT_FILE_HASHER;
    private WatchService watchService = null;
    private FileTreeVisitor fileTreeVisitor = null;

    private Builder() {}

    /** Set multiple paths to watch. */
    public Builder paths(List<Path> paths) {
      this.paths = paths;
      return this;
    }

    /** Set a single path to watch. */
    public Builder path(Path path) {
      return paths(Collections.singletonList(path));
    }

    /** Set a listener that will be called when a directory change event occurs. */
    public Builder listener(DirectoryChangeListener listener) {
      this.listener = listener;
      return this;
    }

    /**
     * Set a {@link WatchService} implementation that will be used by the watcher.
     *
     * <p>By default, this detects your OS and either uses the native JVM watcher or the macOS
     * watcher.
     */
    public Builder watchService(WatchService watchService) {
      this.watchService = watchService;
      return this;
    }

    /**
     * Set a logger to be used by the watcher. This defaults to {@code
     * LoggerFactory.getLogger(DirectoryWatcher.class)}
     */
    public Builder logger(Logger logger) {
      this.logger = logger;
      return this;
    }

    /**
     * Defines whether file hashing should be used to catch duplicate events. Defaults to {@code
     * true}.
     */
    public Builder fileHashing(boolean enabled) {
      this.fileHasher = enabled ? FileHasher.DEFAULT_FILE_HASHER : null;
      return this;
    }

    /**
     * Defines the file hasher to be used by the watcher.
     *
     * <p>Note: will implicitly enable file hashing. Setting to null is equivalent to {@code
     * fileHashing(false)}
     */
    public Builder fileHasher(FileHasher fileHasher) {
      this.fileHasher = fileHasher;
      return this;
    }

    /** Defines the file tree visitor to be used by the watcher. */
    public Builder fileTreeVisitor(FileTreeVisitor fileTreeVisitor) {
      this.fileTreeVisitor = fileTreeVisitor;
      return this;
    }

    public DirectoryWatcher build() throws IOException {
      if (fileTreeVisitor == null) {
        fileTreeVisitor = FileTreeVisitor.DEFAULT_FILE_TREE_VISITOR;
      }
      if (watchService == null) {
        osDefaultWatchService(fileTreeVisitor);
      }
      if (logger == null) {
        staticLogger();
      }
      return new DirectoryWatcher(
          paths, listener, watchService, fileHasher, fileTreeVisitor, logger);
    }

    private Builder osDefaultWatchService(FileTreeVisitor fileTreeVisitor) throws IOException {
      boolean isMac = System.getProperty("os.name").toLowerCase().contains("mac");
      if (isMac) {
        return watchService(
            new MacOSXListeningWatchService(
                new MacOSXListeningWatchService.Config() {
                  @Override
                  public FileHasher fileHasher() {
                    /**
                     * Always return null here. When MacOSXListeningWatchService is used with
                     * DirectoryWatcher, then the hashing should happen within DirectoryWatcher. If
                     * users wish to override this then they must instantiate
                     * MacOSXListeningWatchService and pass it to DirectoryWatcher.
                     */
                    return null;
                  }

                  @Override
                  public FileTreeVisitor fileTreeVisitor() {
                    return fileTreeVisitor;
                  }
                }));
      } else {
        return watchService(FileSystems.getDefault().newWatchService());
      }
    }

    private Builder staticLogger() {
      return logger(LoggerFactory.getLogger(DirectoryWatcher.class));
    }
  }

  /** Get a new builder for a {@link DirectoryWatcher}. */
  public static Builder builder() {
    return new Builder();
  }

  private final Logger logger;
  private final WatchService watchService;
  private final List<Path> paths;
  private final boolean isMac;
  private final DirectoryChangeListener listener;
  private final FileHasher fileHasher;
  private final FileTreeVisitor fileTreeVisitor;

  private final Map<Path, Path> registeredPathToRootPath = new HashMap<>();;
  private final SortedMap<Path, FileHash> pathHashes = new ConcurrentSkipListMap<>();
  private final Set<Path> directories =
      Collections.newSetFromMap(new ConcurrentHashMap<Path, Boolean>());
  private final Map<WatchKey, Path> keyRoots = new ConcurrentHashMap<>();

  private volatile boolean closed = false;

  // set to null until we check if FILE_TREE is supported
  private Boolean fileTreeSupported = null;

  public DirectoryWatcher(
      List<Path> paths,
      DirectoryChangeListener listener,
      WatchService watchService,
      FileHasher fileHasher,
      FileTreeVisitor fileTreeVisitor,
      Logger logger) {
    this.paths = paths.stream().map(p -> p.toAbsolutePath()).collect(Collectors.toList());
    this.listener = listener;
    this.watchService = watchService;
    this.isMac = watchService instanceof MacOSXListeningWatchService;
    this.fileHasher = fileHasher;
    this.fileTreeVisitor = fileTreeVisitor;
    this.logger = logger;
  }

  /**
   * Asynchronously watch the directories using {@code ForkJoinPool.commonPool()} as the executor
   */
  public CompletableFuture<Void> watchAsync() {
    return watchAsync(ForkJoinPool.commonPool());
  }

  /**
   * Start watching for changes asynchronously.
   *
   * <p>The future completes when the listener stops listening or the watcher is closed.
   *
   * <p>This method will block until the watcher is initialized and successfully watching.
   *
   * @param executor the executor to use to watch asynchronously
   */
  public CompletableFuture<Void> watchAsync(Executor executor) {
    try {
      registerPaths();
      return CompletableFuture.supplyAsync(
          () -> {
            runEventLoop();
            return null;
          },
          executor);
    } catch (Throwable t) {
      CompletableFuture<Void> f = new CompletableFuture<>();
      f.completeExceptionally(t);
      return f;
    }
  }

  /**
   * Watch for changes; block until the listener stops listening or the watcher is closed.
   *
   * @throws IllegalStateException if the directory watcher is closed when watch() is called.
   */
  public void watch() {
    registerPaths();
    runEventLoop();
  }

  public Map<Path, FileHash> pathHashes() {
    return Collections.unmodifiableMap(pathHashes);
  }

  private void registerPaths() {
    try {
      PathUtils.initWatcherState(paths, fileHasher, fileTreeVisitor, pathHashes, directories);

      for (Path path : paths) {
        registerAll(path, path);
      }
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void runEventLoop() {
    if (closed) {
      throw new IllegalStateException("watcher already closed");
    }
    int eventCount = 0;
    while (listener.isWatching()) {
      // wait for key to be signalled
      WatchKey key;
      try {
        key = watchService.poll();
        if (key == null) {
          listener.onIdle(eventCount);
          key = watchService.take();
        }
      } catch (InterruptedException | ClosedWatchServiceException e) {
        return;
      }
      for (WatchEvent<?> event : key.pollEvents()) {
        eventCount++;
        try {
          WatchEvent.Kind<?> kind = event.kind();
          // Context for directory entry event is the file name of entry
          WatchEvent<Path> ev = PathUtils.cast(event);
          int count = ev.count();
          Path eventPath = ev.context();
          if (!keyRoots.containsKey(key)) {
            throw new IllegalStateException(
                "WatchService returned key [" + key + "] but it was not found in keyRoots!");
          }
          Path registeredPath = keyRoots.get(key);
          Path rootPath = registeredPathToRootPath.get(registeredPath);
          Path childPath = eventPath == null ? null : keyRoots.get(key).resolve(eventPath);
          logger.debug("{} [{}]", kind, childPath);
          /*
           * If a directory is created, and we're watching recursively, then register it
           * and its sub-directories.
           */
          if (kind == OVERFLOW) {
            onEvent(EventType.OVERFLOW, false, childPath, count, rootPath);
          } else if (eventPath == null) {
            throw new IllegalStateException("WatchService returned a null path for " + kind.name());
          } else if (kind == ENTRY_CREATE) {
            boolean isDirectory = Files.isDirectory(childPath, NOFOLLOW_LINKS);
            if (isDirectory) {
              if (!Boolean.TRUE.equals(fileTreeSupported)) {
                registerAll(childPath, rootPath);
              }
              /*
               * Our custom Mac service sends subdirectory changes but the Windows/Linux do
               * not. Walk the file tree to make sure we send create events for any files that
               * were created.
               */
              if (!isMac) {
                fileTreeVisitor.recursiveVisitFiles(
                    childPath,
                    dir -> notifyCreateEvent(true, dir, count, rootPath),
                    file -> notifyCreateEvent(false, file, count, rootPath));
              }
            }
            notifyCreateEvent(isDirectory, childPath, count, rootPath);
          } else if (kind == ENTRY_MODIFY) {
            boolean isDirectory = directories.contains(childPath);

            if (fileHasher == null) {
              onEvent(EventType.MODIFY, isDirectory, childPath, count, rootPath);
            } else {
              /*
               * Note that existingHash may be null due to the file being created before we
               * start listening It's important we don't discard the event in this case
               */
              FileHash existingHash = pathHashes.get(childPath);

              /*
               * newHash can be null when using File#delete() on windows - it generates MODIFY
               * and DELETE in succession. In this case the MODIFY event can be safely ignored
               */
              FileHash newHash = PathUtils.hash(fileHasher, childPath);

              if (newHash != null && !newHash.equals(existingHash)) {
                pathHashes.put(childPath, newHash);
                onEvent(EventType.MODIFY, isDirectory, childPath, count, rootPath);
              } else if (newHash == null) {
                logger.debug(
                    "Failed to hash modified file [{}]. It may have been deleted.", childPath);
              }
            }
          } else if (kind == ENTRY_DELETE) {
            if (fileHasher == null) {
              boolean isDirectory = directories.remove(childPath);
              // hashing is disabled, so just notify on the path we got the event for
              onEvent(EventType.DELETE, isDirectory, childPath, count, rootPath);
            } else {
              // hashing is enabled, so delete the hashes
              for (Path path : PathUtils.subtreePaths(pathHashes, childPath)) {
                boolean isDirectory = directories.remove(path);
                pathHashes.remove(path);
                onEvent(EventType.DELETE, isDirectory, path, count, rootPath);
              }
            }
          }
        } catch (Exception e) {
          logger.debug("DirectoryWatcher got an exception while watching!", e);
          listener.onException(e);
        }
      }
      boolean valid = key.reset();
      if (!valid) {
        logger.debug("WatchKey for [{}] no longer valid; removing.", key.watchable());
        // remove the key from the keyRoots
        Path registeredPath = keyRoots.remove(key);

        // Also remove from the registeredPathToRootPath maps
        registeredPathToRootPath.remove(registeredPath);

        // if there are no more keys left to watch, we can break out
        if (keyRoots.isEmpty()) {
          logger.debug("No more directories left to watch; terminating watcher.");
          break;
        }
      }
    }
    try {
      close();
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  private void onEvent(
      EventType eventType, boolean isDirectory, Path childPath, int count, Path rootPath)
      throws IOException {
    logger.debug("-> {} [{}] (isDirectory: {})", eventType, childPath, isDirectory);
    FileHash hash = pathHashes.get(childPath);
    listener.onEvent(
        new DirectoryChangeEvent(eventType, isDirectory, childPath, hash, count, rootPath));
  }

  public DirectoryChangeListener getListener() {
    return listener;
  }

  public void close() throws IOException {
    watchService.close();
    closed = true;
  }

  public boolean isClosed() {
    return closed;
  }

  private void registerAll(final Path start, final Path context) throws IOException {
    if (!Boolean.FALSE.equals(fileTreeSupported)) {
      // Try using FILE_TREE modifier since we aren't certain that it's unsupported
      try {
        register(start, true, context);
        // Assume FILE_TREE is supported
        fileTreeSupported = true;
      } catch (UnsupportedOperationException e) {
        // UnsupportedOperationException should only happen if FILE_TREE is unsupported
        logger.debug("Assuming ExtendedWatchEventModifier.FILE_TREE is not supported", e);
        fileTreeSupported = false;
        // If we failed to use the FILE_TREE modifier, try again without
        registerAll(start, context);
      }
    } else {
      // Since FILE_TREE is unsupported, register root directory and sub-directories
      fileTreeVisitor.recursiveVisitFiles(start, dir -> register(dir, false, context), file -> {});
    }
  }

  // Internal method to be used by registerAll
  private void register(Path directory, boolean useFileTreeModifier, Path context)
      throws IOException {
    logger.debug("Registering [{}].", directory);
    Watchable watchable = isMac ? new WatchablePath(directory) : directory;
    WatchEvent.Modifier[] modifiers =
        useFileTreeModifier
            ? new WatchEvent.Modifier[] {ExtendedWatchEventModifier.FILE_TREE}
            : new WatchEvent.Modifier[] {};
    WatchEvent.Kind<?>[] kinds =
        new WatchEvent.Kind<?>[] {ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY};
    WatchKey watchKey = watchable.register(watchService, kinds, modifiers);
    keyRoots.put(watchKey, directory);
    registeredPathToRootPath.put(directory, context);
  }

  private void notifyCreateEvent(boolean isDirectory, Path path, int count, Path rootPath)
      throws IOException {
    if (fileHasher != null) {
      FileHash newHash = PathUtils.hash(fileHasher, path);
      if (newHash == null) {
        // Hashing could fail for locked files on Windows.
        // Skip notification only if we confirm the file does not exist.
        if (Files.notExists(path)) {
          logger.debug("Failed to hash created file [{}]. It may have been deleted.", path);
          // Skip notifying the event.
          return;
        } else {
          // Just warn here and continue to notify the event.
          logger.debug("Failed to hash created file [{}]. It may be locked.", path);
        }
      } else if (pathHashes.put(path, newHash) != null) {
        // Skip notifying the event if we've already seen the path.
        logger.debug("Skipping create event for path [{}]. Path already hashed.", path);
        return;
      }
    }
    if (isDirectory) {
      directories.add(path);
    }
    onEvent(EventType.CREATE, isDirectory, path, count, rootPath);
  }
}
