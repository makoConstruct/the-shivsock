#shivsock

Shivsock is a symmetrical websocket subprotocol supplying entity addressing, RPC, and batching.

* **Remote Procedure Calls**. You can query an entity and the shivsock API will provide you with an es6 Promise(or a shim) that allows your code to immediately start to discuss the asynchronous return value of the query.

* **entity subaddressing**. All messages are addressed to a particular entity on the server from a particular entity on the client. These entities may operate in complete isolation, their communications will never interfere with any others.

* **batching**. If multiple messages are sent from the client at the same time, they will be stuck together and sent in the same TCP packet a few milliseconds later to save badnwidth.

* **no mediocre pub/sub**. Pub/sub overloads a standard with the wrong kind of features. I intend on providing sophisticated pub/sub features for Shivsock, but they will not be built *into* the shivsock protocol, they will be built *on top of it*.

I believe these features make an ideal basis for complex web applications.

###Implementations

language | coverage
--- | ---
scala | there is a play framework version, but porting to any other websocket hosts that use akka would be trivial.
browser side | shiver.js

##why do we need another protocol?

To be honest, I'm not entirely sure we do. I'm not aware of a good alternative, but to be honest I just couldn't be bothered spending time researching the minutae of a raft of mostly inferior protocols when I could just sit down, code it my way, and get exactly what I want with no bullshit. You know how it is. I'll update this section in the event that I'm ever able to articulate a position of superiority.

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