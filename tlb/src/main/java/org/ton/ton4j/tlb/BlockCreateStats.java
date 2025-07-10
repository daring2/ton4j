package org.ton.ton4j.tlb;

import org.ton.ton4j.cell.Cell;
import org.ton.ton4j.cell.CellSlice;

/**
 *
 *
 * <pre>
 * block_create_stats#17 counters:(HashmapE 256 CreatorStats) = BlockCreateStats;
 * block_create_stats_ext#34 counters:(HashmapAugE 256 CreatorStats uint32) = BlockCreateStats;
 * </pre>
 */
public interface BlockCreateStats {

  Cell toCell();

  static BlockCreateStats deserialize(CellSlice cs) {
    long magic = cs.loadUint(8).longValue();
    if (magic == 0x17) {
      return BlockCreateStatsOrdinary.deserialize(cs);
    } else if (magic == 0x34) {
      return BlockCreateStatsExt.deserialize(cs);
    } else {
      throw new Error(
          "BlockCreateStats: magic neither equal to 0x17 nor 0x34, found 0x"
              + Long.toHexString(magic));
    }
  }
}
