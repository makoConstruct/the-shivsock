Shivsock API for Play
=====================================

Shivsock is a symmetrical [websocket subprotocol](https://github.com/makoConstruct/shivsockProtocol) supplying entity addressing, RPC, and batching. I'll elaborate:

* **Remote Procedure Calls**. You can query an entity and the shivsock API will provide you with an es6 Promise(or a shim) that allows your code to immediately start to discuss the asynchronous return value of the query.

* **entity subaddressing**. All messages are addressed to a particular entity on the server from a particular entity on the client. These entities may operate in complete isolation, their communications will never interfere with any others.

* **batching**. If multiple messages are sent from the client at the same time, they will be stuck together and sent in the same TCP packet a few milliseconds later to save badnwidth.

* **no mediocre pub/sub**. Pub/sub overloads a standard with the wrong kind of features. I intend on providing sophisticated pub/sub features for Shivsock, but they will not be built *into* the shivsock protocol, they will be built *on top of it*.

I believe these features make an ideal basis for complex web applications. The Play Shivsock API makes communicating between remote entities easy ( so long as you already know scala and akka ;] ).

Tutorial by Example coming soon. Try the demo app if you're especially curious.
