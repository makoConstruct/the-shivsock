package controllers
import scala.util.{Try, Failure, Success}
import collection.mutable.{HashSet, HashMap}
import concurrent.{Future, Promise, ExecutionContext}
import akka.actor._

import play.api._
import play.api.libs.json._
import play.api.Play.current
import play.api.libs.iteratee.Concurrent
import play.api.libs.iteratee.Iteratee
import play.api.mvc.WebSocket
import play.api.libs.concurrent.Akka

//the playframework-specific parts of this shivsock server implementation are limited to the use of the Json utilities[which are wrought enough that they should probably not be considered ideosyncratic to play], the use of play's default actor system for initializing the name registrar actor, and what's contained in the ShivsockPlay object, which provides little more than an API for connecting a ShivsockActor to a WebSocket. Replace those and you can safely remove the play imports and take this to Spray or whatever.

import RequestResponseActor.bluntExecutionContext

object Shivsock{
	case class ClientAddress(val entityName:String, val shivSock:ActorRef)
	private def sendRaw(cad: ClientAddress, o:JsObject)(implicit sender:ActorRef){ //~war story: Messages were registering as being sent from deadLetter instead of their true sender. Took me a while to realize it was because this method was being used to send the messages, back when it didn't take an implicit ActorRef from the sender context, the ! would then default to sender:deadLetter like an asshole.
		cad.shivSock ! ToClient(o)
	}
	def createNewEntity[EA <: ShivsockEntity with Actor](cla:java.lang.Class[EA], name:String)(implicit context:ActorContext):ActorRef ={
		val r = context.actorOf(Props(cla, name))
		entityWard ! Instate(r, name)
		r
	}
	
	private val entityWard = Akka.system.actorOf(Props(classOf[EntityWardActor]))
	
	private case class EntityCalled(entityName:String)
	private case class FromClient(o:JsValue)
	private case class ToClient(o:JsObject)
	private case class Instate(entityActor:ActorRef, name:String)
	private class EntityWardActor extends RequestResponseActor{
		val entities = new HashMap[String, ActorRef]
		val connections = new HashSet[(ClientAddress, String)]
		def takeQuery = {
			case EntityCalled(name) => Future.successful(entities get name)
		}
		def takeStatement = {
			case Instate(aref, name)=> entities(name) = aref
		}
	}

	private val noSuchEntity = "this entity does not exist"
	private val notConnected = "you are not connected to this entity"
	private def doesNotExist(toName:String, fromName:String) =
		Json.obj("no"-> JsString(noSuchEntity), "to"-> JsString(toName), "from"-> JsString(fromName))
	private def doesNotExist(rid:Int, toName:String, fromName:String) =
		Json.obj("no"-> JsString(noSuchEntity), "rp"-> JsNumber(rid), "to"-> JsString(toName), "from"-> JsString(fromName))

	//provides methods. Implementors must handle json receipt, IE, copy-paste this: `def receive = { case o:JsValue => handlingAsEntity(o, sender) }`
	trait ShivsockEntity extends Actor with ActorLogging{
		
		//the following methods are to ensure and facilitate proper formatting of outgoing messages
		//if the protocol gets any more complex I should probably use case classes internally
		protected def failure(recipient:ClientAddress, message:String, respondingTo:Int) =
			sendRaw(
				recipient,
				Json.obj("no"-> JsString(message), "rp"-> JsNumber(respondingTo), "to"-> JsString(recipient.entityName), "from"-> JsString(name))
			)
		protected def failure(recipient:ClientAddress, message:String) =
			sendRaw(
				recipient,
				Json.obj("no"-> JsString(message), "to"-> JsString(recipient.entityName), "from"-> JsString(name))
			)
		protected def message(recipient:ClientAddress, o:JsValue) =
			sendRaw(
				recipient,
				if(recipient.entityName.length == 0)
					if(name.length == 0)
						Json.obj("o"-> o)
					else
						Json.obj("o"-> o, "from"-> JsString(name))
				else
					if(name.length == 0)
						Json.obj("o"-> o, "to"-> JsString(recipient.entityName))
					else
						Json.obj("o"-> o, "to"-> JsString(recipient.entityName), "from"-> JsString(name))
			)
		protected def response(recipient:ClientAddress, o:JsValue, rid:Int) =
			sendRaw(
				recipient,
				if(recipient.entityName.length == 0)
					if(name.length == 0)
						Json.obj("o"-> o, "rp"-> JsNumber(rid))
					else
						Json.obj("o"-> o, "rp"-> JsNumber(rid), "from"-> JsString(name))
				else
					if(name.length == 0)
						Json.obj("o"-> o, "rp"-> JsNumber(rid), "to"-> JsString(recipient.entityName))
					else
						Json.obj("o"-> o, "rp"-> JsNumber(rid), "to"-> JsString(recipient.entityName), "from"-> JsString(name))
			)
		protected def request(recipient:ClientAddress, o:JsValue, rid:Int) =
			sendRaw(
				recipient,
				if(recipient.entityName.length == 0)
					if(name.length == 0)
						Json.obj("o"-> o, "rq"-> JsNumber(rid))
					else
						Json.obj("o"-> o, "rq"-> JsNumber(rid), "from"-> JsString(name))
				else
					if(name.length == 0)
						Json.obj("o"-> o, "rq"-> JsNumber(rid), "to"-> JsString(recipient.entityName))
					else
						Json.obj("o"-> o, "rq"-> JsNumber(rid), "to"-> JsString(recipient.entityName), "from"-> JsString(name))
			)
		protected def iDoNotExist(recipient:ClientAddress) =
			sendRaw(
				recipient,
				doesNotExist(recipient.entityName, name)
			)
		protected def iDoNotExist(recipient:ClientAddress, rid:Int) =
			sendRaw(
				recipient,
				doesNotExist(rid, recipient.entityName, name)
			)
		val name:String
		var convid:Int = 0
		val pendingClientResponse = new HashMap[Int, Promise[JsValue]]
		def queryClient(r:JsValue, to:ClientAddress):Future[JsValue] ={
			val pr = Promise[JsValue]()
			val id = convid
			convid += 1
			pendingClientResponse(id) = pr
			request(to, r, id)
			pr.future
		}
		def ordinaryProcessing(o:JsObject, origin:ClientAddress){
			(o \ "rp").asOpt[Int] match{
				case Some(id)=>
					pendingClientResponse get id match{
						case Some(pr)=> pr.success(o \ "o")
						case None=> failure(origin, "I was not expecting that")
					}
				case None=>
					(o \ "rq").asOpt[Int] match{
						case Some(id)=>
							takeClientQuery(o \ "o", origin) onComplete {
								case Success(o) =>
									response(origin, o, id)
								case Failure(e) =>
									failure(origin, e.getMessage, id)
							}
						case None=>
							takeClientStatement(o \ "o", origin)
					}
			}
		}
		def handlingAsEntity(o:JsObject, sentBy:ActorRef){
			val from = (o \ "from").asOpt[String].getOrElse("")
			ordinaryProcessing(o, ClientAddress(from, sentBy))
		}
		def takeClientQuery(o:JsValue, origin:ClientAddress):Future[JsValue]
		def takeClientStatement(o:JsValue, origin:ClientAddress):Unit
	}


	//a websocket connection that just routes messages to the appropriate entity
	class OrdinaryReceiver(sendToClient: JsValue=>Unit ) extends RequestResponseActor with ActorLogging{
		def takeQuery = respondToQueryWithFailure
		def takeStatement = {
			case FromClient(incoming)=>
				incoming match{
					case ar:JsArray => ar.value.foreach { j =>
						j.asOpt[JsObject] match{
							case Some(o)=> processJsObj(o)
							case None=> 
						}
					}
					case o:JsObject => processJsObj(o)
					case e:JsValue => sendToClient(Json.obj("no"-> JsString("shivsock does not accept that kind of json"), "exhibit"-> e))
				}
			case ToClient(o)=> sendToClient(o)
		}
		def processJsObj(o:JsObject){
			val to = (o \ "to").asOpt[String].getOrElse("")
			query(entityWard, EntityCalled(to)) onSuccess {
				case Some(a:ActorRef)=>
					a ! o
				case _=> sendToClient(doesNotExist((o \ "from").asOpt[String].getOrElse(""), to))
			}
		}
	}

	//a socket connection which provides a personal root entity to each connection. Messages from the client routed to "" will go to an entity that belongs to this connection. Messages routed to the client from a different "" will be blocked and reported for the sake of security and reliability.
	class PersonalRoots(sendToClient: JsValue=>Unit, generatePersonalRoot: ActorContext=>ActorRef) extends OrdinaryReceiver(sendToClient) with ActorLogging{
		//~ :/ I would have prefered to just take a type parameter for personal root actors and construct them as that but apparently there is no way of doing this in scala.
		val personalRoot = generatePersonalRoot(context)
		override def takeStatement = {
			case FromClient(incoming)=>
				incoming match{
					case ar:JsArray => ar.value.foreach { j =>
						j.asOpt[JsObject] match{
							case Some(o)=> processJsObj(o)
							case None=> 
						}
					}
					case o:JsObject => processJsObj(o)
					case e:JsValue => sendToClient(Json.obj("no"-> JsString("shivsock does not accept that kind of json"), "exhibit"-> e))
				}
			case ToClient(o)=>
				//ensure this isn't coming from some shadowed global ""
				if(  (o \ "from").asOpt[String].getOrElse("") == ""  &&  sender != personalRoot  )
					log.error("the global \"\" Shivsock entity tried to contact one of the clients, but was shadowed by their client-specific \"\" entity. The two refs are "+sender+" and "+personalRoot)
				else
					sendToClient(o)
		}
		override def processJsObj(o:JsObject){
			val to = (o \ "to").asOpt[String].getOrElse("")
			if(to.length == 0){
				personalRoot ! o
			}else
				query(entityWard, EntityCalled(to)) onSuccess {
					case Some(a:ActorRef)=>
						a ! o
					case _=>
						sendToClient(doesNotExist((o \ "from").asOpt[String].getOrElse(""), to))
				}
		}
	}
	
	object ShivsockPlay{
		def websocketConnection:WebSocket[JsValue] = connactor( outChan=> Akka.system.actorOf(Props(classOf[OrdinaryReceiver], outChan) ) )
		def connectionWithPersonal[Personal <: ShivsockEntity with Actor](personalActorClass:java.lang.Class[Personal]):WebSocket[JsValue] =
			connactor(
				outChan=> Akka.system.actorOf(Props(
					classOf[PersonalRoots],
					outChan,
					{ctx:ActorContext => ctx.actorOf(Props(personalActorClass))} )) )
		//basic function for wiring an actor to a websocket
		private def connactor(makeConnacter: (JsValue=>Unit)=>ActorRef) = WebSocket.using[JsValue] { request =>
			val (out, channel) = Concurrent.broadcast[JsValue]
			val actor = makeConnacter({o =>
				channel.push(o)
			})
			val in = Iteratee.foreach[JsValue]({ o =>
				actor ! FromClient(o)
			})(RequestResponseActor.bluntExecutionContext) //because why not
			(in, out)
		}
	}
}