#~mako.shiv(javascript)

@shiverjs_version = '1.0'

@actuallyInstanceOf = (v, constructor)->
	#for the primitives
	(not v? and not constructor?) or v.constructor is constructor or v instanceof constructor #for the rest
@intARGBColorToString = (i)->
	"rgba(" + ((i >> 16) & 0xff) + ", " + ((i >> 8) & 0xff) + ", " + (i & 0xff) + ", " + (((i >> 24) & 0xff) / 255) + ")"

@Storage::setObject = (key, value)->
	@setItem key, JSON.stringify(value)
	return

@Storage::getObject = (key, defaultValue)->
	value = @getItem(key)
	(if value then JSON.parse(value) else defaultValue)

@indexOf = (arr, el)->
	i = 0

	while i < arr.length
		return i  if arr[i] is el
		++i
	-1

@timeoutSet = (t,f)-> setTimeout(f,t) #more readable. Especially in coffeescript

@imul = Math.imul or (a, b)->
	ah = (a >>> 16) & 0xffff
	al = a & 0xffff
	bh = (b >>> 16) & 0xffff
	bl = b & 0xffff
	(al * bl) + (((ah * bl + al * bh) << 16) >>> 0) | 0

@String::hashCode = ->
	hash = 0
	return hash  if @length is 0
	i = 0
	while i < @length
		ch = @charCodeAt(i)
		hash = (((hash << 5) - hash) + ch) | 0
		i++
	hash

@CanvasRenderingContext2D::pathRect = (x, y, w, h)->
	@moveTo x, y
	@lineTo x + w, y
	@lineTo x + w, y + h
	@lineTo x, y + h
	@closePath()
	return

@Copyable = -> #interface
@Copyable.detect = (ob)->
	!!ob.copy and typeof ob.copy is "function"

#credit to emberjs

# var typeOf = function(o){return typeof o}
_copy = (obj, deep, seen, copies)->
	ret = undefined
	loc = undefined
	key = undefined
	
	# primitive data types are immutable, just return them.
	return obj  if "object" isnt typeof obj or obj is null
	
	# avoid cyclical loops
	return copies[loc]  if deep and (loc = indexOf(seen, obj)) >= 0
	
	# IMPORTANT: this specific test will detect a native array only. Any other
	# object will need to implement Copyable.
	if typeof obj is "array"
		ret = obj.slice()
		if deep
			loc = ret.length
			ret[loc] = _copy(ret[loc], deep, seen, copies)  while --loc >= 0
	else if Copyable and Copyable.detect(obj)
		ret = obj.copy(deep, seen, copies)
	else if obj instanceof Date
		ret = new Date(obj.getTime())
	else
		ret = {}
		for key of obj
			continue  unless obj.hasOwnProperty(key)
			
			# Prevents browsers that don't respect non-enumerability from
			# copying internal Ember properties
			continue  if key.substring(0, 2) is "__"
			ret[key] = (if deep then _copy(obj[key], deep, seen, copies) else obj[key])
	if deep
		seen.push obj
		copies.push ret
	ret

###*
Creates a clone of the passed object. This function can take just about
any type of object and create a clone of it, including primitive values
(which are not actually cloned because they are immutable).

If the passed object implements the `clone()` method, then this function
will simply call that method and return the result.

@method copy
@for Ember
@param {Object} obj The object to clone
@param {Boolean} deep If true, a deep copy of the object is made
@return {Object} The cloned object
###
@copy = (obj, deep) ->
	
	# fast paths
	return obj  if "object" isnt typeof obj or obj is null # can't copy primitives
	return obj.copy(deep)  if Copyable and Copyable.detect(obj)
	_copy obj, deep, (if deep then [] else null), (if deep then [] else null)

#end credit to emberjs

# the shift by 0 fixes the sign on the high part
# the final |0 converts the unsigned value into a signed value
@lcgrng = (v) -> (imul(v, 22695477) + 1)|0


#~ I used to use my own Promises. I guess I knew intellectually that es6 was coming but I could believe it in my heart. Now I am ready for the tomorrow.
@Promise = window.Promise || ES6Promise.Promise
if not Promise then throw new Error "shiver.js will not work. Your browser is an obstacle to progress and needs to have the es6 Promise polyfills ( https://github.com/jakearchibald/es6-promise ) included in the page, or be destroyed."


@awaitTime = (milliseconds) ->
	new Promise (g, b)-> setTimeout g, milliseconds

#just resty things:
@awaitRequest = (httpType, address, data, contentType) ->
	new Promise (g,b)->
		q = new XMLHttpRequest()
		q.open httpType, address, true
		q.setRequestHeader "Content-Type", contentType  if contentType
		q.onreadystatechange = (ev) ->
			if q.readyState is 4
				if q.status is 200
					o = undefined
					try
						o = JSON.parse(q.responseText)
					catch e
						b "json malformed. wtf, server?"
						return
					g o
				else
					b "problem fetching json. " + q.status + "."
			return
		q.ontimeout = (ev) ->
			b "ajax query took too long. Network problem?"
			return
		q.send data or null
@postJsonGetJson = (address, data) -> #returns an Promise<json of response>.
	awaitRequest "POST", address, JSON.stringify(data), "application/json"
@postDataGetJson = (address, data) -> #returns an Promise<json of response>.
	awaitRequest "POST", address, data
@fetchJson = (address) -> #returns an Promise<json of response>.
	awaitRequest "GET", address, null
@getConnectedShivSock = (address, callback) -> #passes along a ShivSock
	new Promise (g,b)->
		WS = WebSocket or MozWebSocket
		ws = new WS(address)
		# ws = new WS(address, ["shivsock"])
		ws.onopen = -> g new ShivSock(ws)
		ws.onerror = (e)-> b e

#implements the shivsock protocol, that is subaddressing, rpc and batching
#acquire through getConnectedShivSock (it's much simpler to work with if you can assume the socket is connected as soon as you get it)
defaultSock = null
class @ShivSock
	constructor: (@ws, defaultEntity)->
		if not defaultSock
			defaultSock = @
		@entities =
			if defaultEntity
				{ "": defaultEntity }
			else
				{}
		@batch = []
		self = this
		@ws.onclose = (msgo)->
			console.error "the websocket connection was broken. (" + msgo + ")."
			return
		# if(this.attemptReconnection){
		# function tribonacci(){ //sequence used to decide how long to wait between each attempt to reconnect a broken connection. The tribonacci sequence is guaranteed to provide the optimal balance between user waiting time and network load. The magical healing properties of the sequence has always been known to the ancients.
		# 	var i = 0;
		# 	var ns = [0, 0, 1];
		# 	return function(){
		# 		var r = ns.reduce(function(a,b){return a + b});
		# 		ns[i] = r;
		# 		i = (i + 1)%ns.length;
		# 		return r;
		# 	}
		# }
		# 	var ts = tribonacci();
		# 	function waiting(){
		# 		var pauseSeconds = ts();
		# 		console.error("Attempting to reconnect in "+pauseSeconds+".");
		# 		var nw = setTimeout(function(){
		
		# 		}, pauseSeconds*1000);
		
		# 	}
		# 	waiting();
		# }
		@ws.onmessage = @incoming

		window.addEventListener "beforeunload", (e)=>
			if @ws
				@ws.close 1000, "page closed"
				@ws = null
	wsDividerString = '←≑→'
	ws: null
	newId: 1
	batch: null
	batchingLatency: 0
	batchingIntervalID: 0
	hangingOutFor: null
	tryingToSuffice: 0
	entities: null
	defaultTimeout: 6000
	registerEntity: (entity)->
		if entity.name.indexOf(wsDividerString) >= 0
			throw new Error "why the hell are you trying to coin an address that has the divider string in it."
		else if @entities[entity.name]
			throw new Error "entity already registered"
		else
			@entities[entity.name] = entity
		return

	processIncomingObject: (msg)->
		to = msg.to||""
		re = @entities[to]
		if re
			re.processIncomingObject msg
		else
			console.warning "message to entity that doesn't exist"
			o = {no:"this entity does not exist"}
			o.rp = msg.rq if msg.rq
			@transmit addAddresses o, msg.from, msg.to

	incoming: (msgo)=>
		msg = undefined
		try
			msg = JSON.parse(msgo.data)
		catch e
			console.error "server just sent me a packet that wasn't JSON. I don't understand " + msgo
			return
		if msg.constructor is Array
			for o in msg
				@processIncomingObject o
		else
			@processIncomingObject msg
		return

	batchPump: ->
		if @batch.length > 0
			if @batch.length is 1
				@actuallyTransmit @batch[0]
			else
				@actuallyTransmit @batch
			@batch = []
		return

	setBatchingLatency: (milliseconds) ->
		if @batchingLatency isnt 0
			clearInterval @batchingIntervalID
			@batchPump()  if milliseconds is 0
		@batchingLatency = milliseconds
		if milliseconds isnt 0
			self = this
			@batchingIntervalID = setInterval(->
				self.batchPump()
				return
			, milliseconds)
		return

	enableBatching: ->
		@setBatchingLatency 10
		return

	disableBatching: ->
		@setBatchingLatency 0
		return

	actuallyTransmit: (msg)->
		@ws.send JSON.stringify(msg)
		return

	transmit: (msg)->
		if @batchingLatency > 0
			@batch.push msg
		else
			@actuallyTransmit msg
		return

	newEntity: (cob)-> #name, onQuery, onMessage
		sen = new ShivEntity cob.name, @
		@registerEntity sen
		sen.onMessage = cob.onMessage if cob.onMessage
		sen.onQuery = cob.onQuery if cob.onQuery
		sen


class @ShivEntity
	newId: 1
	hangingOutFor: null
	tryingToSuffice: null
	recieptListeners: null
	requestListeners: null
	name:null
	constructor: (@name)->
		@hangingOutFor = {}
		@recieptListeners = []
		@requestListeners = []
		@tryingToSuffice = {}
	addAddresses = (o, to, from)->
		o.to = to if to and to.length > 0
		o.from = from if from and from.length > 0
		o
	query: (o, to, through = defaultSock)->
		new Promise (g, b)=>
			@hangingOutFor[@newId] = {g;b}
			through.transmit addAddresses {o:o; rq:@newId}, to, @name
			#TODO timeouts
			@newId += 1
	issue: (o, to, through = defaultSock)->
		through.transmit addAddresses {o:o}, to, @name
	processIncomingObject: (msg, socket)->
		if msg.rq #then is request
			responseId = msg.rq
			++@tryingToSuffice
			fo = @onQuery msg.o, msg.from
			throw Error("onQuery handler must return a Promise") if fo == null
			sendo = (outgoing)->
				@socket.transmit JSON.stringify(addAddresses({rp:responseId; o:outgoing}, msg.from, msg.to))
				--@tryingToSuffice
			sende = (erreason)->
				@socket.transmit JSON.stringify(addAddresses({rp:responseId; no:erreason}, msg.from, msg.to))
				--@tryingToSuffice
			if fo.constructor == Promise
				fo.then sendo, sende
			else
				sendo(fo)
		else if msg.rp #then response
			waiting = @hangingOutFor[msg.rp]
			if waiting
				delete @hangingOutFor[msg.rp]
				if msg.no
					waiting.b msg.no
				else if msg.o
					waiting.g msg.o
				else
					console.error "this message doesn't seem to obey the shivsock protocol; "+JSON.stringify(msg)
			else
				console.error "server sent a response we didn't ask for, "+JSON.stringify(msg)
		else #straight transmission
			@onMessage msg.o
		return
	#override these:
	onQuery: (msg, from)-> #must return a promise[value] or the value itself `from` may be undefined(meaning default entity)
	onMessage: (msg, from)->