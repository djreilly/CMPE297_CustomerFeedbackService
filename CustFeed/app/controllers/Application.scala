package controllers

import play.api._
import play.api.data._
import play.api.data.Forms._
import play.api.mvc._

object Application extends Controller {
  
  /**
   * Displays the welcome page.
   */
  def index = Action {
    Ok(views.html.index())
  }
  
  /**
   * Displays a form for signing up a new client.
   */
  def clientForm = TODO
  
  /**
   * Adds a client with the given ID to the system.
   */
  def addClient(clientID: String) = TODO
  
  /**
   * Gets the information for the given client.
   */
  def client(clientID: String) = TODO
  
  /**
   * Displays the details for the given client.
   */
  def editClient(clientID: String) = TODO
  
  /**
   * Removes the client with the given ID from the system.
   */
  def removeClient(clientID: String) = TODO
  
  /**
   * Posts a new review for the given client and product.
   */
  def postReview(clientID: String, productID: String) = Action(parse.json) { request =>
    (request.body \ "review_text").asOpt[String].map { reviewText => 
      storeReview(clientID, productID, reviewText)
      Ok("Review received")
    }.getOrElse {
      BadRequest("Missing parameter [review_text]")
    }
  }
  
  /**
   * Gets all reviews for the given client and product.
   */
  def getReviews(clientID: String, productID: String) = TODO
  
  /**
   * Posts a rating of a given existing review as "negative".
   */
  def postReviewRatingNegative(reviewID: String) = TODO
  
  /**
   * Posts a rating of a given existing review as "positive".
   */
  def postReviewRatingPositive(reviewID: String) = TODO
  
  /**
   * Posts a user's rating of a given existing review as "inappropriate".
   */
  def postReviewRatingInappropriate(reviewID: String) = TODO
  
  /**
   * Gets a summary report of the given type for the given client.
   */
  def getSummaryReport(clientID: String, reportType: String) = TODO
  
  /**
   * Gets a product report of the given type for the given client and product.
   */
  def getProductReport(clientID: String, productID: String, reportType: String) = TODO
  
  /**
   * Stores the given product review in the reviews DB.
   */
  def storeReview(clientID: String, productID: String, reviewText: String) = {}
}