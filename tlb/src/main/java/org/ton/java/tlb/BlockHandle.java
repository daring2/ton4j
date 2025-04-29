package org.ton.java.tlb;

import lombok.Builder;
import lombok.Data;
import org.ton.java.cell.CellSlice;

import java.io.Serializable;
import java.math.BigInteger;

@Builder
@Data
public class BlockHandle implements Serializable {
  BigInteger offset; // The offset of the block in the file. uint64_t
  BigInteger size; // The size of the stored block. uint64_t

  public static BlockHandle deserialize(CellSlice cs) {
    return BlockHandle.builder().offset(cs.loadUint(64)).size(cs.loadUint(64)).build();
  }
}
