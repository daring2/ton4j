package org.ton.ton4j.tl.liteserver.queries;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.Builder;
import lombok.Data;
import org.ton.ton4j.tl.liteserver.responses.BlockId;
import org.ton.ton4j.tl.liteserver.responses.BlockIdExt;
import org.ton.ton4j.tl.liteserver.responses.LiteServerQueryData;

@Builder
@Data
public class LookupBlockWithProofQuery implements LiteServerQueryData {
  public final int LOOKUP_BLOCK_WITH_PROOF_QUERY = -1677434888;

  private int mode;
  private BlockId id;
  private BlockIdExt mcBlockId;
  private long lt;
  private int utime;

  public String getQueryName() {
    return "liteServer.lookupBlockWithProof mode:# id:tonNode.blockId mc_block_id:tonNode.blockIdExt lt:mode.1?long utime:mode.2?int = liteServer.LookupBlockResult";
  }

  public byte[] getQueryData() {
    int size = 4 + 4 + BlockId.getSize() + BlockIdExt.getSize();
    if ((mode & 2) != 0) size += 8; // lt
    if ((mode & 4) != 0) size += 4; // utime

    ByteBuffer buffer = ByteBuffer.allocate(size);
    buffer.order(ByteOrder.LITTLE_ENDIAN);
    buffer.putInt(LOOKUP_BLOCK_WITH_PROOF_QUERY);
    buffer.putInt(mode);
    buffer.put(id.serialize());
    buffer.put(mcBlockId.serialize());

    if ((mode & 2) != 0) {
      buffer.putLong(lt);
    }

    if ((mode & 4) != 0) {
      buffer.putInt(utime);
    }

    return buffer.array();
  }
}
