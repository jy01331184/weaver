/*
 * Copyright 2016 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pink.madis.apk.arsc;

import java.io.DataOutput;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Represents a chunk whose payload is a list of sub-chunks.
 */
public abstract class ChunkWithChunks extends Chunk {

  public Map<Integer, Chunk> chunks = new LinkedHashMap<>();

  protected ChunkWithChunks(ByteBuffer buffer, Chunk parent) {
    super(buffer, parent);
  }

  @Override
  protected void init(ByteBuffer buffer) {
    super.init(buffer);
    chunks.clear();
    int start = this.offset + getHeaderSize();
    int offset = start;
    int end = this.offset + getOriginalChunkSize();
    int position = buffer.position();
    buffer.position(start);

    while (offset < end) {
      Chunk chunk = newInstance(buffer, this);
      chunks.put(offset, chunk);
      offset += chunk.getOriginalChunkSize();
    }

    buffer.position(position);
  }

  /**
   * Retrieves the @{code chunks} contained in this chunk.
   *
   * @return map of buffer offset -&gt; chunk contained in this chunk.
   */
  public final Map<Integer, Chunk> getChunks() {
    return chunks;
  }

  /**
   * append a chunk to end of chunks
   */
  public final void appendChunk(Chunk chunk) {
    int lastOffset = -1;
    for (int offset : chunks.keySet()) {
      lastOffset = offset;
    }
    if (lastOffset == -1) {
      chunks.put(this.offset + getHeaderSize(), chunk);
    } else {
      chunks.put(lastOffset + chunks.get(lastOffset).getOriginalChunkSize(), chunk);
    }
    setOriginalChunkSize(getOriginalChunkSize() + chunk.getOriginalChunkSize());
  }

  @Override
  protected void writePayload(DataOutput output, ByteBuffer header, boolean shrink)
      throws IOException {
    for (Chunk chunk : getChunks().values()) {
      byte[] chunkBytes = chunk.toByteArray(shrink);
      output.write(chunkBytes);
      writePad(output, chunkBytes.length);
    }
  }
}
