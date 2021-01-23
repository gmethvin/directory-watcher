package io.methvin.watcher.hashing;

import java.util.Base64;

import com.google.common.hash.HashCode;

public interface Murmur3FAccessor {

   static String asString(Hash hash) {
      ByteArrayHashCode hashCode = (ByteArrayHashCode) hash;
      return Base64.getEncoder().encodeToString(hashCode.value());
   }

   static byte[] asBytes(Hash hash) {
      ByteArrayHashCode hashCode = (ByteArrayHashCode) hash;
      byte[] clone = new byte[hashCode.value().length];
      System.arraycopy(hashCode.value(), 0, clone, 0, hashCode.value().length);
      return clone;
   }

}
