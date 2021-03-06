/*
 * Copyright (C) 2009 The Guava Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package guava.primitives;

import java.util.Comparator;

import static guava.base.Preconditions.checkArgument;
import static guava.base.Preconditions.checkNotNull;

/**
 * Static utility methods pertaining to {@code byte} primitives that interpret values as
 * <i>unsigned</i> (that is, any negative value {@code b} is treated as the positive value
 * {@code 256 + b}). The corresponding methods that treat the values as signed are found in
 * SignedBytes, and the methods for which signedness is not an issue are in Bytes.
 *
 * <p>See the Guava User Guide article on
 * <a href="https://github.com/google/guava/wiki/PrimitivesExplained">primitive utilities</a>.
 *
 * @author Kevin Bourrillion
 * @author Martin Buchholz
 * @author Hiroshi Yamauchi
 * @author Louis Wasserman
 * @since 1.0
 */
public final class UnsignedBytes {
  private UnsignedBytes() {}

  /**
   * The largest power of two that can be represented as an unsigned {@code
   * byte}.
   *
   * @since 10.0
   */
  public static final byte MAX_POWER_OF_TWO = (byte) 0x80;

  /**
   * The largest value that fits into an unsigned byte.
   *
   * @since 13.0
   */
  public static final byte MAX_VALUE = (byte) 0xFF;

  private static final int UNSIGNED_MASK = 0xFF;

  /**
   * Returns the value of the given byte as an integer, when treated as unsigned. That is, returns
   * {@code value + 256} if {@code value} is negative; {@code value} itself otherwise.
   *
   * @since 6.0
   */
  public static int toInt(byte value) {
    return value & UNSIGNED_MASK;
  }

  /**
   * Returns the {@code byte} value that, when treated as unsigned, is equal to {@code value}, if
   * possible.
   *
   * @param value a value between 0 and 255 inclusive
   * @return the {@code byte} value that, when treated as unsigned, equals {@code value}
   * @throws IllegalArgumentException if {@code value} is negative or greater than 255
   */
  public static byte checkedCast(long value) {
    if ((value >> Byte.SIZE) != 0) {
      // don't use checkArgument here, to avoid boxing
      throw new IllegalArgumentException("Out of range: " + value);
    }
    return (byte) value;
  }

  /**
   * Returns the {@code byte} value that, when treated as unsigned, is nearest in value to
   * {@code value}.
   *
   * @param value any {@code long} value
   * @return {@code (byte) 255} if {@code value >= 255}, {@code (byte) 0} if {@code value <= 0}, and
   *     {@code value} cast to {@code byte} otherwise
   */
  public static byte saturatedCast(long value) {
    if (value > toInt(MAX_VALUE)) {
      return MAX_VALUE; // -1
    }
    if (value < 0) {
      return (byte) 0;
    }
    return (byte) value;
  }

  /**
   * Compares the two specified {@code byte} values, treating them as unsigned values between 0 and
   * 255 inclusive. For example, {@code (byte) -127} is considered greater than {@code (byte) 127}
   * because it is seen as having the value of positive {@code 129}.
   *
   * @param a the first {@code byte} to compare
   * @param b the second {@code byte} to compare
   * @return a negative value if {@code a} is less than {@code b}; a positive value if {@code a} is
   *     greater than {@code b}; or zero if they are equal
   */
  public static int compare(byte a, byte b) {
    return toInt(a) - toInt(b);
  }

  /**
   * Returns the least value present in {@code array}.
   *
   * @param array a <i>nonempty</i> array of {@code byte} values
   * @return the value present in {@code array} that is less than or equal to every other value in
   *     the array
   * @throws IllegalArgumentException if {@code array} is empty
   */
  public static byte min(byte... array) {
    checkArgument(array.length > 0);
    int min = toInt(array[0]);
    for (int i = 1; i < array.length; i++) {
      int next = toInt(array[i]);
      if (next < min) {
        min = next;
      }
    }
    return (byte) min;
  }

  /**
   * Returns the greatest value present in {@code array}.
   *
   * @param array a <i>nonempty</i> array of {@code byte} values
   * @return the value present in {@code array} that is greater than or equal to every other value
   *     in the array
   * @throws IllegalArgumentException if {@code array} is empty
   */
  public static byte max(byte... array) {
    checkArgument(array.length > 0);
    int max = toInt(array[0]);
    for (int i = 1; i < array.length; i++) {
      int next = toInt(array[i]);
      if (next > max) {
        max = next;
      }
    }
    return (byte) max;
  }

  /**
   * Returns a string representation of x, where x is treated as unsigned.
   *
   * @since 13.0
   */
  public static String toString(byte x) {
    return toString(x, 10);
  }

  /**
   * Returns a string representation of {@code x} for the given radix, where {@code x} is treated as
   * unsigned.
   *
   * @param x the value to convert to a string.
   * @param radix the radix to use while working with {@code x}
   * @throws IllegalArgumentException if {@code radix} is not between {@link Character#MIN_RADIX}
   *     and {@link Character#MAX_RADIX}.
   * @since 13.0
   */
  public static String toString(byte x, int radix) {
    checkArgument(
        radix >= Character.MIN_RADIX && radix <= Character.MAX_RADIX,
        "radix (%s) must be between Character.MIN_RADIX and Character.MAX_RADIX",
        radix);
    // Benchmarks indicate this is probably not worth optimizing.
    return Integer.toString(toInt(x), radix);
  }

  /**
   * Returns the unsigned {@code byte} value represented by the given decimal string.
   *
   * @throws NumberFormatException if the string does not contain a valid unsigned {@code byte}
   *     value
   * @throws NullPointerException if {@code string} is null (in contrast to
   *     {@link Byte#parseByte(String)})
   * @since 13.0
   */
  public static byte parseUnsignedByte(String string) {
    return parseUnsignedByte(string, 10);
  }

  /**
   * Returns the unsigned {@code byte} value represented by a string with the given radix.
   *
   * @param string the string containing the unsigned {@code byte} representation to be parsed.
   * @param radix the radix to use while parsing {@code string}
   * @throws NumberFormatException if the string does not contain a valid unsigned {@code byte} with
   *     the given radix, or if {@code radix} is not between {@link Character#MIN_RADIX} and
   *     {@link Character#MAX_RADIX}.
   * @throws NullPointerException if {@code string} is null (in contrast to
   *     {@link Byte#parseByte(String)})
   * @since 13.0
   */
  public static byte parseUnsignedByte(String string, int radix) {
    int parse = Integer.parseInt(checkNotNull(string), radix);
    // We need to throw a NumberFormatException, so we have to duplicate checkedCast. =(
    if (parse >> Byte.SIZE == 0) {
      return (byte) parse;
    } else {
      throw new NumberFormatException("out of range: " + parse);
    }
  }

  /**
   * Returns a string containing the supplied {@code byte} values separated by {@code separator}.
   * For example, {@code join(":", (byte) 1, (byte) 2,
   * (byte) 255)} returns the string {@code "1:2:255"}.
   *
   * @param separator the text that should appear between consecutive values in the resulting string
   *     (but not at the start or end)
   * @param array an array of {@code byte} values, possibly empty
   */
  public static String join(String separator, byte... array) {
    checkNotNull(separator);
    if (array.length == 0) {
      return "";
    }

    // For pre-sizing a builder, just get the right order of magnitude
    StringBuilder builder = new StringBuilder(array.length * (3 + separator.length()));
    builder.append(toInt(array[0]));
    for (int i = 1; i < array.length; i++) {
      builder.append(separator).append(toString(array[i]));
    }
    return builder.toString();
  }

  /**
   * Returns a comparator that compares two {@code byte} arrays <a
   * href="http://en.wikipedia.org/wiki/Lexicographical_order">lexicographically</a>. That is, it
   * compares, using {@link #compare(byte, byte)}), the first pair of values that follow any common
   * prefix, or when one array is a prefix of the other, treats the shorter array as the lesser. For
   * example, {@code [] < [0x01] < [0x01, 0x7F] < [0x01, 0x80] < [0x02]}. Values are treated as
   * unsigned.
   *
   * <p>The returned comparator is inconsistent with {@link Object#equals(Object)} (since arrays
   * support only identity equality), but it is consistent with
   * {@link java.util.Arrays#equals(byte[], byte[])}.
   *
   * @since 2.0
   */
  public static Comparator<byte[]> lexicographicalComparator() {
    return LexicographicalComparatorHolder.BEST_COMPARATOR;
  }

  static Comparator<byte[]> lexicographicalComparatorJavaImpl() {
    return LexicographicalComparatorHolder.PureJavaComparator.INSTANCE;
  }

  /**
   * Provides a lexicographical comparator implementation;
   *
   * <p>Uses reflection to gracefully fall back to the Java implementation if {@code Unsafe} isn't
   * available.
   */
  static class LexicographicalComparatorHolder {
    static final String UNSAFE_COMPARATOR_NAME =
        LexicographicalComparatorHolder.class.getName() + "$UnsafeComparator";

    static final Comparator<byte[]> BEST_COMPARATOR = getBestComparator();

    enum PureJavaComparator implements Comparator<byte[]> {
      INSTANCE;

      @Override
      public int compare(byte[] left, byte[] right) {
        int minLength = Math.min(left.length, right.length);
        for (int i = 0; i < minLength; i++) {
          int result = UnsignedBytes.compare(left[i], right[i]);
          if (result != 0) {
            return result;
          }
        }
        return left.length - right.length;
      }

      @Override
      public String toString() {
        return "UnsignedBytes.lexicographicalComparator() (pure Java version)";
      }
    }

    /**
     * Returns the Unsafe-using Comparator, or falls back to the pure-Java implementation if unable
     * to do so.
     */
    static Comparator<byte[]> getBestComparator() {
      try {
        Class<?> theClass = Class.forName(UNSAFE_COMPARATOR_NAME);

        // yes, UnsafeComparator does implement Comparator<byte[]>
        @SuppressWarnings("unchecked")
        Comparator<byte[]> comparator = (Comparator<byte[]>) theClass.getEnumConstants()[0];
        return comparator;
      } catch (Throwable t) { // ensure we really catch *everything*
        return lexicographicalComparatorJavaImpl();
      }
    }
  }
}
