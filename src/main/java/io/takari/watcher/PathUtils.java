package io.takari.watcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Charsets;
import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.io.Files;

public class PathUtils {

  private final static HashFunction HASH_FUNCTION = Hashing.goodFastHash(64);

  public static HashCode hash(Path file) {
    try {
      File f = file.toFile();
      if (!f.isDirectory()) {
        if(!f.exists()) return null;
        return Files.hash(file.toFile(), HASH_FUNCTION);
      } else {
        return HASH_FUNCTION.newHasher().putString(file.toString(), Charsets.UTF_8).hash();
      }
    } catch (IOException e) {
    }
    return null;
  }

  public static Map<Path, HashCode> createHashCodeMap(Path file) {
    Map<Path, HashCode> lastModifiedMap = new ConcurrentHashMap<Path, HashCode>();
    for (Path child : recursiveListFiles(file)) {
      HashCode hash = hash(child);
      lastModifiedMap.put(child, hash);
    }
    return lastModifiedMap;
  }

  public static Set<Path> recursiveListFiles(Path file) {
    Set<Path> files = new HashSet<Path>();
    files.add(file);
    if (file.toFile().isDirectory()) {
      for (File child : file.toFile().listFiles()) {
        files.addAll(recursiveListFiles(child.toPath()));
      }
    }
    return files;
  }

  @SuppressWarnings("unchecked")
  public static <T> WatchEvent<T> cast(WatchEvent<?> event) {
    return (WatchEvent<T>) event;
  }
}
