package controllers
import util.{Try, Success, Failure, Random}
import concurrent.Future
import scala.collection.mutable.ArrayBuffer
import scala.collection.mutable.HashMap
import scala.collection.mutable.Set
import play.api.libs.json._
import akka.actor._
import Shivsock._

//usage example
class KeeperOfSecretDragons extends ShivsockEntity{
	val name = ""
	var incredulity = 0
	var clientKnowsAboutTheDragons = false
	def response(says:String) = Json.obj("says"-> JsString(says))
	def takeShivsockStatement(r:JsValue, origin:ClientAddress){
		(r \ "says").asOpt[String].foreach {
			case "Dragons are fake as shit." => incredulity += 1
			case "I BELIEVE." => incredulity -= 1
			case _=> {}
		}
	}
	def takeShivsockQuery(r:JsValue, origin:ClientAddress):Future[JsValue] ={
		(r \ "says").asOpt[String] match{
			case Some(str) => str match{
				case "Are dragons real?" => Future.successful(response("No. All dragons are fake and there are no dragons here."))
				case "I know that dragons are real. Let me talk to the dragon." =>
					if(incredulity < 3){
						incredulity = 0
						clientKnowsAboutTheDragons = true
						Future.successful(response("OK fine. Her name is NAGENDRA. You can summon her by crying her name from the top of a hillock or occasionally from the pit of a dingle."))
					}else{
						Future.successful(response("You don't even believe that."))
					}
				case _ => Future.successful(response("I don't understand what you're saying"))
			}
			case None => Future.successful(response("Speak up."))
		}
	}
	
	val NAGENDRA:ActorRef = Shivsock.createNewEntity(classOf[GloriousDragon], "NAGENDRA")
	
	def receive = shivsockEntityReceive
}


class GloriousDragon(val name:String) extends ShivsockEntity{
	var impatience = 0
	var currentlyWith: Option[ClientAddress] = None
	def action(msg:String):JsObject = Json.obj("action"-> JsString(msg))
	def says(msg:String):JsObject = Json.obj("says"-> JsString(msg))
	def boostImpatience(i:Int){
		val wasWith = currentlyWith
		for(js <- impatienceBoost(i); cad <- wasWith) message(cad, js)
	}
	val nothingAction = action("NAGENDRA says nothing.")
	val notHereYouFool = Future.successful(Json.obj("no"-> JsString("NAGENDRA is not with you.")))
	def boostAsResponse(i:Int, jsIfGood:JsObject = nothingAction):Future[JsObject] ={
		Future.successful(impatienceBoost(i) getOrElse { jsIfGood })
	}
	def impatienceBoost(i:Int):Option[JsObject] ={
		impatience += i
		val r = if(impatience >= 11){
			currentlyWith = None
			impatience -= 9
			Some(action("NAGENDRA snaps and burns you to death with her rainbow beams."))
		}else None
		if(impatience < 0) impatience = 0 //NAGENDRA favours no one.
		r
	}
	def embrace(cad:ClientAddress){
		val previouslyWith = currentlyWith
		currentlyWith = Some(cad)
		previouslyWith match{
			case Some(pcad) =>
				message(pcad, action("NAGENDRA suddenly flew away"))
				boostImpatience(4)
			case None =>
				boostImpatience(6)
		}
	}
	def takeShivsockStatement(o:JsValue, origin:ClientAddress){
		currentlyWith.foreach { (cad)=>
			if(cad equals origin)
				(o \ "says").asOpt[String] match{
					case Some(str)=> str match{
						case "AAAAAAUGH!" => boostImpatience(3)
						case "Wow!" => boostImpatience(9)
						case "Such.. beauty.." => boostImpatience( -2 )
						case _ => boostImpatience(2)
					}
					case None => Future.successful(nothingAction)
				}
		}
	}
	def takeShivsockQuery(o:JsValue, origin:ClientAddress):Future[JsValue] ={
		def maybeEmbrace ={
			def embraced = {
				embrace(origin)
				Future.successful(says("THE GREAT AND GLORIOUS NAGENDRA HERSELF DESCENDS FROM THE HEAVENS IN A SHOWER OF GLIMMERING FLECKS OF SILVER."))
			}
			currentlyWith match{
				case Some(c)=>
					if(c equals origin)
						boostAsResponse(4)
					else
						embraced
				case None=>
					embraced
			}
		}
		def maybeCall =
			(o \ "says").asOpt[String] match{
				case Some(s)=>
					if(s equals "NAGENDRA")
						maybeEmbrace
					else
						notHereYouFool
				case None=>
					notHereYouFool
			}
		currentlyWith match{
			case Some(cad)=>
				if(cad equals origin)
					(o \ "says").asOpt[String] match{
						case Some(str)=> str match{
							case "NAGENDRA" => maybeEmbrace
							case "AAAAAAUGH?" => boostAsResponse(5)
							case "Oh NAGENDRA. Would you like to eat this cow?" =>
								Future.successful(says("yes. Where is the cow"))
							case "There's a cow here" =>
								impatienceBoost(-4)
								Future.successful(action("NAGENDRA holds the cow up in her jaws and melts it with the intense rainbow light coming from within her. The cow bubbles and drips down her neck."))
							case "How can I appease you, NAGENDRA?"=>
								boostAsResponse(5)
							case _ => boostAsResponse(2)
						}
						case None => {
							(o \ "action").asOpt[String] match{
								case Some(action)=> action match{
									case "kneels"=> boostAsResponse(-4)
								}
								case None =>{
									Future.successful(action("NAGENDRA says nothing"))
								}
							}
						}
					}
				else
					maybeCall
			case None=>
				maybeCall
		}
	}
	def receive = shivsockEntityReceive
}