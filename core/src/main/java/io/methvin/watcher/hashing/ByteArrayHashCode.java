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

import java.util.Arrays;
import java.util.Base64;

/** A class representing the hash code of a file as a byte array. */
class ByteArrayFileHash implements FileHash {
  private final byte[] value;

  ByteArrayFileHash(byte[] value) {
    this.value = value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ByteArrayFileHash hashCode = (ByteArrayFileHash) o;
    return Arrays.equals(value, hashCode.value);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(value);
  }

  @Override
  public String toString() {
    return Base64.getEncoder().encodeToString(value);
  }

  public byte[] asBytes() {
    return Arrays.copyOf(value, value.length);
  }
}
