package io.takari.watcher;

import static io.takari.watcher.PathUtils.cast;
import static io.takari.watcher.PathUtils.createHashCodeMap;
import static io.takari.watcher.PathUtils.createKeyRootsMap;
import static io.takari.watcher.PathUtils.hash;
import static java.nio.file.LinkOption.NOFOLLOW_LINKS;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_DELETE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import static java.nio.file.StandardWatchEventKinds.OVERFLOW;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

import com.google.common.hash.HashCode;

class DirectoryWatcherJdk {

  private final WatchService watcher;
  private final DirectoryChangeListener listener;
  private final Path directory;
  private final Map<Path, HashCode> pathHashes;
  private final Map<WatchKey, Path> keyRoots;

  public DirectoryWatcherJdk(Path directory, DirectoryChangeListener listener) throws IOException {
    this.directory = directory;
    this.watcher = FileSystems.getDefault().newWatchService();
    this.listener = listener;
    this.pathHashes = createHashCodeMap(directory);
    this.keyRoots = createKeyRootsMap();
    registerAll(directory);
  }

  private void register(Path directory) throws IOException {
    keyRoots.put(directory.register(watcher, new WatchEvent.Kind[] {ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY}), directory);
  }

  private void registerAll(final Path start) throws IOException {
    // register directory and sub-directories
    Files.walkFileTree(start, new SimpleFileVisitor<Path>() {
      @Override
      public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
        throws IOException {
        register(dir);
        return FileVisitResult.CONTINUE;
      }
    });
  }

  public void processEventsJdk() throws IOException {
    for (;;) {
      if (listener.stopWatching()) {
        return;
      }
      // wait for key to be signalled
      WatchKey key;
      try {
        key = watcher.take();
      } catch (InterruptedException x) {
        return;
      }      
      for (WatchEvent<?> event : key.pollEvents()) {
        WatchEvent.Kind<?> kind = event.kind();
        if (kind == OVERFLOW) {
          continue;
        }
        // Context for directory entry event is the file name of entry
        WatchEvent<Path> ev = cast(event);
        Path name = ev.context();
        Path child = keyRoots.containsKey(key) ?  keyRoots.get(key).resolve(name) : directory.resolve(name);
        // if directory is created, and watching recursively, then register it and its sub-directories
        if (kind == ENTRY_CREATE) {
          if (Files.isDirectory(child, NOFOLLOW_LINKS)) {
            registerAll(child);
          } else {            
            pathHashes.put(child, hash(child));
            listener.onCreate(child);
          }
        } else if (kind == ENTRY_MODIFY) {
          HashCode existingHash = pathHashes.get(child);
          HashCode newHash = hash(child);
          // newHash can be null when using File#delete() on windows - it generates MODIFY and DELETE in succession
          // in this case the MODIFY event can be safely ignored
          if (existingHash != null && newHash != null && !existingHash.equals(newHash)) {
            pathHashes.put(child, newHash);
            listener.onModify(child);
          }
        } else if (kind == ENTRY_DELETE) {
          pathHashes.remove(child);
          listener.onDelete(child);
        }
      }
      boolean valid = key.reset();
      if (!valid) {
        break;
      }
    }
  }
}
