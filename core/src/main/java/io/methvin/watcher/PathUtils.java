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

import io.methvin.watcher.hashing.FileHash;
import io.methvin.watcher.hashing.FileHasher;
import io.methvin.watcher.visitor.FileTreeVisitor;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;

public class PathUtils {

  public static FileHash hash(FileHasher fileHasher, Path path) {
    try {
      if (Files.isDirectory(path)) {
        return FileHash.directory();
      } else {
        if (!Files.exists(path)) {
          return null;
        }
        return fileHasher.hash(path);
      }
    } catch (IOException e) {
      return null;
    }
  }

  public static <T> SortedMap<Path, T> subMap(SortedMap<Path, T> pathMap, Path treeRoot) {
    return pathMap.subMap(treeRoot, Paths.get(treeRoot.toString(), "" + Character.MAX_VALUE));
  }

  public static SortedMap<Path, FileHash> createHashCodeMap(
      Path file, FileHasher fileHasher, FileTreeVisitor fileTreeVisitor) throws IOException {
    SortedMap<Path, FileHash> hashes = new ConcurrentSkipListMap<>();
    FileTreeVisitor.Callback addHash =
        path -> {
          FileHash hash = PathUtils.hash(fileHasher, path);
          if (hash != null) hashes.put(path, hash);
        };
    if (fileHasher != null) {
      fileTreeVisitor.recursiveVisitFiles(file, addHash, addHash);
    }
    return hashes;
  }

  public static void initWatcherState(
      List<Path> roots,
      FileHasher fileHasher,
      FileTreeVisitor fileTreeVisitor,
      Map<Path, FileHash> hashes,
      Set<Path> directories)
      throws IOException {
    for (Path root : roots) {
      if (fileHasher == null) {
        fileTreeVisitor.recursiveVisitFiles(root, directories::add, file -> {});
      } else {
        FileTreeVisitor.Callback addHash =
            path -> {
              FileHash hash = PathUtils.hash(fileHasher, path);
              if (hash != null) hashes.put(path, hash);
            };
        fileTreeVisitor.recursiveVisitFiles(
            root,
            dir -> {
              directories.add(dir);
              addHash.call(dir);
            },
            addHash);
      }
    }
  }

  public static Set<Path> recursiveListFiles(FileTreeVisitor fileTreeVisitor, Path file)
      throws IOException {
    if (!Files.exists(file)) {
      return Collections.emptySet();
    }

    final Set<Path> files = new HashSet<>();
    files.add(file);

    fileTreeVisitor.recursiveVisitFiles(file, files::add, files::add);

    return files;
  }

  @SuppressWarnings("unchecked")
  public static <T> WatchEvent<T> cast(WatchEvent<?> event) {
    return (WatchEvent<T>) event;
  }
}
