package ru.serbis.okto.node.runtime.senv

import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.Materializer
import akka.util.ByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.http.HttpProxy
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.{Failure, Success}

/**
  * Implementation of methods for working with HTTP protocol
  */
class VHttp(http: HttpProxy, mat: Materializer) extends StreamLogger {

  setLogSourceName(s"VHttp*${System.currentTimeMillis()}")
  setLogKeys(Seq("VHttp"))

  implicit val logQualifier = LogEntryQualifier("static")

  /**
    *  Performs some http request and returns the result of its execution
    *
    * @param method http method. Empty string defines as GET method
    * @param uri link for request
    * @param contentType entity content type
    * @param entity entity as string
    * @param headers paired headers list, say [h1, v1, h2, v2]
    * @param timeout timeout for request
    * @param response wait response. If this parameter is false, program does not wait for result of request and
    *                 return immediately after it.
    * @return http response result, see it's comment for details
    */
  def req(method: String, uri: String, contentType: String, entity: String, headers: Array[String], timeout: Long, response: Boolean): VHttpRequestResult = {
    implicit val logQualifier = LogEntryQualifier("req")

    def strParams = s"method=$method, uri=$uri, contentType=$contentType, entity=$entity, headers=$headers, timeout=$timeout, response=$response"


    if (method == null ||uri == null ||contentType == null || entity == null || headers == null) {
      logger.warning(s"Unable to perform request, some of the input params is null [$strParams]")
      new VHttpRequestResult(1)
    } else {
      val zm = if (method.length == 0) "GET" else method
      val iMethod = HttpMethod.custom(zm)
      val iContentType = ContentType.parse(contentType)
      if (iContentType.isRight) {
        val iEntity = HttpEntity(iContentType.right.get, ByteString(entity).toArray)
        if (headers.length % 2 == 0) {
          val iHeaders = headers.foldLeft((None: Option[String], Seq.empty[RawHeader]))((a, v) => {
            if (a._1.isEmpty)
              (Some(v), a._2)
            else {
              (None, RawHeader(a._1.get, v) +: a._2)
            }
          })._2

          val request = HttpRequest(iMethod, uri, entity = iEntity).withHeaders(iHeaders: _*)
          if (response) {
            Await.ready(http.singleRequest(request), timeout milliseconds).value.get match {
              case Success(r) =>
                Await.ready(r.entity.toStrict(timeout milliseconds)(mat), timeout milliseconds).value.get match {
                  case Success(r1) =>
                    logger.debug(s"Success http request with result [status=${r.status.intValue()}, entity=${r1.data.utf8String}, $strParams]")
                    new VHttpRequestResult(0, r.status.intValue(), r1.data.utf8String)
                  case Failure(e1) => //NOT TESTABLE
                    logger.warning(s"Unable to perform request, because entity strictification was failed by error [err=${e1.getMessage}, $strParams]")
                    new VHttpRequestResult(5, 0, e1.getMessage)
                }
              case Failure(e) =>
                logger.warning(s"Unable to perform request, because request was failed by error [err=${e.getMessage}, $strParams]")
                new VHttpRequestResult(4, 0, e.getMessage)
            }
          } else {
            http.singleRequest(request)
            logger.debug(s"Success http request with discarded result [$strParams]")
            new VHttpRequestResult(0)
          }
        } else {
          logger.warning(s"Unable to perform request, headers is not paired  [$strParams]")
          new VHttpRequestResult(3)
        }

      } else {
        logger.warning(s"Unable to perform request, incorrect content type [$strParams]")
        new VHttpRequestResult(2)
      }
    }
  }

  /** The result of the http request
    *
    * @param error error code. It is may be:
    *                  0 - result was success
    *                  1 - some of the input parameters is null
    *                  2 - incorrect content type
    *                  3 - headers is not paired
    *                  4 - http request complete as failure. This error may occurs only if some operation implicitly wait
    *                      response. Result field will contain error message
    *                  5 - http entity strictification complete with failure  This error may occurs only if some operation
    *                      implicitly wait response. Result field will contain error message
    * @param code http response status code. This value will be 0 if some error was occurs or if waiting response is
    *             discarded
    * @param result result of operation. It contain response entity as string, empty string if error was occurs of error
    *               message for some errors
    */
  class VHttpRequestResult(val error: Int, val code: Int = 0, val result: String = "")
}
