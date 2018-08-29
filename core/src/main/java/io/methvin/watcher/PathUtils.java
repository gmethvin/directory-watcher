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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
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

  public static Set<Path> createKeyRootsSet() {
    return ConcurrentHashMap.newKeySet();
  }

  public static Map<Path, HashCode> createHashCodeMap(Path file, FileHasher fileHasher) {
    return createHashCodeMap(Collections.singletonList(file), fileHasher);
  }

  public static Map<Path, HashCode> createHashCodeMap(List<Path> files, FileHasher fileHasher) {
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

  public static Set<Path> nonRecursiveListFiles(Path file) {
    Set<Path> files = new HashSet<Path>();
    files.add(file);
    if (file.toFile().isDirectory()) {
      File[] filesInDirectory = file.toFile().listFiles();
      if (filesInDirectory != null) {
        for (File child : filesInDirectory) {
          if (!child.isDirectory()) {
            files.add(child.toPath());
          }
        }
      }
    }
    return files;
  }

  public static Set<Path> recursiveListFiles(Path file) {
    Set<Path> files = new HashSet<Path>();
    files.add(file);
    if (file.toFile().isDirectory()) {
      File[] filesInDirectory = file.toFile().listFiles();
      if (filesInDirectory != null) {
        for (File child : filesInDirectory) {
          files.addAll(recursiveListFiles(child.toPath()));
        }
      }
    }
    return files;
  }

  @SuppressWarnings("unchecked")
  public static <T> WatchEvent<T> cast(WatchEvent<?> event) {
    return (WatchEvent<T>) event;
  }
}
