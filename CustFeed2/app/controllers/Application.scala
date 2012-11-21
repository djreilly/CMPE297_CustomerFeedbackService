package controllers

import models._
import org.joda.time._
import play.api._
import play.api.mvc._
import play.api.Play.current
import play.modules.reactivemongo._
import scala.concurrent.{ExecutionContext, Future}

import reactivemongo.api._
import reactivemongo.api.gridfs._
import reactivemongo.bson._
import reactivemongo.bson.handlers.DefaultBSONHandlers.DefaultBSONDocumentWriter
import reactivemongo.bson.handlers.DefaultBSONHandlers.DefaultBSONReaderHandler

object Application extends Controller with MongoController {
  val db = ReactiveMongoPlugin.db
  val collection = db("reviews")
  // a GridFS store named 'attachments'
  val gridFS = new GridFS(db, "attachments")

  // let's build an index on our gridfs chunks collection if none
  gridFS.ensureIndex()

  // list all reviews and sort them
  def index = Action { implicit request =>
    Async {
      implicit val reader = Review.ReviewBSONReader
      // empty query to match all the documents
      val query = BSONDocument()
      val sort = getSort(request)
      if(sort.isDefined) {
        // build a selection document with an empty query and a sort subdocument ('$orderby')
        query += "$orderby" -> sort.get
        query += "$query" -> BSONDocument()
      }
      val activeSort = request.queryString.get("sort").flatMap(_.headOption).getOrElse("none")
      // the future cursor of documents
      val found = collection.find(query)
      // build (asynchronously) a list containing all the reviews
      found.toList.map { reviews =>
        Ok(views.html.reviews(reviews, activeSort))
      }
    }
  }

  def showCreationForm = Action {
    Ok(views.html.editReview(None, Review.form, None))
  }

  def showEditForm(id: String) = Action {
    implicit val reader = Review.ReviewBSONReader
    Async {
      val objectId = new BSONObjectID(id)
      // get the documents having this id (there will be 0 or 1 result)
      val cursor = collection.find(BSONDocument("_id" -> objectId))
      // ... so we get optionally the matching review, if any
      // let's use for-comprehensions to compose futures (see http://doc.akka.io/docs/akka/2.0.3/scala/futures.html#For_Comprehensions for more information)
      for {
        // get a future option of a review
        maybeReview <- cursor.headOption
        // if there is some review, return a future of result with the review and its attachments
        result <- maybeReview.map { review =>
          // search for the matching attachments
          // find(...).toList returns a future list of documents (here, a future list of ReadFileEntry)
          gridFS.find(BSONDocument("review" -> review.id.get)).toList.map { files =>
            val filesWithId = files.map { file =>
              file.id.asInstanceOf[BSONObjectID].stringify -> file
            }
            Ok(views.html.editReview(Some(id), Review.form.fill(review), Some(filesWithId)))
          }
        }.getOrElse(Future(NotFound))
      } yield result
    }
  }

  def create = Action { implicit request =>
    Review.form.bindFromRequest.fold(
      errors => Ok(views.html.editReview(None, errors, None)),
      // if no error, then insert the review into the 'reviews' collection
      review => AsyncResult {
        collection.insert(review.copy(review_date = new DateTime())).map( _ =>
          Redirect(routes.Application.index)
        )
      }
    )
  }

  def edit(id: String) = Action { implicit request =>
    Review.form.bindFromRequest.fold(
      errors => Ok(views.html.editReview(Some(id), errors, None)),
      review => AsyncResult {
        val objectId = new BSONObjectID(id)
        // create a modifier document, ie a document that contains the update operations to run onto the documents matching the query
        val modifier = BSONDocument(
          // this modifier will set the fields 'client_id', 'product_id', and 'review_summary', 'review_details', and 'review_date'
          "$set" -> BSONDocument(
            "client_id" -> BSONString(review.client_id),
            "product_id" -> BSONString(review.product_id),
            "review_summary" -> BSONString(review.review_summary),
            "review_details" -> BSONString(review.review_details.get),
            "review_date" -> BSONDateTime(new DateTime().getMillis)))
        // ok, let's do the update
        collection.update(BSONDocument("_id" -> objectId), modifier).map { _ =>
          Redirect(routes.Application.index)
        }
      }
    )
  }

  def delete(id: String) = Action {
    Async {
      // let's collect all the attachments matching that match the review to delete
      gridFS.find(BSONDocument("review" -> new BSONObjectID(id))).toList.flatMap { files =>
        // for each attachment, delete their chunks and then their file entry
        val deletions = files.map { file =>
          gridFS.remove(file)
        }
        Future.sequence(deletions)
      }.flatMap { _ =>
        // now, the last operation: remove the review
        collection.remove(BSONDocument("_id" -> new BSONObjectID(id)))
      }.map(_ => Ok).recover { case _ => InternalServerError}
    }
  }

  // save the uploaded file as an attachment of the review with the given id
  def saveAttachment(id: String) = Action(gridFSBodyParser(gridFS)) { request =>
    // the reader that allows the 'find' method to return a future Cursor[Review]
    implicit val reader = Review.ReviewBSONReader
    // first, get the attachment matching the given id, and get the first result (if any)
    val cursor = collection.find(BSONDocument("_id" -> new BSONObjectID(id)))
    val uploaded = cursor.headOption

    val futureUpload = for {
      // we filter the future to get it successful only if there is a matching review
      review <- uploaded.filter(_.isDefined).map(_.get)
      // we wait (non-blocking) for the upload to complete. (This example does not handle multiple files uploads).
      putResult <- request.body.files.head.ref
      // when the upload is complete, we add the review id to the file entry (in order to find the attachments of the review)
      result <- gridFS.files.update(BSONDocument("_id" -> putResult.id), BSONDocument("$set" -> BSONDocument("review" -> review.id.get)))
    } yield result

    Async {
      futureUpload.map {
        case _ => Redirect(routes.Application.showEditForm(id))
      }.recover {
        case _ => BadRequest
      }
    }
  }

  def getAttachment(id: String) = Action {
    Async {
      // find the matching attachment, if any, and streams it to the client
      serve(gridFS.find(BSONDocument("_id" -> new BSONObjectID(id))))
    }
  }

  def removeAttachment(id: String) = Action {
    Async {
      gridFS.remove(new BSONObjectID(id)).map(_ => Ok).recover { case _ => InternalServerError }
    }
  }

  private def getSort(request: Request[_]) = {
    request.queryString.get("sort").map { fields =>
      val orderBy = BSONDocument()
      for(field <- fields) {
        val order = if(field.startsWith("-"))
          field.drop(1) -> -1
        else field -> 1

        if(order._1 == "client_id" || order._1 == "product_id" || order._1 == "review_summary" || order._1 == "review_date")
          orderBy += order._1 -> BSONInteger(order._2)
      }
      orderBy
    }
  }
}