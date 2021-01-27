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

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Base64;
import java.util.Formatter;

/** A class representing the hash code of a file. */
public class ByteArrayHashCode implements Hash {
  private final byte[] value;

  public static final ByteArrayHashCode EMPTY = new ByteArrayHashCode(new byte[0]);

  public static ByteArrayHashCode fromBytes(byte[] value) {
    return new ByteArrayHashCode(Arrays.copyOf(value, value.length));
  }

  public static ByteArrayHashCode fromLong(long value) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.putLong(value);
    return new ByteArrayHashCode(buffer.array());
  }

  public static ByteArrayHashCode empty() {
    return EMPTY;
  }

  ByteArrayHashCode(byte[] value) {
    this.value = value;
  }

  byte[] value() {
    return this.value;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    ByteArrayHashCode hashCode = (ByteArrayHashCode) o;
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
}
