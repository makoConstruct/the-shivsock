#shivsock

Shivsock is a symmetrical websocket subprotocol supplying entity addressing, RPC(with error propagation), and batching.

* **Remote Procedure Calls**. You can query an entity and any good shivsock API will immediately return an es6 Promise(or a shim) containing the result.

* **entity subaddressing**. All messages are addressed to a particular entity on the server from a particular entity on the client. These entities may operate in complete isolation, their communications will never interfere with any others.

* **batching**. If multiple messages are sent from the client at the same time, they will be packaged together and sent in the same TCP packet a few milliseconds later to save bandwidth. (configurable)

I believe these features make an great basis for complex web applications.

###Implementations

language | coverage
--- | ---
scala | there is a play framework version, but porting to any other websocket hosts that use akka would be trivial.
browser side | shiver.js

##why do we need another protocol?

We probably don't. [WAMP](https://wamp.ws) is very good. It was simply easier for me to whip up something simple that did exactly what I wanted and nothing more(does WAMP really need pubsub to be a part of the protocol? Does it do batching? Will I ever run into scaling issues with the implementations?) than it was to figure out how to get existing java wamp implementations into a playframework actor system.

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