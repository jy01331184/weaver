
package pink.madis.apk.arsc;

final class LibraryChunkEntryImpl extends LibraryChunk.Entry {

  private final int packageId;
  private final String packageName;

  LibraryChunkEntryImpl(
      int packageId,
      String packageName) {
    this.packageId = packageId;
    if (packageName == null) {
      throw new NullPointerException("Null packageName");
    }
    this.packageName = packageName;
  }

  @Override
  public int packageId() {
    return packageId;
  }

  @Override
  public String packageName() {
    return packageName;
  }

  @Override
  public String toString() {
    return "Entry{"
        + "packageId=" + packageId + ", "
        + "packageName=" + packageName
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof LibraryChunk.Entry) {
      LibraryChunk.Entry that = (LibraryChunk.Entry) o;
      return (this.packageId == that.packageId())
           && (this.packageName.equals(that.packageName()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.packageId;
    h *= 1000003;
    h ^= this.packageName.hashCode();
    return h;
  }

}
