package models

import play.api.Play.current
import java.util.{Date}
import com.novus.salat._
import com.novus.salat.dao._
import com.mongodb.casbah.Imports._
import se.radley.plugin.salat._
import salatcontext._

case class ReviewData(
  _id: ObjectId = new ObjectId,
  review_id: Option[String] = None,
  rating: Option[Int] = None,
  reviewer_id: Option[String] = None,
  product_id: Option[String] = None,
  client_id: Option[String] = None, 
  review_date: Option[String] = None,
  review_text: Option[String] = None,
  positive_count: Option[Int] = None, 
  negative_count: Option[Int] = None, 
  inappropriate_count: Option[Int] = None
)

object ReviewData extends ModelCompanion[ReviewData, ObjectId] {
  val collection = mongoCollection("reviews")
  val dao = new SalatDAO[ReviewData, ObjectId](collection = collection) {}
  
  def getReviews(clientID: String, productID: String) = dao.find(MongoDBObject("client_id" -> clientID), MongoDBObject("product_id" -> productID))
  def getReviewByClientID(clientID: String): Option[ReviewData] = dao.findOne(MongoDBObject("client_id" -> clientID))
  def addReview(clientID: String, productID: String, reviewText: String) = dao.insert( new ReviewData( client_id = Option(clientID), product_id = Option(productID), review_text = Option(reviewText)))
}