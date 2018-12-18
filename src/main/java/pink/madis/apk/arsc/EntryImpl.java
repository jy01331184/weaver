
package pink.madis.apk.arsc;

import java.util.Map;

public final class EntryImpl extends TypeChunk.Entry {
  private final int headerSize;
  private final int flags;
  private int keyIndex;
  private final ResourceValue value;
  private Map<Integer, ResourceValue> values;
  private int parentEntry;
  private final int index;
  private final TypeChunk parent;

  public EntryImpl(
      int headerSize,
      int flags,
      int keyIndex,
      ResourceValue value,
      Map<Integer, ResourceValue> values,
      int parentEntry,
      int index,
      TypeChunk parent) {
    this.headerSize = headerSize;
    this.flags = flags;
    this.keyIndex = keyIndex;
    this.value = value;
    if (values == null) {
      throw new NullPointerException("Null values");
    }
    this.values = values;
    this.parentEntry = parentEntry;
    this.index = index;
    if (parent == null) {
      throw new NullPointerException("Null parent");
    }
    this.parent = parent;
  }

  @Override
  public int headerSize() {
    return headerSize;
  }

  @Override
  public int flags() {
    return flags;
  }

  @Override
  public int keyIndex() {
    return keyIndex;
  }

  @Override
  public void setKeyIndex(int keyIndex) {
    this.keyIndex = keyIndex;
  }

  @Override
  public ResourceValue value() {
    return value;
  }

  @Override
  public Map<Integer, ResourceValue> values() {
    return values;
  }

  @Override
  public void setValues(Map<Integer, ResourceValue> values) {
    if(!isComplex() && values != null) {
      throw new IllegalArgumentException("only complex entry can has values!");
    }
    this.values = values;
  }

  @Override
  public int parentEntry() {
    return parentEntry;
  }

  @Override
  public void setParentEntry(int parentEntry) {
    this.parentEntry = parentEntry;
  }

  @Override
  public int index() {
    return index;
  }

  @Override
  public TypeChunk parent() {
    return parent;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof TypeChunk.Entry) {
      TypeChunk.Entry that = (TypeChunk.Entry) o;
      return (this.headerSize == that.headerSize())
          && (this.flags == that.flags())
          && (this.keyIndex == that.keyIndex())
          && ((this.value == null) ? (that.value() == null) : this.value.equals(that.value()))
          && (this.values.equals(that.values()))
          && (this.parentEntry == that.parentEntry())
          && (this.index == that.index())
          && (this.parent.equals(that.parent()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.headerSize;
    h *= 1000003;
    h ^= this.flags;
    h *= 1000003;
    h ^= this.keyIndex;
    h *= 1000003;
    h ^= (value == null) ? 0 : this.value.hashCode();
    h *= 1000003;
    h ^= this.values.hashCode();
    h *= 1000003;
    h ^= this.parentEntry;
    h *= 1000003;
    h ^= this.index;
    h *= 1000003;
    h ^= this.parent.hashCode();
    return h;
  }

}
