package org.ton.ton4j.smartcontract.integrationtests;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.ton.ton4j.utils.Utils.formatNanoValue;

import com.iwebpp.crypto.TweetNaclFast;
import java.math.BigInteger;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.ton.java.adnl.AdnlLiteClient;
import org.ton.ton4j.address.Address;
import org.ton.ton4j.smartcontract.faucet.TestnetFaucet;
import org.ton.ton4j.smartcontract.types.WalletV1R3Config;
import org.ton.ton4j.smartcontract.wallet.v1.WalletV1R3;
import org.ton.ton4j.tonlib.types.ExtMessageInfo;
import org.ton.ton4j.utils.Utils;

@Slf4j
@RunWith(JUnit4.class)
public class TestWalletV1R3Short extends CommonTest {

  @Test
  public void testWalletV1R3() throws InterruptedException {

    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPair();

    WalletV1R3 contract = WalletV1R3.builder().tonlib(tonlib).keyPair(keyPair).build();

    String nonBounceableAddress = contract.getAddress().toNonBounceable();
    String bounceableAddress = contract.getAddress().toBounceable();

    log.info("non-bounceable address {}", nonBounceableAddress);
    log.info("    bounceable address {}", bounceableAddress);
    String status = tonlib.getAccountStatus(Address.of(bounceableAddress));
    log.info("account status {}", status);

    // top up new wallet using test-faucet-wallet
    BigInteger balance =
        TestnetFaucet.topUpContract(tonlib, Address.of(nonBounceableAddress), Utils.toNano(0.1));
    log.info("new wallet {} balance: {}", contract.getName(), formatNanoValue(balance));

    ExtMessageInfo extMessageInfo = contract.deploy();
    assertThat(extMessageInfo.getError().getCode()).isZero();

    contract.waitForDeployment(20);

    // transfer coins from new wallet (back to faucet)
    WalletV1R3Config config =
        WalletV1R3Config.builder()
            .seqno(contract.getSeqno())
            .destination(Address.of(TestnetFaucet.BOUNCEABLE))
            .amount(Utils.toNano(0.08))
            .comment("testNewWalletV1R2")
            .build();

    extMessageInfo = contract.send(config);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    contract.waitForBalanceChange(30);

    balance = contract.getBalance();
    status = tonlib.getAccountStatus(Address.of(bounceableAddress));
    log.info(
        "new wallet {} with status {} and balance: {}",
        contract.getName(),
        status,
        formatNanoValue(balance));

    assertThat(balance.longValue()).isLessThan(Utils.toNano(0.02).longValue());

    log.info("seqno {}", contract.getSeqno());
    log.info("pubkey {}", contract.getPublicKey());
    log.info("transactions {}", contract.getTransactions());
  }

  @Test
  public void testWalletV1R3AdnlLiteClient() throws Exception {

    TweetNaclFast.Signature.KeyPair keyPair = Utils.generateSignatureKeyPair();
    AdnlLiteClient adnlLiteClient =
        AdnlLiteClient.builder().configUrl(Utils.getGlobalConfigUrlTestnetGithub()).build();
    WalletV1R3 contract =
        WalletV1R3.builder().adnlLiteClient(adnlLiteClient).keyPair(keyPair).build();

    String nonBounceableAddress = contract.getAddress().toNonBounceable();
    String bounceableAddress = contract.getAddress().toBounceable();

    log.info("non-bounceable address {}", nonBounceableAddress);
    log.info("    bounceable address {}", bounceableAddress);
    String status = adnlLiteClient.getAccountStatus(Address.of(bounceableAddress));
    log.info("account status {}", status);

    // top up new wallet using test-faucet-wallet
    BigInteger balance =
        TestnetFaucet.topUpContract(
            adnlLiteClient, Address.of(nonBounceableAddress), Utils.toNano(0.1));
    log.info("new wallet {} balance: {}", contract.getName(), formatNanoValue(balance));

    ExtMessageInfo extMessageInfo = contract.deploy();
    assertThat(extMessageInfo.getError().getCode()).isZero();

    contract.waitForDeployment(20);

    // transfer coins from new wallet (back to faucet)
    WalletV1R3Config config =
        WalletV1R3Config.builder()
            .seqno(contract.getSeqno())
            .destination(Address.of(TestnetFaucet.BOUNCEABLE))
            .amount(Utils.toNano(0.08))
            .comment("testNewWalletV1R2")
            .build();

    extMessageInfo = contract.send(config);
    assertThat(extMessageInfo.getError().getCode()).isZero();

    contract.waitForBalanceChange(30);

    balance = contract.getBalance();
    status = adnlLiteClient.getAccountStatus(Address.of(bounceableAddress));
    log.info(
        "new wallet {} with status {} and balance: {}",
        contract.getName(),
        status,
        formatNanoValue(balance));

    assertThat(balance.longValue()).isLessThan(Utils.toNano(0.02).longValue());

    log.info("seqno {}", contract.getSeqno());
    log.info("pubkey {}", contract.getPublicKey());
    log.info("transactions {}", contract.getTransactionsTlb());
  }
}
