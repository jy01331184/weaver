/*
 * Copyright (C) 2011 The Guava Authors
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

import java.math.BigInteger;

import static guava.base.Preconditions.checkArgument;
import static guava.base.Preconditions.checkNotNull;

/**
 * Static utility methods pertaining to {@code long} primitives that interpret values as
 * <i>unsigned</i> (that is, any negative value {@code x} is treated as the positive value
 * {@code 2^64 + x}). The methods for which signedness is not an issue are in {@link Longs}, as well
 * as signed versions of methods for which signedness is an issue.
 *
 * <p>In addition, this class provides several static methods for converting a {@code long} to a
 * {@code String} and a {@code String} to a {@code long} that treat the {@code long} as an unsigned
 * number.
 *
 * <p>Users of these utilities must be <i>extremely careful</i> not to mix up signed and unsigned
 * {@code long} values. When possible, it is recommended that the {@link UnsignedLong} wrapper class
 * be used, at a small efficiency penalty, to enforce the distinction in the type system.
 *
 * <p>See the Guava User Guide article on
 * <a href="https://github.com/google/guava/wiki/PrimitivesExplained#unsigned-support">unsigned
 * primitive utilities</a>.
 *
 * @author Louis Wasserman
 * @author Brian Milch
 * @author Colin Evans
 * @since 10.0
 */


public final class UnsignedLongs {
  private UnsignedLongs() {}

  public static final long MAX_VALUE = -1L; // Equivalent to 2^64 - 1

  /**
   * A (self-inverse) bijection which converts the ordering on unsigned longs to the ordering on
   * longs, that is, {@code a <= b} as unsigned longs if and only if {@code flip(a) <= flip(b)} as
   * signed longs.
   */
  private static long flip(long a) {
    return a ^ Long.MIN_VALUE;
  }

  /**
   * Compares the two specified {@code long} values, treating them as unsigned values between
   * {@code 0} and {@code 2^64 - 1} inclusive.
   *
   * @param a the first unsigned {@code long} to compare
   * @param b the second unsigned {@code long} to compare
   * @return a negative value if {@code a} is less than {@code b}; a positive value if {@code a} is
   *     greater than {@code b}; or zero if they are equal
   */
  public static int compare(long a, long b) {
    return Longs.compare(flip(a), flip(b));
  }

  /**
   * Returns dividend / divisor, where the dividend and divisor are treated as unsigned 64-bit
   * quantities.
   *
   * @param dividend the dividend (numerator)
   * @param divisor the divisor (denominator)
   * @throws ArithmeticException if divisor is 0
   */
  public static long divide(long dividend, long divisor) {
    if (divisor < 0) { // i.e., divisor >= 2^63:
      if (compare(dividend, divisor) < 0) {
        return 0; // dividend < divisor
      } else {
        return 1; // dividend >= divisor
      }
    }

    // Optimization - use signed division if dividend < 2^63
    if (dividend >= 0) {
      return dividend / divisor;
    }

    /*
     * Otherwise, approximate the quotient, check, and correct if necessary. Our approximation is
     * guaranteed to be either exact or one less than the correct value. This follows from fact that
     * floor(floor(x)/i) == floor(x/i) for any real x and integer i != 0. The proof is not quite
     * trivial.
     */
    long quotient = ((dividend >>> 1) / divisor) << 1;
    long rem = dividend - quotient * divisor;
    return quotient + (compare(rem, divisor) >= 0 ? 1 : 0);
  }

  /**
   * Returns dividend % divisor, where the dividend and divisor are treated as unsigned 64-bit
   * quantities.
   *
   * @param dividend the dividend (numerator)
   * @param divisor the divisor (denominator)
   * @throws ArithmeticException if divisor is 0
   * @since 11.0
   */
  public static long remainder(long dividend, long divisor) {
    if (divisor < 0) { // i.e., divisor >= 2^63:
      if (compare(dividend, divisor) < 0) {
        return dividend; // dividend < divisor
      } else {
        return dividend - divisor; // dividend >= divisor
      }
    }

    // Optimization - use signed modulus if dividend < 2^63
    if (dividend >= 0) {
      return dividend % divisor;
    }

    /*
     * Otherwise, approximate the quotient, check, and correct if necessary. Our approximation is
     * guaranteed to be either exact or one less than the correct value. This follows from the fact
     * that floor(floor(x)/i) == floor(x/i) for any real x and integer i != 0. The proof is not
     * quite trivial.
     */
    long quotient = ((dividend >>> 1) / divisor) << 1;
    long rem = dividend - quotient * divisor;
    return rem - (compare(rem, divisor) >= 0 ? divisor : 0);
  }

  /**
   * Returns the unsigned {@code long} value represented by a string with the given radix.
   *
   * @param string the string containing the unsigned {@code long} representation to be parsed.
   * @param radix the radix to use while parsing {@code string}
   * @throws NumberFormatException if the string does not contain a valid unsigned {@code long} with
   *     the given radix, or if {@code radix} is not between {@link Character#MIN_RADIX} and
   *     {@link Character#MAX_RADIX}.
   * @throws NullPointerException if {@code string} is null (in contrast to
   *     {@link Long#parseLong(String)})
   */
  
  public static long parseUnsignedLong(String string, int radix) {
    checkNotNull(string);
    if (string.length() == 0) {
      throw new NumberFormatException("empty string");
    }
    if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX) {
      throw new NumberFormatException("illegal radix: " + radix);
    }

    int maxSafePos = maxSafeDigits[radix] - 1;
    long value = 0;
    for (int pos = 0; pos < string.length(); pos++) {
      int digit = Character.digit(string.charAt(pos), radix);
      if (digit == -1) {
        throw new NumberFormatException(string);
      }
      if (pos > maxSafePos && overflowInParse(value, digit, radix)) {
        throw new NumberFormatException("Too large for unsigned long: " + string);
      }
      value = (value * radix) + digit;
    }

    return value;
  }

  /**
   * Returns true if (current * radix) + digit is a number too large to be represented by an
   * unsigned long. This is useful for detecting overflow while parsing a string representation of a
   * number. Does not verify whether supplied radix is valid, passing an invalid radix will give
   * undefined results or an ArrayIndexOutOfBoundsException.
   */
  private static boolean overflowInParse(long current, int digit, int radix) {
    if (current >= 0) {
      if (current < maxValueDivs[radix]) {
        return false;
      }
      if (current > maxValueDivs[radix]) {
        return true;
      }
      // current == maxValueDivs[radix]
      return (digit > maxValueMods[radix]);
    }

    // current < 0: high bit is set
    return true;
  }

  /**
   * Returns a string representation of x, where x is treated as unsigned.
   */
  public static String toString(long x) {
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
   */
  public static String toString(long x, int radix) {
    checkArgument(
        radix >= Character.MIN_RADIX && radix <= Character.MAX_RADIX,
        "radix (%s) must be between Character.MIN_RADIX and Character.MAX_RADIX",
        radix);
    if (x == 0) {
      // Simply return "0"
      return "0";
    } else if (x > 0) {
      return Long.toString(x, radix);
    } else {
      char[] buf = new char[64];
      int i = buf.length;
      if ((radix & (radix - 1)) == 0) {
        // Radix is a power of two so we can avoid division.
        int shift = Integer.numberOfTrailingZeros(radix);
        int mask = radix - 1;
        do {
          buf[--i] = Character.forDigit(((int) x) & mask, radix);
          x >>>= shift;
        } while (x != 0);
      } else {
        // Separate off the last digit using unsigned division. That will leave
        // a number that is nonnegative as a signed integer.
        long quotient;
        if ((radix & 1) == 0) {
          // Fast path for the usual case where the radix is even.
          quotient = (x >>> 1) / (radix >>> 1);
        } else {
          quotient = divide(x, radix);
        }
        long rem = x - quotient * radix;
        buf[--i] = Character.forDigit((int) rem, radix);
        x = quotient;
        // Simple modulo/division approach
        while (x > 0) {
          buf[--i] = Character.forDigit((int) (x % radix), radix);
          x /= radix;
        }
      }
      // Generate string
      return new String(buf, i, buf.length - i);
    }
  }

  // calculated as 0xffffffffffffffff / radix
  private static final long[] maxValueDivs = new long[Character.MAX_RADIX + 1];
  private static final int[] maxValueMods = new int[Character.MAX_RADIX + 1];
  private static final int[] maxSafeDigits = new int[Character.MAX_RADIX + 1];

  static {
    BigInteger overflow = new BigInteger("10000000000000000", 16);
    for (int i = Character.MIN_RADIX; i <= Character.MAX_RADIX; i++) {
      maxValueDivs[i] = divide(MAX_VALUE, i);
      maxValueMods[i] = (int) remainder(MAX_VALUE, i);
      maxSafeDigits[i] = overflow.toString(i).length() - 1;
    }
  }
}
