
package pink.madis.apk.arsc;

public final class XmlAttributeImpl extends XmlAttribute {

  private int namespaceIndex;
  private int nameIndex;
  private int rawValueIndex;
  private final ResourceValue typedValue;
  private final XmlNodeChunk parent;

  public XmlAttributeImpl(
      int namespaceIndex,
      int nameIndex,
      int rawValueIndex,
      ResourceValue typedValue,
      XmlNodeChunk parent) {
    this.namespaceIndex = namespaceIndex;
    this.nameIndex = nameIndex;
    this.rawValueIndex = rawValueIndex;
    if (typedValue == null) {
      throw new NullPointerException("Null typedValue");
    }
    this.typedValue = typedValue;
    if (parent == null) {
      throw new NullPointerException("Null parent");
    }
    this.parent = parent;
  }

  @Override
  public int namespaceIndex() {
    return namespaceIndex;
  }

  @Override
  public void setNamespaceIndex(int namespaceIndex) {
    this.namespaceIndex = namespaceIndex;
  }

  @Override
  public int nameIndex() {
    return nameIndex;
  }

  @Override
  public void setNameIndex(int nameIndex) {
    this.nameIndex = nameIndex;
  }

  @Override
  public int rawValueIndex() {
    return rawValueIndex;
  }

  @Override
  public void setRawValueIndex(int rawValueIndex) {
    this.rawValueIndex = rawValueIndex;
  }

  @Override
  public ResourceValue typedValue() {
    return typedValue;
  }

  @Override
  public XmlNodeChunk parent() {
    return parent;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof XmlAttribute) {
      XmlAttribute that = (XmlAttribute) o;
      return (this.namespaceIndex == that.namespaceIndex())
           && (this.nameIndex == that.nameIndex())
           && (this.rawValueIndex == that.rawValueIndex())
           && (this.typedValue.equals(that.typedValue()))
           && (this.parent.equals(that.parent()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.namespaceIndex;
    h *= 1000003;
    h ^= this.nameIndex;
    h *= 1000003;
    h ^= this.rawValueIndex;
    h *= 1000003;
    h ^= this.typedValue.hashCode();
    h *= 1000003;
    h ^= this.parent.hashCode();
    return h;
  }

}
