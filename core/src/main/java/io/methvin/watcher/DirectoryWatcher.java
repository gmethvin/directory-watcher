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

import com.sun.nio.file.ExtendedWatchEventModifier;
import io.methvin.watcher.DirectoryChangeEvent.EventType;
import io.methvin.watcher.hashing.FileHasher;
import io.methvin.watcher.hashing.HashCode;
import io.methvin.watchservice.MacOSXListeningWatchService;
import io.methvin.watchservice.WatchablePath;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.*;

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

    public DirectoryWatcher build() throws IOException {
      if (watchService == null) {
        osDefaultWatchService();
      }
      if (logger == null) {
        staticLogger();
      }
      return new DirectoryWatcher(paths, listener, watchService, fileHasher, logger);
    }

    private Builder osDefaultWatchService() throws IOException {
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
  private final Map<Path, Path> registeredPathToRootPath;
  private final boolean isMac;
  private final DirectoryChangeListener listener;
  private final SortedMap<Path, HashCode> pathHashes;
  private final Set<Path> directories;
  private final Map<WatchKey, Path> keyRoots;

  // set to null until we check if FILE_TREE is supported
  private Boolean fileTreeSupported = null;
  private FileHasher fileHasher;

  private volatile boolean closed;

  public DirectoryWatcher(
      List<Path> paths,
      DirectoryChangeListener listener,
      WatchService watchService,
      FileHasher fileHasher,
      Logger logger)
      throws IOException {
    this.closed = false;
    this.registeredPathToRootPath = new HashMap<>();
    this.listener = listener;
    this.watchService = watchService;
    this.isMac = watchService instanceof MacOSXListeningWatchService;
    this.pathHashes = new ConcurrentSkipListMap<>();
    this.directories = Collections.newSetFromMap(new ConcurrentHashMap<Path, Boolean>());
    this.keyRoots = new ConcurrentHashMap<>();
    this.fileHasher = fileHasher;
    this.logger = logger;

    PathUtils.initWatcherState(paths, fileHasher, pathHashes, directories);

    for (Path path : paths) {
      registerAll(path, path);
    }
  }

  /**
   * Asynchronously watch the directories using {@code ForkJoinPool.commonPool()} as the executor
   */
  public CompletableFuture<Void> watchAsync() {
    return watchAsync(ForkJoinPool.commonPool());
  }

  /**
   * Asynchronously watch the directories.
   *
   * @param executor the executor to use to watch asynchronously
   */
  public CompletableFuture<Void> watchAsync(Executor executor) {
    return CompletableFuture.supplyAsync(
        () -> {
          watch();
          return null;
        },
        executor);
  }

  /**
   * Watch the directories. Block until either the listener stops watching or the DirectoryWatcher
   * is closed.
   */
  public void watch() {
    while (listener.isWatching()) {
      // wait for key to be signalled
      WatchKey key;
      try {
        key = watchService.take();
      } catch (InterruptedException x) {
        return;
      }
      for (WatchEvent<?> event : key.pollEvents()) {
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
            onEvent(EventType.OVERFLOW, childPath, count, rootPath, false);
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
                PathUtils.recursiveVisitFiles(
                    childPath,
                    dir -> notifyCreateEvent(dir, count, rootPath, true),
                    file -> notifyCreateEvent(file, count, rootPath, false));
              }
            }
            notifyCreateEvent(childPath, count, rootPath, isDirectory);
          } else if (kind == ENTRY_MODIFY) {
            boolean isDirectory = directories.contains(childPath);
            if (fileHasher == null) {
              onEvent(EventType.MODIFY, childPath, count, rootPath, isDirectory);
            } else {
              /*
               * Note that existingHash may be null due to the file being created before we
               * start listening It's important we don't discard the event in this case
               */
              HashCode existingHash = pathHashes.get(childPath);

              /*
               * newHash can be null when using File#delete() on windows - it generates MODIFY
               * and DELETE in succession. In this case the MODIFY event can be safely ignored
               */
              HashCode newHash = PathUtils.hash(fileHasher, childPath);

              if (newHash != null && !newHash.equals(existingHash)) {
                pathHashes.put(childPath, newHash);
                onEvent(EventType.MODIFY, childPath, count, rootPath, isDirectory);
              } else if (newHash == null) {
                logger.debug(
                    "Failed to hash modified file [{}]. It may have been deleted.", childPath);
              }
            }
          } else if (kind == ENTRY_DELETE) {
            if (fileHasher == null) {
              boolean isDirectory = directories.remove(childPath);
              // hashing is disabled, so just notify on the path we got the event for
              onEvent(EventType.DELETE, childPath, count, rootPath, isDirectory);
            } else {
              // hashing is enabled, so delete the hashes
              Set<Path> subtreePaths = PathUtils.subMap(pathHashes, childPath).keySet();
              for (Path path : subtreePaths) {
                boolean isDirectory = directories.remove(path);
                onEvent(EventType.DELETE, path, count, rootPath, isDirectory);
              }
              // this will remove from the original map
              subtreePaths.clear();
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
      EventType eventType, Path childPath, int count, Path rootPath, boolean isDirectory)
      throws IOException {
    logger.debug("-> {} [{}] (isDirectory: {})", eventType, childPath, isDirectory);
    listener.onEvent(new DirectoryChangeEvent(eventType, childPath, count, rootPath, isDirectory));
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
      PathUtils.recursiveVisitFiles(start, dir -> register(dir, false, context), file -> {});
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

  private void notifyCreateEvent(Path path, int count, Path context, boolean isDirectory)
      throws IOException {
    if (fileHasher != null) {
      HashCode newHash = PathUtils.hash(fileHasher, path);
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
    onEvent(EventType.CREATE, path, count, context, isDirectory);
  }
}
