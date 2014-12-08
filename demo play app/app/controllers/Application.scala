package controllers

import play.api._
import play.api.mvc._
import Shivsock._

object Application extends Controller {
	def index = Action {
		Ok(views.html.index())
	}
	//the default ShivSock Websocket connection, ShivsockPlay.websocket(), will be sufficient for most cases. This one uses a special PersonalRoots connection that will supply each connection with a distinct KeeperOfSecretDragons that no other actor can access, which will be addressed with to:""(or to:undefined). This will shadow the global "". Messages coming from other "" will be blocked and akka-logged as errors.
	def connect = ShivsockPlay.connectionWithPersonal(classOf[KeeperOfSecretDragons])
}