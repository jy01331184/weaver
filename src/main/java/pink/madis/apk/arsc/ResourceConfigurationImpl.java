
package pink.madis.apk.arsc;

import java.util.Arrays;

final class ResourceConfigurationImpl extends ResourceConfiguration {

 private final int size;
 private final int mcc;
 private final int mnc;
 private final byte[] language;
 private final byte[] region;
 private final int orientation;
 private final int touchscreen;
 private final int density;
 private final int keyboard;
 private final int navigation;
 private final int inputFlags;
 private final int screenWidth;
 private final int screenHeight;
 private final int sdkVersion;
 private final int minorVersion;
 private final int screenLayout;
 private final int uiMode;
 private final int smallestScreenWidthDp;
 private final int screenWidthDp;
 private final int screenHeightDp;
 private final byte[] localeScript;
 private final byte[] localeVariant;
 private final int screenLayout2;
 private final byte[] unknown;

 ResourceConfigurationImpl(
     int size,
     int mcc,
     int mnc,
     byte[] language,
     byte[] region,
     int orientation,
     int touchscreen,
     int density,
     int keyboard,
     int navigation,
     int inputFlags,
     int screenWidth,
     int screenHeight,
     int sdkVersion,
     int minorVersion,
     int screenLayout,
     int uiMode,
     int smallestScreenWidthDp,
     int screenWidthDp,
     int screenHeightDp,
     byte[] localeScript,
     byte[] localeVariant,
     int screenLayout2,
     byte[] unknown) {
   this.size = size;
   this.mcc = mcc;
   this.mnc = mnc;
   if (language == null) {
     throw new NullPointerException("Null language");
   }
   this.language = language;
   if (region == null) {
     throw new NullPointerException("Null region");
   }
   this.region = region;
   this.orientation = orientation;
   this.touchscreen = touchscreen;
   this.density = density;
   this.keyboard = keyboard;
   this.navigation = navigation;
   this.inputFlags = inputFlags;
   this.screenWidth = screenWidth;
   this.screenHeight = screenHeight;
   this.sdkVersion = sdkVersion;
   this.minorVersion = minorVersion;
   this.screenLayout = screenLayout;
   this.uiMode = uiMode;
   this.smallestScreenWidthDp = smallestScreenWidthDp;
   this.screenWidthDp = screenWidthDp;
   this.screenHeightDp = screenHeightDp;
   if (localeScript == null) {
     throw new NullPointerException("Null localeScript");
   }
   this.localeScript = localeScript;
   if (localeVariant == null) {
     throw new NullPointerException("Null localeVariant");
   }
   this.localeVariant = localeVariant;
   this.screenLayout2 = screenLayout2;
   if (unknown == null) {
     throw new NullPointerException("Null unknown");
   }
   this.unknown = unknown;
 }

 @Override
 public int size() {
   return size;
 }

 @Override
 public int mcc() {
   return mcc;
 }

 @Override
 public int mnc() {
   return mnc;
 }

 @SuppressWarnings(value = {"mutable"})
 @Override
 public byte[] language() {
   return language;
 }

 @SuppressWarnings(value = {"mutable"})
 @Override
 public byte[] region() {
   return region;
 }

 @Override
 public int orientation() {
   return orientation;
 }

 @Override
 public int touchscreen() {
   return touchscreen;
 }

 @Override
 public int density() {
   return density;
 }

 @Override
 public int keyboard() {
   return keyboard;
 }

 @Override
 public int navigation() {
   return navigation;
 }

 @Override
 public int inputFlags() {
   return inputFlags;
 }

 @Override
 public int screenWidth() {
   return screenWidth;
 }

 @Override
 public int screenHeight() {
   return screenHeight;
 }

 @Override
 public int sdkVersion() {
   return sdkVersion;
 }

 @Override
 public int minorVersion() {
   return minorVersion;
 }

 @Override
 public int screenLayout() {
   return screenLayout;
 }

 @Override
 public int uiMode() {
   return uiMode;
 }

 @Override
 public int smallestScreenWidthDp() {
   return smallestScreenWidthDp;
 }

 @Override
 public int screenWidthDp() {
   return screenWidthDp;
 }

 @Override
 public int screenHeightDp() {
   return screenHeightDp;
 }

 @SuppressWarnings(value = {"mutable"})
 @Override
 public byte[] localeScript() {
   return localeScript;
 }

 @SuppressWarnings(value = {"mutable"})
 @Override
 public byte[] localeVariant() {
   return localeVariant;
 }

 @Override
 public int screenLayout2() {
   return screenLayout2;
 }

 @SuppressWarnings(value = {"mutable"})
 @Override
 public byte[] unknown() {
   return unknown;
 }

 @Override
 public boolean equals(Object o) {
   if (o == this) {
     return true;
   }
   if (o instanceof ResourceConfiguration) {
     ResourceConfiguration that = (ResourceConfiguration) o;
     return (this.size == that.size())
          && (this.mcc == that.mcc())
          && (this.mnc == that.mnc())
          && (Arrays.equals(this.language, (that instanceof ResourceConfigurationImpl) ? ((ResourceConfigurationImpl) that).language : that.language()))
          && (Arrays.equals(this.region, (that instanceof ResourceConfigurationImpl) ? ((ResourceConfigurationImpl) that).region : that.region()))
          && (this.orientation == that.orientation())
          && (this.touchscreen == that.touchscreen())
          && (this.density == that.density())
          && (this.keyboard == that.keyboard())
          && (this.navigation == that.navigation())
          && (this.inputFlags == that.inputFlags())
          && (this.screenWidth == that.screenWidth())
          && (this.screenHeight == that.screenHeight())
          && (this.sdkVersion == that.sdkVersion())
          && (this.minorVersion == that.minorVersion())
          && (this.screenLayout == that.screenLayout())
          && (this.uiMode == that.uiMode())
          && (this.smallestScreenWidthDp == that.smallestScreenWidthDp())
          && (this.screenWidthDp == that.screenWidthDp())
          && (this.screenHeightDp == that.screenHeightDp())
          && (Arrays.equals(this.localeScript, (that instanceof ResourceConfigurationImpl) ? ((ResourceConfigurationImpl) that).localeScript : that.localeScript()))
          && (Arrays.equals(this.localeVariant, (that instanceof ResourceConfigurationImpl) ? ((ResourceConfigurationImpl) that).localeVariant : that.localeVariant()))
          && (this.screenLayout2 == that.screenLayout2())
          && (Arrays.equals(this.unknown, (that instanceof ResourceConfigurationImpl) ? ((ResourceConfigurationImpl) that).unknown : that.unknown()));
   }
   return false;
 }

 @Override
 public int hashCode() {
   int h = 1;
   h *= 1000003;
   h ^= this.size;
   h *= 1000003;
   h ^= this.mcc;
   h *= 1000003;
   h ^= this.mnc;
   h *= 1000003;
   h ^= Arrays.hashCode(this.language);
   h *= 1000003;
   h ^= Arrays.hashCode(this.region);
   h *= 1000003;
   h ^= this.orientation;
   h *= 1000003;
   h ^= this.touchscreen;
   h *= 1000003;
   h ^= this.density;
   h *= 1000003;
   h ^= this.keyboard;
   h *= 1000003;
   h ^= this.navigation;
   h *= 1000003;
   h ^= this.inputFlags;
   h *= 1000003;
   h ^= this.screenWidth;
   h *= 1000003;
   h ^= this.screenHeight;
   h *= 1000003;
   h ^= this.sdkVersion;
   h *= 1000003;
   h ^= this.minorVersion;
   h *= 1000003;
   h ^= this.screenLayout;
   h *= 1000003;
   h ^= this.uiMode;
   h *= 1000003;
   h ^= this.smallestScreenWidthDp;
   h *= 1000003;
   h ^= this.screenWidthDp;
   h *= 1000003;
   h ^= this.screenHeightDp;
   h *= 1000003;
   h ^= Arrays.hashCode(this.localeScript);
   h *= 1000003;
   h ^= Arrays.hashCode(this.localeVariant);
   h *= 1000003;
   h ^= this.screenLayout2;
   h *= 1000003;
   h ^= Arrays.hashCode(this.unknown);
   return h;
 }

}
