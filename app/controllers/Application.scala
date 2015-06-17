package controllers

import play.api._
import play.api.libs.iteratee.Iteratee
import play.api.mvc._

import play.api.libs.concurrent.Execution.Implicits.defaultContext
import play.api.libs.functional.syntax._
import play.api.libs.json._
import reactivemongo.bson.BSONDocument
import reactivemongo.core.commands.LastError
import scala.concurrent.Future
import scala.concurrent._
import ExecutionContext.Implicits.global

// Reactive Mongo imports
import reactivemongo.api._

// Reactive Mongo plugin, including the JSON-specialized collection
import play.modules.reactivemongo.MongoController
import play.modules.reactivemongo.json.collection.JSONCollection

object Application extends Controller {

  def index = Action {
    Ok(views.html.index("Your new application is ready."))
  }

  def rm = Action{
    Ok(views.html.rm())
  }

}

object ApplicationUsingJsonReadersWriters extends Controller with MongoController {
  /*
   * Get a JSONCollection (a Collection implementation that is designed to work
   * with JsObject, Reads and Writes.)
   * Note that the `collection` is not a `val`, but a `def`. We do _not_ store
   * the collection reference to avoid potential problems in development with
   * Play hot-reloading.
   */
  def collection: JSONCollection = db.collection[JSONCollection]("persons")
  def projectCollection: JSONCollection = db.collection[JSONCollection]("projects")

  // ------------------------------------------ //
  // Using case classes + Json Writes and Reads //
  // ------------------------------------------ //
  import play.api.data.Form
  import models._
  import models.JsonFormats._

  def deleteProject = Action.async(parse.json) { request =>
    /*
     * request.body is a JsValue.
     * There is an implicit Writes that turns this JsValue as a JsObject,
     * so you can call insert() with this JsValue.
     * (insert() takes a JsObject as parameter, or anything that can be
     * turned into a JsObject using a Writes.)
     */
    Logger.debug("Hello World from deleteProject")
    Logger.debug("Request:" + request.body.toString() + "projectID=" + request.body \ "projectID")
    val myProjectID = request.body\"projectID"


    val myJsonObjectQuery = Json.obj("projectID"->myProjectID)
    Logger.debug("query:"+myJsonObjectQuery.toString())

    val myObjectsHead = projectCollection.find(Json.obj("projectID" -> request.body \ "projectID")).
      cursor[JsObject].headOption

    myObjectsHead.flatMap {
      obj => obj match {
        case None => Future({
          Logger.debug("Entry not found")
          BadRequest("Entry not found")
        })
        case Some(obj2) => {
          Logger.debug("Obj2:" + obj2.toString())
          projectCollection.remove(Json.obj("projectID" -> request.body \ "projectID")).map(
            lastError => {
              Logger.debug(lastError.toString)
              Ok
            }
          )
         }
      }
    }
  }



  def saveProject = Action.async(parse.json) { request =>
    /*
     * request.body is a JsValue.
     * There is an implicit Writes that turns this JsValue as a JsObject,
     * so you can call insert() with this JsValue.
     * (insert() takes a JsObject as parameter, or anything that can be
     * turned into a JsObject using a Writes.)
     */

    // Get the projectID passed by the POST request. We need to check if it's a new project or not.
    Logger.debug("Request:" + request.body.toString() + " projectID=" + request.body \ "projectID")
    val myProjectID = (request.body \ "projectID")

    //Check the value of myProjectID, if it's NEW, then we have a new project we need to save.
    // If not, it is an existing one that needs to be updated.
    if (myProjectID.toString() == "\"NEW\"") {
      //Logger.debug("NEW project")
      //We have a new project
      //The first step is to know what should be the project ID.
      // So, we will query the database to get the list of records, order them by projectIDs, and take the highest one
      val query = "{ projectID: { $gt: 0 } }, { projectID: 1}" //To be used later to only return the list of projectIDs
      // instead of full records?
      val myJsonObjectQuery = Json.obj("projectID" -> Json.obj("$gt" -> 0))
      val myObjectsIDHead = projectCollection.find(myJsonObjectQuery).sort(Json.obj("projectID" -> -1)).
        cursor[JsObject].headOption

      //Now we check what was returned, if nothing is returned, it means that the projects collection is empty.
      myObjectsIDHead.flatMap {
        obj => obj match {
          case None => {
            //Nothing found, the collection is empty, the projectID should be 1
            val toSaveQuery = request.body.as[JsObject].deepMerge(Json.obj("projectID" -> 1))
            projectCollection.save(toSaveQuery).map { lastError =>
              Logger.debug(s"Successfully updated with LastError: $lastError")
              Created(Json.obj("projectID" -> 1))
            }
          }
          case Some(obj) => {
            //There are already some projects in the collection. Increase the value of the highest projectID by 1.
            //It will be the projectID of the new project
            val highestProjectID = ((obj \ ("projectID")).toString().toFloat + 1).##
            val toSaveQuery = request.body.as[JsObject].deepMerge(Json.obj("projectID" -> highestProjectID))
            projectCollection.save(toSaveQuery).map { lastError =>
              Logger.debug(s"Successfully updated with LastError: $lastError")
              Created(Json.obj("projectID" -> highestProjectID))
            }
          }
        }
      }
    }
    else {
      //Logger.debug("Project should exist")
      //this project should already exist. We need to find it, and merge the new data
      val myObjectsHead = projectCollection.find(Json.obj("projectID" -> request.body \ "projectID")).
        cursor[JsObject].headOption
      myObjectsHead.flatMap {
        obj => obj match {
          case None => {
            //Nothing has been found, this is a problem, the projectID must be wrong.
            Future(BadRequest("Record not found"))
          }
          case Some(obj2) => {
            //A project has been found in the collection, merge it with the new data, and save it
            projectCollection.update(Json.obj("projectID" -> request.body \ "projectID"),
                                      obj2.deepMerge(request.body.as[JsObject])).map { lastError =>
              Logger.debug(s"Successfully updated with LastError: $lastError")
              Created
            }
          }
        }
      }
    }
  }


  def getAllProjects() = Action.async {
    val myJsonObjectQuery = Json.obj("projectID"->Json.obj("$gt"->0))
    // let's do our query
    val cursor: Cursor[JsObject] = projectCollection.
      // find all projects
      find(myJsonObjectQuery).sort(Json.obj("projectID" -> 1)).
      // perform the query and get a cursor of JsObject
      //cursor[Project]
      cursor[JsObject]


    // gather all the JsObjects in a list
      val futureProjectsList: Future[List[JsObject]] = cursor.collect[List]()

    // transform the list into a JsArray
    val futureProjectsJsonArray: Future[JsArray] = futureProjectsList.map { projects => Json.arr(projects)}

    // everything's ok! Let's reply with the array
    futureProjectsJsonArray.map { projects => Ok(projects)}
  }

}