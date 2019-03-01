package repositories.datastory
import java.time.ZonedDateTime
import java.util.UUID

import com.mongodb
import com.mongodb.{DBObject, casbah}
import com.mongodb.casbah.Imports.{MongoCredential, MongoDBObject, ServerAddress}
import com.mongodb.casbah.query.Imports
import com.mongodb.casbah.{MongoClient, TypeImports, commons}
import play.api.Logger
import ftd_api.yaml.{Datastory, Error, Success}
import play.api.libs.json._
import utils.ConfigReader

import scala.concurrent.Future

class DatastoryRepositoryProd extends DatastoryRepository {

  import ftd_api.yaml.BodyReads._
  import ftd_api.yaml.ResponseWrites._


  private val mongoHost: String = ConfigReader.getDbHost
  private val mongoPort = ConfigReader.getDbPort
  private val userName = ConfigReader.userName
  private val source = ConfigReader.database
  private val password = ConfigReader.password

  private val server = new ServerAddress(mongoHost, mongoPort)
  private val credentials = MongoCredential.createCredential(userName, source, password.toCharArray)

  private val collectionName = "datastory"

  private val collection = MongoClient(server, List(credentials))(source)(collectionName)

  private val logger = Logger(this.getClass.getName)

  override def saveDatastory(user: String, datastory: Datastory): Future[Either[Error, Success]] = {
    datastory.id match {
      case Some(id) =>
        val json: JsValue = Json.toJson(datastory)
        val obj: DBObject = com.mongodb.util.JSON.parse(json.toString()).asInstanceOf[DBObject]
        val query = MongoDBObject("id" -> id)
        val responseUpdate: TypeImports.WriteResult = collection.update(query, obj)
        if(responseUpdate.isUpdateOfExisting){
          logger.debug(s"datastory ${datastory.title} updatate by $user")
          Future.successful(Right(Success(Some(s"datastory ${datastory.title} updatate by $user"), None)))
        } else {
          logger.debug(s"error in update datastory ${datastory.title}")
          Future.successful(Left(Error(Some(500), Some(s"error in update datastory ${datastory.title}"), None)))
        }
      case None    =>
        val uid: String = UUID.randomUUID().toString
        val timestamps: ZonedDateTime = ZonedDateTime.now()
        val newDatastory: Datastory = datastory.copy(id = Some(uid), timestamp = Some(timestamps))
        val json: JsValue = Json.toJson(newDatastory)
        val obj: DBObject = com.mongodb.util.JSON.parse(json.toString()).asInstanceOf[DBObject]
        val resultInsert: casbah.TypeImports.WriteResult = collection.insert(obj)
        if(resultInsert.wasAcknowledged()){
          logger.debug(s"datastory ${datastory.title} saved for user $user")
          Future.successful(Right(Success(Some(s"datastory ${datastory.title} saved for user $user"), None)))
        }
        else{
          logger.debug(s"error in save datastory ${datastory.title}")
          Future.successful(Left(Error(Some(500), Some(s"error in save datastory ${datastory.title}"), None)))
        }
    }
  }

  private def getInternalDatastory(id: String): Option[Datastory] = {
    val result: Option[commons.TypeImports.DBObject] = collection.findOne(MongoDBObject("id" -> id))
    val jsonString: String = com.mongodb.util.JSON.serialize(result)
    val json: JsValue = Json.parse(jsonString)
    val datastoryResult: JsResult[Datastory] = json.validate[Datastory]
    datastoryResult match {
      case s: JsSuccess[Datastory] => Some(s.get)
      case e: JsError => logger.debug(s"[getInternalDatastory] error in parsing datastory: $e"); None
    }
  }

  private def delete(id: String, user: String): Future[Either[Error, Success]] = {
    import mongodb.casbah.query.Imports._
    val query = $and(mongodb.casbah.Imports.MongoDBObject("id" -> id), mongodb.casbah.Imports.MongoDBObject("user" -> user))

    val resultDelete: TypeImports.WriteResult = collection.remove(query)
    if(resultDelete.getN > 0) {
      logger.debug(s"$user deleted datastory $id")
      Future.successful(Right(Success(Some(s"$user deleted datastory $id"), None)))
    } else {
      logger.debug(s"$user not delet datastory $id")
      Future.successful(Left(Error(Some(500), Some(s"$user not delet datastory $id"), None)))
    }
  }

  override def deleteDatastory(id: String, user: String): Future[Either[Error, Success]] = {

    getInternalDatastory(id) match {
      case None            => Future.successful(Left(Error(Some(404), Some("datastory not found"), None)))
      case Some(datastory) =>
        if (datastory.user.equals(user)) delete(id, user)
        else Future.successful(Left(Error(Some(401), Some(s"$user unauthorized to delete $datastory"), None)))
    }
  }

  private def createQuery(status: Option[Int], user: String, groups: List[String]): Imports.DBObject = {
    import mongodb.casbah.query.Imports._
    status match {
      case Some(0) =>
        $and(mongodb.casbah.Imports.MongoDBObject("status" -> 0), mongodb.casbah.Imports.MongoDBObject("user" -> user))
      case Some(1) =>
        $and(mongodb.casbah.Imports.MongoDBObject("status" -> 1), "org" $in groups)
      case Some(2) =>
        mongodb.casbah.Imports.MongoDBObject("status" -> 2)
      case None    =>
        $or(
          $and(mongodb.casbah.Imports.MongoDBObject("status" -> 0), mongodb.casbah.Imports.MongoDBObject("user" -> user)),
          $and(mongodb.casbah.Imports.MongoDBObject("status" -> 1), "org" $in groups),
          mongodb.casbah.Imports.MongoDBObject("status" -> 2)
        )
    }
  }

  override def getAllDatastory(user: String, groups: List[String], limit: Option[Int], status: Option[Int]): Future[Either[Error, Seq[Datastory]]] = {
    val result: List[Imports.DBObject] = collection.find(createQuery(status, user, groups))
      .sort(mongodb.casbah.Imports.MongoDBObject("timestamp" -> -1))
      .limit(limit.getOrElse(1000)).toList
    if(result.isEmpty) { logger.debug("Datastories not found"); Future.successful(Left(Error(Some(404), Some("Datastories not found"), None))) }
    else {
      val jsonStrirng: String = com.mongodb.util.JSON.serialize(result)
      val json: JsValue = Json.parse(jsonStrirng)
      val seqDatastory: JsResult[Seq[Datastory]] = json.validate[Seq[Datastory]]
      seqDatastory match {
        case success: JsSuccess[Seq[Datastory]] => logger.debug(s"$user found ${success.get.size}"); Future.successful(Right(success.get))
        case error: JsError => logger.debug(s"[$user] error in get all datastories: $error"); Future.successful(Left(Error(Some(500), Some("Internal Server Error"), None)))
      }
    }
  }

  override def getAllPublicDatastory(limit: Option[Int]): Future[Either[Error, Seq[Datastory]]] = {
    val query = MongoDBObject("status" -> 2)
    val result: List[Imports.DBObject] = collection.find(query)
      .sort(MongoDBObject("timestamp" -> -1))
      .limit(limit.getOrElse(1000)).toList
    if(result.isEmpty) { logger.debug("Datastories not found"); Future.successful(Left(Error(Some(404), Some("Datastories not found"), None))) }
    else {
      val jsonStrirng: String = com.mongodb.util.JSON.serialize(result)
      val json: JsValue = Json.parse(jsonStrirng)
      val seqDatastory: JsResult[Seq[Datastory]] = json.validate[Seq[Datastory]]
      seqDatastory match {
        case success: JsSuccess[Seq[Datastory]] => logger.debug(s"found ${success.get.size}"); Future.successful(Right(success.get))
        case error: JsError => logger.debug(s"error in get all datastories: $error"); Future.successful(Left(Error(Some(500), Some("Internal Server Error"), None)))
      }
    }
  }

  override def getDatastoryById(id: String, user: String, group: List[String]): Future[Either[Error, Datastory]] = {
    import mongodb.casbah.query.Imports._
    val query = $and(
      mongodb.casbah.Imports.MongoDBObject("id" -> id),
      createQuery(None, user, group)
    )
    val result: Option[TypeImports.DBObject] = collection.findOne(query)
    result match {
      case Some(findResult) =>
        val jsonString: String = com.mongodb.util.JSON.serialize(findResult)
        val json: JsValue = Json.parse(jsonString)
        val datastoryResult: JsResult[Datastory] = json.validate[Datastory]
        datastoryResult match {
          case s: JsSuccess[Datastory] => Future.successful(Right(s.get))
          case e: JsError => logger.debug(s"[getDatastoryById] error in parsing datastory: $e"); Future.successful(Left(Error(Some(500), Some("Internal Server Error"), None)))
        }
      case None => Future.successful(Left(Error(Some(404), Some(s"Datastory $id not found"), None)))
    }

  }

  override def getPublicDatastoryById(id: String): Future[Either[Error, Datastory]] = {
    import mongodb.casbah.query.Imports._
    val query = $and(
      mongodb.casbah.Imports.MongoDBObject("id" -> id),
      mongodb.casbah.Imports.MongoDBObject("status" -> 2)
    )
    val result: Option[TypeImports.DBObject] = collection.findOne(query)
    result match {
      case Some(findResult) =>
        val jsonString: String = com.mongodb.util.JSON.serialize(findResult)
        val json: JsValue = Json.parse(jsonString)
        val datastoryResult: JsResult[Datastory] = json.validate[Datastory]
        datastoryResult match {
          case s: JsSuccess[Datastory] => Future.successful(Right(s.get))
          case e: JsError => logger.debug(s"[getDatastoryById] error in parsing datastory: $e"); Future.successful(Left(Error(Some(500), Some("Internal Server Error"), None)))
        }
      case None => Future.successful(Left(Error(Some(404), Some(s"Datastory $id not found"), None)))
    }
  }
}
