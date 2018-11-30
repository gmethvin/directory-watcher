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

import io.methvin.watcher.hashing.FileHasher;
import io.methvin.watcher.hashing.HashCode;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PathUtils {

  public static HashCode hash(FileHasher fileHasher, Path path) {
    try {
      if (Files.isDirectory(path)) {
        return HashCode.empty();
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

  public static Map<WatchKey, Path> createKeyRootsMap() {
    return new ConcurrentHashMap<>();
  }

  public static Map<Path, HashCode> createHashCodeMap(Path file, FileHasher fileHasher)
      throws IOException {
    return createHashCodeMap(Collections.singletonList(file), fileHasher);
  }

  public static Map<Path, HashCode> createHashCodeMap(List<Path> files, FileHasher fileHasher)
      throws IOException {
    Map<Path, HashCode> lastModifiedMap = new ConcurrentHashMap<>();
    if (fileHasher != null) {
      for (Path file : files) {
        for (Path child : recursiveListFiles(file)) {
          HashCode hash = PathUtils.hash(fileHasher, child);
          if (hash != null) {
            lastModifiedMap.put(child, hash);
          }
        }
      }
    }
    return lastModifiedMap;
  }

  public static Set<Path> recursiveListFiles(Path file) throws IOException {
    if (!Files.exists(file)) {
      return Collections.emptySet();
    }

    final Set<Path> files = new HashSet<>();
    files.add(file);

    recursiveVisitFiles(file, files::add, files::add);

    return files;
  }

  interface PathCallback {
    void call(Path p) throws IOException;
  }

  public static void recursiveVisitFiles(Path file, PathCallback onDirectory, PathCallback onFile)
      throws IOException {
    Files.walkFileTree(
        file,
        new SimpleFileVisitor<Path>() {
          @Override
          public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
              throws IOException {
            onDirectory.call(dir);
            return FileVisitResult.CONTINUE;
          }

          @Override
          public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
              throws IOException {
            onFile.call(file);
            return FileVisitResult.CONTINUE;
          }
        });
  }

  @SuppressWarnings("unchecked")
  public static <T> WatchEvent<T> cast(WatchEvent<?> event) {
    return (WatchEvent<T>) event;
  }
}
