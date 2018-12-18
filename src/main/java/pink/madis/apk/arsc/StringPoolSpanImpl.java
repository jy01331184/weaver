
package pink.madis.apk.arsc;

final class StringPoolSpanImpl extends StringPoolChunk.StringPoolSpan {
  //TODO liuyan modified
  private int nameIndex;
  private final int start;
  private final int stop;
  //TODO liuyan modified
  private StringPoolChunk parent;

  StringPoolSpanImpl(
      int nameIndex,
      int start,
      int stop,
      StringPoolChunk parent) {
    this.nameIndex = nameIndex;
    this.start = start;
    this.stop = stop;
    if (parent == null) {
      throw new NullPointerException("Null parent");
    }
    this.parent = parent;
  }

  @Override
  public int nameIndex() {
    return nameIndex;
  }

  @Override
  public int setNameIndex(int index) {
    return nameIndex = index;
  }

  @Override
  public int start() {
    return start;
  }

  @Override
  public int stop() {
    return stop;
  }

  //TODO liuyan
  @Override
  public void setParent(StringPoolChunk stringPoolChunk) {
    parent = stringPoolChunk;
  }

  @Override
  public StringPoolChunk parent() {
    return parent;
  }

  //TODO liuyan delete
//  @Override
//  public boolean equals(Object o) {
//    if (o == this) {
//      return true;
//    }
//    if (o instanceof StringPoolChunk.StringPoolSpan) {
//      StringPoolChunk.StringPoolSpan that = (StringPoolChunk.StringPoolSpan) o;
//      return (this.nameIndex == that.nameIndex())
//           && (this.start == that.start())
//           && (this.stop == that.stop())
//           && (this.parent.equals(that.parent()));
//    }
//    return false;
//  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.nameIndex;
    h *= 1000003;
    h ^= this.start;
    h *= 1000003;
    h ^= this.stop;
    h *= 1000003;
    h ^= this.parent.hashCode();
    return h;
  }

}
