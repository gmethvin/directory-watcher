package io.methvin.watcher.hashing;

public interface Hash {
   static final Hash DIRECTORY = HashCode.DIRECTORY;

   String asString();

   byte[] asBytes();
}
