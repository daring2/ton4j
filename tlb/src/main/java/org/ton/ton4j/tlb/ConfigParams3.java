package org.ton.ton4j.tlb;

import java.io.Serializable;
import java.math.BigInteger;
import lombok.Builder;
import lombok.Data;
import org.ton.ton4j.cell.Cell;
import org.ton.ton4j.cell.CellBuilder;
import org.ton.ton4j.cell.CellSlice;
import org.ton.ton4j.utils.Utils;

/**
 *
 *
 * <pre>
 * _ fee_collector_addr:bits256 = ConfigParam 3; // ConfigParam 1 is used if absent
 * </pre>
 */
@Builder
@Data
public class ConfigParams3 implements Serializable {
  BigInteger feeCollectorAddr;

  public String getConfigAddr() {
    if (feeCollectorAddr == null) {
      return "";
    }
    return Utils.bytesToHex(Utils.to32ByteArray(feeCollectorAddr));
  }

  public Cell toCell() {
    return CellBuilder.beginCell().storeUint(feeCollectorAddr, 256).endCell();
  }

  public static ConfigParams3 deserialize(CellSlice cs) {
    return ConfigParams3.builder().feeCollectorAddr(cs.loadUint(256)).build();
  }
}
