package org.ton.ton4j.smartcontract.types;

import java.math.BigInteger;
import java.util.List;
import lombok.Builder;
import lombok.Data;
import org.ton.ton4j.address.Address;
import org.ton.ton4j.cell.Cell;
import org.ton.ton4j.smartcontract.SendMode;
import org.ton.ton4j.tlb.StateInit;
import org.ton.ton4j.tonlib.types.ExtraCurrency;

@Builder
@Data
public class LockupWalletV1Config implements WalletConfig {
  long walletId;
  long seqno;

  @Deprecated(since = "0.9.9", forRemoval = true)
  int mode;

  SendMode sendMode;
  boolean bounce;
  long validUntil;
  Address destination;
  StateInit stateInit;
  BigInteger amount;
  List<ExtraCurrency> extraCurrencies;
  Cell body;
  String comment;
}
