
package pink.madis.apk.arsc;

final class ResourceIdentifierImpl extends ResourceIdentifier {

  private final int packageId;
  private final int typeId;
  private final int entryId;

  ResourceIdentifierImpl(
      int packageId,
      int typeId,
      int entryId) {
    this.packageId = packageId;
    this.typeId = typeId;
    this.entryId = entryId;
  }

  @Override
  public int packageId() {
    return packageId;
  }

  @Override
  public int typeId() {
    return typeId;
  }

  @Override
  public int entryId() {
    return entryId;
  }

  @Override
  public String toString() {
    return "ResourceIdentifier{"
        + "packageId=" + packageId + ", "
        + "typeId=" + typeId + ", "
        + "entryId=" + entryId
        + "}";
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof ResourceIdentifier) {
      ResourceIdentifier that = (ResourceIdentifier) o;
      return (this.packageId == that.packageId())
           && (this.typeId == that.typeId())
           && (this.entryId == that.entryId());
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.packageId;
    h *= 1000003;
    h ^= this.typeId;
    h *= 1000003;
    h ^= this.entryId;
    return h;
  }

}
