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
package io.methvin.watcher.hashing;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * A function to convert a Path to a hash code used to check if the file content is changed. This is called by
 * DirectoryWatcher after checking that the path exists and is not a directory. Therefore this hasher can generally
 * assume that those two things are true.
 *
 * By default, this hasher may throw an IOException, which will be treated as a `null` hash by the watcher, meaning the
 * associated event will be ignored. If you want to handle that exception you can catch/rethrow it.
 */
@FunctionalInterface
public interface FileHasher {
  FileHasher DEFAULT_FILE_HASHER = path -> {
    Murmur3F murmur = new Murmur3F();
    try (InputStream is = new BufferedInputStream(Files.newInputStream(path))) {
      int b;
      while ((b = is.read()) != -1) {
        murmur.update(b);
      }
    }
    return HashCode.fromBytes(murmur.getValueBytesBigEndian());
  };

  HashCode hash(Path path) throws IOException;
}
