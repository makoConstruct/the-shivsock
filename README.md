#shivsock

Shivsock is a symmetrical websocket subprotocol supplying entity addressing, RPC, and batching.

* **Remote Procedure Calls**. You can query an entity and any good shivsock API will immediately return an es6 Promise(or a shim) by which your code to immediately start to discuss the response that will come through it.

* **entity subaddressing**. All messages are addressed to a particular entity on the server from a particular entity on the client. These entities may operate in complete isolation, their communications will never interfere with any others.

* **batching**. If multiple messages are sent from the client at the same time, they will be stuck together and sent in the same TCP packet a few milliseconds later to save bandwidth.

I believe these features make an ideal basis for complex web applications.

###Implementations

language | coverage
--- | ---
scala | there is a play framework version, but porting to any other websocket hosts that use akka would be trivial.
browser side | shiver.js

##why do we need another protocol?

To be honest, I'm not entirely sure we do. I'm not aware of a good alternative, but, full disclosure, I could not be bothered researching every little feature of every mostly inferior existing protocol when I could just sit down, code it my way, and get exactly what I want with no bullshit. I'll update this section in the event that I'm ever able to articulate an unambiguous position of superiority.

###spec
Simple one way message: `{o:<payload>}`

Batched sequence of simple one way messages: `[{o:<payload>},{o:<payload>},{o:<payload>}]`

Request: `{rq:<integer>,o:<payload>}`

Which shall expect a response in turn of a similar nature, for which the rp field has the same value as the outgoing rq field did: `{rp:<integer>,o:<payload>}`

A request from a named entity on our end(rather than the default `""`), would then be `{to:<entity name>, from:<local entity name>, rq:<id>, o:<payload>}`

We would then expect `{to:<local entity name>, from:<entity name>, rp:<id>, o:<reply>}`

If the `to` and `from` fields are not specified, a default value of `""` is assumed. Servers may wish to install a client-specific socket guard at `""`, it is not required that names have the same meaning between different clients, everyone's `""` may have different state.

If the target does not exist(or if you don't have permission to know about it): `{rp:<integer>, to:<local entity name>, from:<entity name>, no:"this entity does not exist"}`

A `no` field in place of an `o` field denotes an error, and should cause the Promise returned from the query to fail.

For any two distinct requests, if their source is the same, their request IDs must be different. This is the only law governing the allocation of request IDs. Differing entities may simultaniously query with identical IDs, and the messages must not collide in this case.

The implementation must not depend on the fields being in a particular order.

☐