package models

import org.jboss.netty.buffer._
import org.joda.time.DateTime
import play.api.data._
import play.api.data.Forms._
import play.api.data.format.Formats._
import play.api.data.validation.Constraints._

import reactivemongo.bson._
import reactivemongo.bson.handlers._

case class Review(
  id: Option[BSONObjectID],
  client_id: String,
  product_id: String,
  review_summary: String,
  review_details: Option[String],
  review_date: DateTime
)
// Turn off your mind, relax, and float downstream
// It is not dying...
object Review {
  implicit object ReviewBSONReader extends BSONReader[Review] {
    def fromBSON(document: BSONDocument) :Review = {
      val doc = document.toTraversable
      Review(
        doc.getAs[BSONObjectID]("_id"),
        doc.getAs[BSONString]("client_id").get.value,
        doc.getAs[BSONString]("product_id").get.value,
        doc.getAs[BSONString]("review_summary").get.value,
        doc.getAs[BSONString]("review_details").map(rd => new String),
        new DateTime(doc.getAs[BSONDateTime]("review_date").get.value)
      )
    }
  }
  implicit object ReviewBSONWriter extends BSONWriter[Review] {
    def toBSON(review: Review) = {
      val bson = BSONDocument(
        "_id" -> review.id.getOrElse(BSONObjectID.generate),
        "client_id" -> BSONString(review.client_id),
        "product_id" -> BSONString(review.product_id),
        "review_summary" -> BSONString(review.review_summary))
      if(review.review_details.isDefined)
        bson += "review_details" -> BSONString(review.review_details.get)
      bson += "review_date" -> BSONDateTime(review.review_date.getMillis())
      bson
    }
  }
  val form = Form(
    mapping(
      "id" -> optional(of[String] verifying pattern(
        """[a-fA-F0-9]{24}""".r,
        "constraint.objectId",
        "error.objectId")),
      "client_id" -> nonEmptyText,
      "product_id" -> nonEmptyText,
      "review_summary" -> nonEmptyText,
      "review_details" -> text
    ) { (id, client_id, product_id, review_summary, /* review_date,*/ review_details) =>
      Review(
        id.map(new BSONObjectID(_)),
        client_id,
        product_id,
        review_summary,
        Option(review_details),
        new DateTime
      )
    } { (review =>
      Some(
        (review.id.map(_.stringify),
        review.client_id,
        review.product_id,
        review.review_summary,
        if(review.review_details.isDefined) review.review_details.get else ""
      )))
    })
}