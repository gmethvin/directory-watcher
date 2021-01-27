package io.methvin.watcher.hashing;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * An interface representing the hash of a file.
 *
 * <p>DirectoryWatcher uses the equals() method on the member classes of this interface to determine
 * if the content of a file has changed.
 */
public interface FileHash {

  /** A special hash instance used by DirectoryWatcher to represent a directory */
  static final FileHash DIRECTORY =
      new FileHash() {
        private byte[] emptyBytes = new byte[0];

        @Override
        public String toString() {
          return "DIRECTORY";
        }

        @Override
        public byte[] asBytes() {
          return emptyBytes;
        }
      };

  public static FileHash fromBytes(byte[] value) {
    return new ByteArrayFileHash(Arrays.copyOf(value, value.length));
  }

  public static FileHash fromLong(long value) {
    ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
    buffer.putLong(value);
    return new ByteArrayFileHash(buffer.array());
  }

  public static FileHash directory() {
    return DIRECTORY;
  }

  /** @return A representation of this hash as a human-readable string */
  default String asString() {
    return toString();
  }

  /** @return a representation of this hash as a byte array. */
  byte[] asBytes();
}
