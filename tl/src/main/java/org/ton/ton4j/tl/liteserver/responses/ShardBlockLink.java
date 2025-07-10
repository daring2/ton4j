package org.ton.ton4j.tl.liteserver.responses;

import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import lombok.Builder;
import lombok.Data;
import org.ton.ton4j.utils.Utils;

/** liteServer.shardBlockLink id:tonNode.blockIdExt proof:bytes = liteServer.ShardBlockLink; */
@Builder
@Data
public class ShardBlockLink implements Serializable {
  private BlockIdExt id;
  public byte[] proof;

  public String getProof() {
    if (proof == null) {
      return "";
    }
    return Utils.bytesToHex(proof);
  }

  public static ShardBlockLink deserialize(ByteBuffer byteBuffer) {
    byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
    return ShardBlockLink.builder()
        .id(BlockIdExt.deserialize(byteBuffer))
        .proof(Utils.fromBytes(byteBuffer))
        .build();
  }
}
