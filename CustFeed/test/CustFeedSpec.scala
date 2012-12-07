import org.specs2.mutable.Specification
import play.api.test._
import play.api.test.Helpers._
import play.api.libs.json.Json



class CustFeedSpec extends Specification {
  "respond to the index Action" in {
    val Some(result) = routeAndCall(FakeRequest(GET, "/"))
    
    status(result) must equalTo(OK)
  }
  
  "post a new product review, using route POST /review/<client_id>/<product_id> with JSON body" in {
    val jsonStr = """{"review_text": "this is the new review text"}"""
    val json = Json.parse(jsonStr)
    
    val Some(result) = routeAndCall(
        FakeRequest(POST, "/review/C01/P01", FakeHeaders(Map("Content-Type" -> Seq("application/json"))), json)
      )
    status(result) must equalTo(OK)
   
  }

}