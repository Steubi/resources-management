package models

/**
 * Created by sremy on 6/1/2015.
 */
case class User(
                 age: Int,
                 firstName: String,
                 lastName: String,
                 feeds: List[Feed])

case class Feed(
                 name: String,
                 url: String)

case class Project(
                  projectName: String,
                  customerName: String,
                  IATACode: String,
                  description: String,
                  startDate: String,
                  endDate: String
                    )

object JsonFormats {
  import play.api.libs.json.Json
  import play.api.data._
  import play.api.data.Forms._

  // Generates Writes and Reads for Feed and User thanks to Json Macros
  implicit val feedFormat = Json.format[Feed]
  implicit val userFormat = Json.format[User]
  implicit val projectFormat = Json.format[Project]
}
