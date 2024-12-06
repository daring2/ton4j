package org.ton.java.tonconnect;

import lombok.*;

@Builder
@Data
public class WalletAccount {
  private String address;
  private String publicKey;
  private int chain;
  private String walletStateInit; // base64Url
}
