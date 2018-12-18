
package pink.madis.apk.arsc;

import java.util.List;

public final class StringPoolStyleImpl extends StringPoolChunk.StringPoolStyle {

  private final List<StringPoolChunk.StringPoolSpan> spans;

  public StringPoolStyleImpl(
      List<StringPoolChunk.StringPoolSpan> spans) {
    if (spans == null) {
      throw new NullPointerException("Null spans");
    }
    this.spans = spans;
  }

  @Override
  public List<StringPoolChunk.StringPoolSpan> spans() {
    return spans;
  }

  @Override
  public boolean equals(Object o) {
    if (o == this) {
      return true;
    }
    if (o instanceof StringPoolChunk.StringPoolStyle) {
      StringPoolChunk.StringPoolStyle that = (StringPoolChunk.StringPoolStyle) o;
      return (this.spans.equals(that.spans()));
    }
    return false;
  }

  @Override
  public int hashCode() {
    int h = 1;
    h *= 1000003;
    h ^= this.spans.hashCode();
    return h;
  }

}
