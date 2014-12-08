#shivsock

A json websocket protocol providing subaddressing/subsockets, RPC, and batching.

Happens to be symmetrical.

###Implementations

language |
--- | ---
scala | there is a play framework version, but porting to any other websocket hosts that use akka would be trivial.
browser side | shiver.js

##why do we need another protocol?

In summary: Directly addressing entities on a server is somehow a provision that no other framework provides. Meanwhile it is transparent to me that competing protocols' decisions to embed pub/sub into the expected API is mad.

Entity Addressing: these other protocols do allow you to *request* an entity ID and use that to engage in direct communication, but the fact that this ID is provided by the protocol implementation rather than the server itself closes certain doors. Say you were engaged through a reverse proxy which wanted to switch your messages to another recipient, what then? Either your entity IDs would be meaningless on the other server or you'd have to get new entity IDs before resuming conversation. Do you enjoy writing reams of client code for edge cases? What about mailboxes? How can entities on the client side get the messages due of them if their ID changes every time the client refreshes the page? It does not make sense to have the protocol handling ID allocation.

A whole lot of reactive applications need pub sub, but to put forth a system that only provides subscriptions as a pure function of a server and a text string is to insult the opportunity space. No app, not even your toy example chat applications, should settle for such a meager provision. I'm planning on building systems that enable clients to subscribe to intervals, to arbitrary expressions of set intersections and unions, properties of remote standard data structures, and full blown CRDTs, and I do not forsee defining an `emit` method.

###spec
Simple one way message: `{o:<payload>}`

Batched sequence of simple one way messages: `[{o:<payload>},{o:<payload>},{o:<payload>}]`

Request: `{rq:<integer>,o:<payload>}`

Which shall expect a response in turn of a similar nature, for which the rp field has the same value as the outgoing rq field did: `{rp:<integer>,o:<payload>}`

A request through this channel, from our end, would then be `{to:<entity name>, from:<local entity name>, rq:<id>, o:<payload>}`

We would then expect `{to:<local entity name>, from:<entity name>, rp:<id>, o:<reply>}`

If the to and from fields are not specified, a default value of `""` is assumed. Servers may wish to install a client-specific socket guard at `""`, it is not required that names have the same meaning between different clients, everyone's `""` may be different.

If the target does not exist(or if you don't have permission to know about it): `{rp:<integer>, to:<local entity name>, from:<entity name>, no:"this entity does not exist"}`

Or, if the transmission were to go wrong in some other way

Request IDs only need to be unique among the request IDs issuing from their entity. The system must be able to deal with different entities using the same IDs at the same time.

The implementation must not depend on the fields being in a particular order.

☐