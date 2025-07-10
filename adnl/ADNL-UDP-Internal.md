## ADNL UDP

Originally written by Oleg Baranov https://github.com/xssnick/ton-deep-doc/blob/master/ADNL-UDP-Internal.md

ADNL over UDP is used by nodes and TON components to communicate with each other. It is a low-level protocol on top of which other, higher-level TON protocols such as DHT and RLDP operate. In this article, we will analyze how ADNL over UDP works for basic data exchange between nodes, tunneling and anonymizing traffic will be discussed in a separate article.

Unlike ADNL over TCP, in the UDP implementation, the handshake takes place in a different form, and an additional layer is used in the form of channels, but other principles are similar: keys are also generated based on our private key and the server's public key, which is known in advance from the config or received from other network nodes.

In the UDP version of ADNL, the connection is established simultaneously with the receipt of initial data from the client, the key is calculated and the creation of the channel is confirmed, if the initiator sent a message about the desire to create a channel.

Within one connection, several channels can be opened, they are needed for data isolation. Each channel has its own ID and encryption key. But usually, for basic interaction, only one channel is used, which is created along with the first request.

### Packet arrangement and information exchange

##### First exchange of data
Let's analyze the initialization of the connection with the DHT node and obtaining a signed list of its addresses in order to understand how the data exchange works.

Find the node you like in [mainnet config](https://ton-blockchain.github.io/global.config.json), in the `dht.nodes` section. For example:
```json
{
  "@type": "dht.node",
  "id": {
    "@type": "pub.ed25519",
    "key": "fZnkoIAxrTd4xeBgVpZFRm5SvVvSx7eN3Vbe8c83YMk="
  },
  "addr_list": {
    "@type": "adnl.addressList",
    "addrs": [
      {
        "@type": "adnl.address.udp",
        "ip": 1091897261,
        "port": 15813
      }
    ],
    "version": 0,
    "reinit_date": 0,
    "priority": 0,
    "expire_at": 0
  },
  "version": -1,
  "signature": "cmaMrV/9wuaHOOyXYjoxBnckJktJqrQZ2i+YaY3ehIyiL3LkW81OQ91vm8zzsx1kwwadGZNzgq4hI4PCB/U5Dw=="
}
```

1. Let's take its key ED25519, `fZnkoIAxrTd4xeBgVpZFRm5SvVvSx7eN3Vbe8c83YMk`, decode from base64
2. Take its IP address `1091897261` and translate it into an understandable format using [service](https://www.browserling.com/tools/dec-to-ip), get `65.21.7.173`
3. Combine with the port, get `65.21.7.173:15813` and establish a UDP connection.

We want to open a channel for constant exchange of information with the node, and as the main task - to receive a list of subscribed addresses from it. To do this, we will generate 2 messages, the first - [create a channel](https://github.com/ton-blockchain/ton/blob/master/tl/generate/scheme/ton_api.tl#L129):
```
adnl.message.createChannel key:int256 date:int = adnl.Message
```
Here we have 2 parameters - key and date. As a date, we will specify the current unix timestamp. And for the key - we need to generate a new ED25519 private / public key pair, specifically for the channel, they will be used for initialization of [public encryption key](/ADNL-TCP-Liteserver.md#%D0%BF%D0%BE%D0%BB%D1%83%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-%D0%BE%D0%B1%D1%89%D0%B5%D0%B3%D0%BE-%D0%BA%D0%BB%D1%8E%D1%87%D0%B0-%D0%BF%D0%BE-ecdh). We will specify our generated public key in the `key` parameter of the message, and just remember the private one for now.

Serialize the filled TL structure and get:
```
bbc373e6                                                         -- TL ID adnl.message.createChannel 
d59d8e3991be20b54dde8b78b3af18b379a62fa30e64af361c75452f6af019d7 -- key
555c8763                                                         -- date
```

Next, let's move on to our main query - [get a list of addresses](https://github.com/ton-blockchain/ton/blob/master/tl/generate/scheme/ton_api.tl#L198). To execute it, we first need to serialize its TL structure:
```
dht.getSignedAddressList = dht.Node
```
It has no parameters, so we just serialize it. Get `ed4879a9`.

Next, since this is a higher level request of the DHT protocol, we need to first wrap it in an `adnl.message.query` TL structure:
```
adnl.message.query query_id:int256 query:bytes = adnl.Message
```
As `query_id` we generate random 32 bytes, as `query` we use our main request, [wrapped as an array of bytes](/TL.md#кодирование-bytes-в-tl). Get:
```
7af98bb4                                                         -- TL ID adnl.message.query
d7be82afbc80516ebca39784b8e2209886a69601251571444514b7f17fcd8875 -- query_id
04 ed4879a9 000000                                               -- query
```
 
###### Building the packet

All data exchange is carried out using packets, the content of which is [TL structure](https://github.com/ton-blockchain/ton/blob/master/tl/generate/scheme/ton_api.tl#L81):
```
adnl.packetContents 
  rand1:bytes                                     -- random 7 or 15 bytes
  flags:#                                         -- bit flags, used to determine the presence of fields further
  from:flags.0?PublicKey                          -- sender's public key
  from_short:flags.1?adnl.id.short                -- sender's ID
  message:flags.2?adnl.Message                    -- message (used if there is only one message)
  messages:flags.3?(vector adnl.Message)          -- messages (if there are > 1)
  address:flags.4?adnl.addressList                -- list of our addresses
  priority_address:flags.5?adnl.addressList       -- priority list of our addresses
  seqno:flags.6?long                              -- packet sequence number
  confirm_seqno:flags.7?long                      -- sequence number of the last packet received
  recv_addr_list_version:flags.8?int              -- address version 
  recv_priority_addr_list_version:flags.9?int     -- priority address version
  reinit_date:flags.10?int                        -- connection reinitialization date (counter reset)
  dst_reinit_date:flags.10?int                    -- connection reinitialization date from the last received packet
  signature:flags.11?bytes                        -- signature
  rand2:bytes                                     -- random 7 or 15 bytes
        = adnl.PacketContents
        
```

Once we have serialized all the messages we want to send, we can start building the packet. Packets to be sent to a channel differ in content from packets that are sent before the channel is initialized. First, let's analyze the main package, which is used for initialization.

During the initial exchange of information, outside the channel, the serialized content structure of the packet is prefixed with the public key of the server - 32 bytes. Our public key is 32 bytes, the sha256 hash of the serialized TL of the content structure of the package - 32 bytes. The content of the packet is encrypted using the [shared key](/ADNL-TCP-Liteserver.md#%D0%BF%D0%BE%D0%BB%D1%83%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-%D0%BE%D0%B1%D1%89%D0%B5%D0%B3%D0%BE-%D0%BA%D0%BB%D1%8E%D1%87%D0%B0-%D0%BF%D0%BE-ecdh), obtained from our private key and the public key of the server.

Serialize our package content structure, and parse it byte by byte:
```
89cd42d1                                                               -- TL ID adnl.packetContents
0f 4e0e7dd6d0c5646c204573bc47e567                                      -- rand1, 15 (0f) random bytes
d9050000                                                               -- flags (0x05d9) -> 0b0000010111011001
                                                                       -- from (present because flag's zero bit = 1)
c6b41348                                                                  -- TL ID pub.ed25519
   afc46336dd352049b366c7fd3fc1b143a518f0d02d9faef896cb0155488915d6       -- key:int256
                                                                       -- messages (present because flag's third bit = 1)
02000000                                                                  -- vector adnl.Message, size = 2 messages   
   bbc373e6                                                                  -- TL ID adnl.message.createChannel
   d59d8e3991be20b54dde8b78b3af18b379a62fa30e64af361c75452f6af019d7          -- key
   555c8763                                                                  -- date (date of creation)
   
   7af98bb4                                                                  -- TL ID [adnl.message.query](/)
   d7be82afbc80516ebca39784b8e2209886a69601251571444514b7f17fcd8875          -- query_id
   04 ed4879a9 000000                                                        -- query (bytes size 4, padding 3)
                                                                       -- address (present because flag's fourth bit = 1), without TL ID since it is specified explicitly
00000000                                                                  -- addrs (empty vector, because we are in client mode and do not have an address on wiretap)
555c8763                                                                  -- version (usually initialization date)
555c8763                                                                  -- reinit_date (usually initialization date)
00000000                                                                  -- priority
00000000                                                                  -- expire_at

0100000000000000                                                       -- seqno (present because flag's sixth bit = 1)
0000000000000000                                                       -- confirm_seqno (present because flag's seventh bit = 1)
555c8763                                                               -- recv_addr_list_version (present because flag's eighth bit = 1, usually initialization date)
555c8763                                                               -- reinit_date (present because flag's tenth bit = 1, usually initialization date)
00000000                                                               -- dst_reinit_date (present because flag's tenth bit = 1)
0f 2b6a8c0509f85da9f3c7e11c86ba22                                      -- rand2, 15 (0f) random bytes
```
After serialization - we need to sign the resulting byte array with our private ED25519 client (not channel) key, which we generated and remembered before. After we have received the signature (64 bytes in size), we need to add it to the packet, serialize it again, but add the 11th bit to the flag, which means the presence of the signature:
```
89cd42d1                                                               -- TL ID adnl.packetContents
0f 4e0e7dd6d0c5646c204573bc47e567                                      -- rand1, 15 (0f) random bytes
d90d0000                                                               -- flags (0x0dd9) -> 0b0000110111011001
                                                                       -- from (present because flag's zero bit = 1)
c6b41348                                                                  -- TL ID pub.ed25519
   afc46336dd352049b366c7fd3fc1b143a518f0d02d9faef896cb0155488915d6       -- key:int256
                                                                       -- messages (present because flag's third bit = 1)
02000000                                                                  -- vector adnl.Message, size = 2 message   
   bbc373e6                                                                  -- TL ID adnl.message.createChannel
   d59d8e3991be20b54dde8b78b3af18b379a62fa30e64af361c75452f6af019d7          -- key
   555c8763                                                                  -- date (date of creation)
   
   7af98bb4                                                                  -- TL ID adnl.message.query
   d7be82afbc80516ebca39784b8e2209886a69601251571444514b7f17fcd8875          -- query_id
   04 ed4879a9 000000                                                        -- query (bytes size 4, padding 3)
                                                                       -- address (present because flag's fourth bit = 1), without TL ID since it is specified explicitly
00000000                                                                  -- addrs (empty vector, because we are in client mode and do not have an address on wiretap)
555c8763                                                                  -- version (usually initialization date)
555c8763                                                                  -- reinit_date (usually initialization date)
00000000                                                                  -- priority
00000000                                                                  -- expire_at

0100000000000000                                                       -- seqno (present because flag's sixth bit = 1)
0000000000000000                                                       -- confirm_seqno (present because flag's seventh bit = 1)
555c8763                                                               -- recv_addr_list_version (present because flag's eighth bit = 1, usually initialization date)
555c8763                                                               -- reinit_date (present because flag's tenth bit = 1, usually initialization date)
00000000                                                               -- dst_reinit_date (present because flag's tenth bit = 1)
40 b453fbcbd8e884586b464290fe07475ee0da9df0b8d191e41e44f8f42a63a710    -- signature (present because flag's eleventh bit = 1), (bytes size 64, padding 3)
   341eefe8ffdc56de73db50a25989816dda17a4ac6c2f72f49804a97ff41df502    --
   000000                                                              --
0f 2b6a8c0509f85da9f3c7e11c86ba22                                      -- rand2, 15 (0f) random bytes
```
Now we have an assembled, signed and serialized packet, which is an array of bytes. For subsequent verification of its integrity by the recipient, we need to calculate its sha256 hash. For example, let this be `408a2a4ed623b25a2e2ba8bbe92d01a3b5dbd22c97525092ac3203ce4044dcd2`.

Now let's encrypt the content of our package with the AES-CTR cipher, using [shared key](/ADNL-TCP-Liteserver.md#%D0%BF%D0%BE%D0%BB%D1%83%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-%D0%BE%D0%B1%D1%89%D0%B5%D0%B3%D0%BE-%D0%BA%D0%BB%D1%8E%D1%87%D0%B0-%D0%BF%D0%BE-ecdh), obtained from our private key and the public key of the server (not the channel key).

We are almost ready for sending, it remains to [calculate ID](/ADNL-TCP-Liteserver.md#получение-айди-ключа) of ED25519 server key and connect everything together:
```
daa76538d99c79ea097a67086ec05acca12d1fefdbc9c96a76ab5a12e66c7ebb  -- Server Key ID
afc46336dd352049b366c7fd3fc1b143a518f0d02d9faef896cb0155488915d6  -- our public key
408a2a4ed623b25a2e2ba8bbe92d01a3b5dbd22c97525092ac3203ce4044dcd2  -- sha256 content hash (before encryption)
...                                                               -- encrypted content of the packet
```
Now we can send the received packet to the server via UDP, and wait for a response. 

In response, we will receive a packet similar in structure, but with different messages. It will consist of:
```

68426d4906bafbd5fe25baf9e0608cf24fffa7eca0aece70765d64f61f82f005  -- ID of our key
2d11e4a08031ad3778c5e060569645466e52bd1bd2c7b78ddd56def1cf3760c9  -- server public key, for shared key
f32fa6286d8ae61c0588b5a03873a220a3163cad2293a5dace5f03f06681e88a  -- sha256 content hash (before encryption)
...                                                               -- the encrypted content of the packet
```

The deserialization of the package from the server is as follows:
1. We check the ID of the key from the packet to understand that the packet is for us.
2. Using the public key of the server from the packet and our private key, we create a public key and decrypt the content of the package
3. Compare the sha256 hash sent to us with the hash received from the decrypted data, they must match
4. Start deserializing the packet content using the `adnl.packetContents` TL schema

The content of the packet will look like this:
```
89cd42d1                                                               -- TL ID adnl.packetContents
0f 985558683d58c9847b4013ec93ea28                                      -- rand1, 15 (0f) random bytes
ca0d0000                                                               -- flags (0x0dca) -> 0b0000110111001010
daa76538d99c79ea097a67086ec05acca12d1fefdbc9c96a76ab5a12e66c7ebb       -- from_short (because flag's first bit = 1)
02000000                                                               -- messages (present because flag's third bit = 1)
   691ddd60                                                               -- TL ID adnl.message.confirmChannel 
   db19d5f297b2b0d76ef79be91ad3ae01d8d9f80fab8981d8ed0c9d67b92be4e3       -- key (server channel public key)
   d59d8e3991be20b54dde8b78b3af18b379a62fa30e64af361c75452f6af019d7       -- peer_key (our public channel key)
   94848863                                                               -- date
   
   1684ac0f                                                               -- TL ID adnl.message.answer 
   d7be82afbc80516ebca39784b8e2209886a69601251571444514b7f17fcd8875       -- query_id
   90 48325384c6b413487d99e4a08031ad3778c5e060569645466e52bd5bd2c7b       -- answer (the answer to our request, we will analyze its content in an article about DHT)
      78ddd56def1cf3760c901000000e7a60d67ad071541c53d0000ee354563ee       --
      35456300000000000000009484886340d46cc50450661a205ad47bacd318c       --
      65c8fd8e8f797a87884c1bad09a11c36669babb88f75eb83781c6957bc976       --
      6a234f65b9f6e7cc9b53500fbe2c44f3b3790f000000                        --
      000000                                                              --
0100000000000000                                                       -- seqno (present because flag's sixth bit = 1)
0100000000000000                                                       -- confirm_seqno (present because flag's seventh bit = 1)
94848863                                                               -- recv_addr_list_version (present because flag's eighth bit = 1, usually initialization date)
ee354563                                                               -- reinit_date (present because flag's tenth bit = 1, usually initialization date)
94848863                                                               -- dst_reinit_date (present because flag's tenth bit = 1)
40 5c26a2a05e584e9d20d11fb17538692137d1f7c0a1a3c97e609ee853ea9360ab6   -- signature (present because flag's eleventh bit = 1), (bytes size 64, padding 3)
   d84263630fe02dfd41efb5cd965ce6496ac57f0e51281ab0fdce06e809c7901     --
   000000                                                              --
0f c3354d35749ffd088411599101deb2                                      -- rand2, 15 (0f) random bytes
```
The server responded to us with two messages: `adnl.message.confirmChannel` and `adnl.message.answer`. With `adnl.message.answer` everything is simple, this is the answer to our request `dht.getSignedAddressList`, we will analyze it in the article about DHT.

Let's focus on `adnl.message.confirmChannel`, which means that the server has confirmed the creation of the channel and sent us its public channel key. Now, having our private channel key and the server's public channel key, we can calculate the [shared key](/ADNL-TCP-Liteserver.md#%D0%BF%D0%BE%D0%BB%D1%83%D1%87%D0%B5%D0%BD%D0%B8%D0%B5-%D0%BE%D0%B1%D1%89%D0%B5%D0%B3%D0%BE-%D0%BA%D0%BB%D1%8E%D1%87%D0%B0-%D0%BF%D0%BE-ecdh).

Now when we have calculated the shared channel key, we need to make 2 keys out of it - one for encrypting outgoing messages, the other for decrypting incoming messages. Making 2 keys out of it is quite simple, the second key is equal to the common key written in reverse order. Example:
```
Shared key : AABB2233

First key: AABB2233
Second key: 3322BBAA
```
It remains to determine which key to use for what, we can do this by comparing the id of our public key with the id of the public key of the server, converting them to a numerical form - uint256. This approach is used to ensure that both the server and the client determine which key to use for what. If the server uses the first key for encryption, then with this approach the client will always use it for decryption.

The terms of use are:
```
The server id is smaller than our id:
Encryption: First Key
Decryption: Second Key

The server id is larger than our id:
Encryption: Second Key
Decryption: First Key

If the ids are equal (nearly impossible):
Encryption: First Key
Decryption: First Key
```
[[Implementation example]](https://github.com/xssnick/tonutils-go/blob/udp-rldp-2/adnl/adnl.go#L502)

##### Data exchange in a channel

All subsequent packet exchange will occur within the channel, and new keys will be used for encryption. Let's send the same `dht.getSignedAddressList` request inside a freshly created channel to see the difference.

Let's collect the package content for the channel using the same `adnl.packetContents` structure:
```
89cd42d1                                                               -- TL ID adnl.packetContents
0f c1fbe8c4ab8f8e733de83abac17915                                      -- rand1, 15 (0f) random bytes
c4000000                                                               -- flags (0x00c4) -> 0b0000000011000100
                                                                       -- message (because second bit = 1)
7af98bb4                                                                  -- TL ID adnl.message.query
fe3c0f39a89917b7f393533d1d06b605b673ffae8bbfab210150fe9d29083c35          -- query_id
04 ed4879a9 000000                                                        -- query (our dht.getSignedAddressList packed in bytes with padding 3)
0200000000000000                                                       -- seqno (because flag's sixth bit = 1), 2 because it is our second message
0100000000000000                                                       -- confirm_seqno (flag's seventh bit = 1), 1 because it is the last seqno received from the server
07 e4092842a8ae18                                                      -- rand2, 7 (07) random bytes
```
The packets in a channel are quite simple and essentially consist of sequences (seqno) and the messages themselves. 

After serialization, like last time, we calculate the sha256 hash from the content. Then we encrypt the content of the packet using the key intended for the outgoing packets of the channel. [Calculate](/ADNL-TCP-Liteserver.md#дополнительные-технические-детали-хендшейка) `pub.aes` ID of encryption key of our outgoing messages, and we collect our package:
```
bcd1cf47b9e657200ba21d94b822052cf553a548f51f539423c8139a83162180 -- ID of encryption key of our outgoing messages 
6185385aeee5faae7992eb350f26ba253e8c7c5fa1e3e1879d9a0666b9bd6080 -- sha256 content hash (before encryption)
...                                                              -- the encrypted content of the package
```
We send a packet via UDP and wait for a response. In response, we will receive a packet of the same type as we sent (the same fields), but with the answer to our request `dht.getSignedAddressList`.

#### Other message types
For basic communication, messages like `adnl.message.query` and `adnl.message.answer` are used, which we discussed above, but other types of messages are also possible for some situations, which we will discuss in this section.

#####adnl.message.part
This message type is a piece of one of the other possible message types, such as `adnl.message.answer`. This method of transmission is used when the message is too large to be transmitted in a single UDP datagram.
```
adnl.message.part 
hash:int256            -- sha256 hash of the original message
total_size:int         -- original message size
offset:int             -- offset according to the beginning of the original message
data:bytes             -- piece of data of the original message
   = adnl.Message;
```
Thus, in order to assemble the original message, we need to get several parts and, according to the offsets, add them into a single byte array. And then process it as a message (according to the prefix in this array).

##### adnl.message.custom
```
adnl.message.custom data:bytes = adnl.Message;
```
Such messages are used when the logic at the higher level does not correspond to the request-response format, messages of this type allow you to completely move the processing to the higher level, since the message carries only an array of bytes, without query_id and other fields. Messages of this type are used, for example, in RLDP, since there can be only one response to many requests and this logic is controlled by RLDP itself.

##### Conclusion

Further data exchange takes place on the basis of the logic discussed in this article,
but the content of the packets depends on higher level protocols such as DHT and RLDP.
