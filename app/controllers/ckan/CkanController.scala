package controllers.ckan

/**
  * Created by ale on 04/04/17.
  */


import javax.inject._

import play.api.mvc._
import play.api.libs.ws._

import scala.concurrent.Future
import play.api.libs.json._
import play.api.inject.ConfigurationProvider
import services.ckan.CkanRegistry
import utils.it.gov.daf.catalogmanager.utilities.WebServiceUtil




@Singleton
class CkanController @Inject() (ws: WSClient, config: ConfigurationProvider) extends Controller {

  import play.api.libs.concurrent.Execution.Implicits.defaultContext

  private val CKAN_URL :String = config.get.getString("app.ckan.url").get

  private val LOCAL_URL :String = config.get.getString("app.local.url").get

  private val ENV:String = config.get.getString("app.type").get

  private val AUTH_TOKEN:String = config.get.getString("app.ckan.auth.token").get


  private def getOrgs(orgId :String): Future[List[String]] = {
      val orgs : Future[WSResponse] = ws.url(LOCAL_URL + "/ckan/organizations/" + orgId).get()
      orgs.map( item => {
          val messages  = (item.json \ "result" \\ "message").map(_.as[String]).toList
          val datasetIds :List[String]= messages.filterNot(_.isEmpty) map { message =>
            println(message)
            val datasetId = message.split("object")(1).trim
            println(datasetId)
            datasetId
          }
           datasetIds
      })
  }

  private def getOrgDatasets(datasetIds : List[String]): Future[Seq[JsValue]] = {
    val datasetResponses: Seq[Future[JsValue]] = datasetIds.map(datasetId => {
         val response = ws.url(LOCAL_URL + "/ckan/dataset/" + datasetId).get
         println(datasetId)
         response map { x =>
           println(x.json.toString)
           (x.json \ "result").getOrElse(JsNull)
         }
    })
    val datasetFuture :Future[Seq[JsValue]] = Future.sequence(datasetResponses)
    datasetFuture
  }



  def createDataset = Action.async { implicit request =>

    /*
    curl -H "Content-Type: application/json" -X POST -d @data.json http://localhost:9000/ckan/createDataset  dove data.json
    contiene:

    {
      "creator_name": "test",
      "maintainer": "",
      "encoding": "UTF-8",
      "issued": "2017-06-01",
      "temporal_start": "2017-06-01",
      "private": false,
      "creation_date": "2017-06-01",
      "num_tags": 1,
      "frequency": "BIENNIAL",
      "publisher_name": "test",
      "metadata_created": "2017-06-01T12:55:19.951016",
      "creator_identifier": "12122",
      "conforms_to": "Regolamento (UE) n. 1089/2010 della Commissione del 23 novembre 2010 recante attuazione della direttiva 2007/2/CE del Parlamento europeo e del Consiglio per quanto riguarda l'interoperabilit\u00e0 dei set di dati territoriali e dei servizi di dati territoriali",
      "metadata_modified": "2017-06-01T12:57:13.553832",
      "author": "",
      "author_email": "",
      "geographical_geonames_url": "http://www.geonames.org/3175395",
      "theme": "ECON",
      "site_url": "http://192.168.1.124",
      "state": "active",
      "version": "",
      "alternate_identifier": "ISBN,TEST",
      "relationships_as_object": [],
      "creator_user_id": "db368512-e344-4711-a428-f4615287ef7c",
      "type": "dataset",
      "ksdlkn": "vvvv",
      "resources": [{
          "cache_last_updated": null,
          "package_id": "8a1a9892-2cab-4454-8bec-d68517580cc5",
          "distribution_format": "XML",
          "webstore_last_updated": null,
          "datastore_active": false,
          "id": "5a15f32b-705f-4d3a-9caa-07525307ff64",
          "size": null,
          "state": "active",
          "hash": "",
          "description": "rete viaria rete ciclabile",
          "format": "XML",
          "mimetype_inner": null,
          "url_type": null,
          "mimetype": null,
          "cache_url": null,
          "name": "rete viaria rete ciclabile",
          "created": "2017-06-01T14:57:07.665875",
          "url": "http://geoservices.buergernetz.bz.it/geoserver/p_bz-transport_network/ows?SERVICE=WMS",
          "webstore_url": null,
          "last_modified": null,
          "position": 0,
          "revision_id": "88fe8aee-1540-4038-80bc-47677640d729",
          "resource_type": null
        }
      ],
      "holder_name": "test",
      "tags": [{
          "vocabulary_id": null,
          "state": "active",
          "display_name": "test",
          "id": "6f19cf3e-0c6a-497d-919c-7953bf64c06e",
          "name": "test"
        }
      ],
      "holder_identifier": "121212",
      "fields_description": "",
      "groups": [],
      "license_id": "other-nc",
      "temporal_end": "2017-06-16",
      "maintainer_email": "",
      "relationships_as_subject": [],
      "is_version_of": "http://dati.retecivica.bz.it/it/dataset/rete-viaria-rete-ciclabile",
      "license_title": "Altro (Non Commerciale)",
      "num_resources": 1,
      "name": "test-dcatapit-api-4",
      "language": "{ENG,ITA}",
      "url": "",
      "isopen": false,
      "owner_org": "mytestorg",
      "modified": "2017-06-01",
      "publisher_identifier": "23232",
      "geographical_name": "ITA_BZO",
      "contact": "",
      "title": "test dcatapit api 2",
      "revision_id": "88fe8aee-1540-4038-80bc-47677640d729",
      "identifier": "12121212",
      "notes": "test description dcatapit api"
    }

    */

    //val AUTH_TOKEN:String = config.get.getString("app.ckan.auth.token").get

    val json:JsValue = request.body.asJson.get

    if(ENV == "dev"){
      CkanRegistry.ckanService.createDataset(json)

      val isOk = Future.successful(JsString("operazione effettuata correttamente"))
      isOk map { x =>
        Ok(x)
      }
    }else{
      val response = ws.url(CKAN_URL + "/api/3/action/package_create").withHeaders("Authorization" -> AUTH_TOKEN).post(json)

      response map { x =>
        println(x.json.toString)
        Ok(x.json)
      }
    }
  }


  def updateDataset(datasetId :String)= Action.async { implicit request =>
    /*
    curl -H "Content-Type: application/json" -X PUT -d @datavv.json http://localhost:9000/ckan/updateDataset/id=81b643bd-007e-44cb-b724-0a02018db6d9
    dove datavv.json contiene:
  {
      "license_title":"Creative Commons Attribution",
      "maintainer":"",
      "geographical_geonames_url":"",
      "issued":"",
      "private":false,
      "maintainer_email":"",
      "num_tags":1,
      "frequency":"ANNUAL",
      "publisher_name":"andrea",
      "id":"81b643bd-007e-44cb-b724-0a02018db6d9",
      "metadata_created":"2017-06-27T17:08:10.884624",
      "creator_identifier":"",
      "conforms_to":"",
      "metadata_modified":"2017-06-27T17:15:06.150414",
      "author":"",
      "author_email":"",
      "isopen":true,
      "theme":"{SOCI,TRAN}",
      "temporal_start":"",
      "state":"active",
      "version":"",
      "alternate_identifier":"",
      "relationships_as_object":[

      ],
      "license_id":"cc-by",
      "type":"dataset",
      "resources":[
         {
            "mimetype":null,
            "cache_url":null,
            "hash":"",
            "description":"",
            "name":"",
            "format":"CSV",
            "url":"http://localhost/pippo.csv",
            "cache_last_updated":null,
            "package_id":"81b643bd-007e-44cb-b724-0a02018db6d9",
            "created":"2017-06-27T17:08:56.845480",
            "state":"active",
            "mimetype_inner":null,
            "distribution_format":"CSV",
            "last_modified":null,
            "position":0,
            "revision_id":"d679dd2b-b972-4f07-b0f4-fed58aea646a",
            "url_type":null,
            "id":"558299e5-cb15-4f6d-ae0c-9beb6f83f949",
            "resource_type":null,
            "size":null
         }
      ],
      "holder_name":"andrea",
      "tags":[
         {
            "vocabulary_id":null,
            "state":"active",
            "display_name":"economy",
            "id":"545104f0-9d0d-46bb-94cd-0ebd0da204d3",
            "name":"economy"
         }
      ],
      "holder_identifier":"231224123234",
      "groups":[

      ],
      "creator_user_id":"c7bdf748-7798-41e7-87e6-0be3b5ede2ad",
      "temporal_end":"",
      "relationships_as_subject":[

      ],
      "is_version_of":"",
      "num_resources":1,
      "name":"mydataset",
      "creator_name":"",
      "url":"",
      "notes":"prova",
      "owner_org":"99154d6b-af89-4797-beb1-14e2a6823672",
      "modified":"2017-06-15",
      "publisher_identifier":"test",
      "geographical_name":"ITA_AGR",
      "license_url":"http://www.opendefinition.org/licenses/cc-by",
      "title":"dataset di prova",
      "revision_id":"6d70740c-c50c-412a-8705-8c5ff659e96b",
      "identifier":"myDatasetId"
   }
    */


    val json:JsValue = request.body.asJson.get

    val response = ws.url(CKAN_URL + "/api/3/action/package_update?id=" + datasetId).withHeaders("Authorization" -> AUTH_TOKEN).post(json)

    response map { x =>
      Ok(x.json)
    }

  }

  def deleteDataset(datasetId :String) = Action.async { implicit request =>

    //curl -H "Content-Type: application/json" -X DELETE http://localhost:9000/ckan/deleteDataset/49f3d5f9-5ad6-4451-9bca-b3287185d19d
    val url = CKAN_URL + "/api/3/action/package_delete"
    val body = s"""{\"id\":\"$datasetId\"}"""
    val test = ws.url(url).withHeaders("Authorization" -> AUTH_TOKEN).post(body)
    println("--"+url)
    test map { response =>
      Ok(response.json)
    }
  }

  def purgeDataset(datasetId :String) = Action.async { implicit request =>

    //curl -H "Content-Type: application/json" -X DELETE http://localhost:9000/ckan/purgeDataset/mydataset

    val url = CKAN_URL + "/api/3/action/dataset_purge"
    val body = s"""{\"id\":\"$datasetId\"}"""
    val test = ws.url(url).withHeaders("Authorization" -> AUTH_TOKEN).post(body)
    test map { response =>
      Ok(response.json)
    }
  }

  def createOrganization = Action.async { implicit request =>

    /*
    curl -H "Content-Type: application/json" -X POST -d @org.json http://localhost:9000/ckan/createOrganization dove org.json contiene
    {
      "description": "Azienda per il Turismo Altopiano di Pin\u00e9 e Valle di Cembra. Maggiori informazioni sul loro [sito web](http://www.visitpinecembra.it)",
      "created": "2014-04-04T12:27:29.698895",
      "title": "APT Altopiano di Pin\u00e8 e Valle di Cembra it",
      "name": "apt-altopiano-di-pine-e-valle-di-cembra2",
      "is_organization": true,
      "state": "active",
      "image_url": "http://dati.trentino.it/images/logo.png",
      "revision_id": "8d453086-2b89-4d9c-8bc2-a86b91978021",
      "type": "organization",
      "id": "232cad97-ecf2-447d-9656-63899023887t",
      "approval_status": "approved"
    }
    * */

    //val AUTH_TOKEN:String = config.get.getString("app.ckan.auth.token").get
    val json:JsValue = request.body.asJson.get

    val response = ws.url(CKAN_URL + "/api/3/action/organization_create").withHeaders("Authorization" -> AUTH_TOKEN).post(json)

    response map { x =>
      println(x.json.toString)
      Ok(x.json)
    }

  }

  def getOrganization(orgId :String) = Action.async { implicit request =>

    val url = CKAN_URL + "/api/3/action/organization_show?id=" + orgId
    println("URL " + url)
    val test = ws.url(url).get
    test map { response =>
      // val bodyResponse :String = response.body
      Ok(response.json)
    }


  }


  def updateOrganization(orgId :String)= Action.async { implicit request =>

    // curl -H "Content-Type: application/json" -X PUT -d @org.json http://localhost:9000/ckan/updateOrganization/id=232cad97-ecf2-447d-9656-63899023887t

    val json:JsValue = request.body.asJson.get

    val response = ws.url(CKAN_URL + "/api/3/action/organization_update?id=" + orgId).withHeaders("Authorization" -> AUTH_TOKEN).post(json)

    response map { x =>
      Ok(x.json)
    }

  }


  def deleteOrganization(orgId :String) = Action.async { implicit request =>


    //curl -H "Content-Type: application/json" -X DELETE http://localhost:9000/ckan/deleteOrganization/apt-altopiano-di-pine-e-valle-di-cembra2

    val url = CKAN_URL + "/api/3/action/organization_delete"
    val body = s"""{\"id\":\"$orgId\"}"""

    val test = ws.url(url).withHeaders("Authorization" -> AUTH_TOKEN).post(body)
    test map { response =>
      Ok(response.json)
    }
  }


  def purgeOrganization(orgId :String) = Action.async { implicit request =>

    //curl -H "Content-Type: application/json" -X DELETE http://localhost:9000/ckan/purgeOrganization/apt-altopiano-di-pine-e-valle-di-cembra2

    val url = CKAN_URL + "/api/3/action/organization_purge"
    val body = s"""{\"id\":\"$orgId\"}"""

    val test = ws.url(url).withHeaders("Authorization" -> AUTH_TOKEN).post(body)
    test map { response =>
      Ok(response.json)
    }
  }


  def getDatasetList = Action.async { implicit request =>
    val test = ws.url(CKAN_URL + "/api/3/action/package_list").get
    test map { response =>
      Ok(response.json)
    }
  }


  def getDatasetListWithResources(limit:Option[Int], offset:Option[Int]) = Action.async { implicit request =>

    // curl -X GET "http://localhost:9000/ckan/datasetsWithResources?limit=1&offset=1"

    val params= Map( ("limit",limit), ("offset",offset) )
    val queryString = WebServiceUtil.buildEncodedQueryString(params)

    val url = CKAN_URL + s"/api/3/action/current_package_list_with_resources$queryString"
    println("url-->"+url)
    val test = ws.url(url).get
    test map { response =>
      Ok(response.json)
    }
  }


  def getOrganizationList = Action.async { implicit request =>
    val test = ws.url(CKAN_URL + "/api/3/action/organization_list").get
    test map { response =>
      println(response.body)
      Ok(response.json)
    }
  }


  def searchDataset(q:Option[String], sort:Option[String], rows:Option[Int]) = Action.async { implicit request =>

    //curl -X GET "http://localhost:9000/ckan/datasetsWithResources?limit=1&offset=1

    val params= Map( ("q",q), ("sort",sort), ("rows",rows) )
    val queryString = WebServiceUtil.buildEncodedQueryString(params)

    val url = CKAN_URL + s"/api/3/action/package_search$queryString"
    println(url)
    val test = ws.url(url).get
    test map { response =>
      Ok(response.json)
    }

  }


  def getOganizationRevisionList(organizationId :String) = Action.async { implicit request =>
    val url = CKAN_URL + "/api/3/action/organization_revision_list?id=" + organizationId
    val test = ws.url(url).get
    test map { response =>
      // val bodyResponse :String = response.body
      Ok(response.json)
    }
  }

  def getDataset(datasetId :String) = Action.async { implicit request =>

    if(ENV == "dev"){
      val dataset = CkanRegistry.ckanService.dataset(datasetId)

      val isOk = Future.successful(dataset)
      isOk map { x =>
        Ok(x)
      }

    }else{
      val url = CKAN_URL + "/api/3/action/package_show?id=" + datasetId
      println("URL " + url)
      val test = ws.url(url).get
      test map { response =>
        // val bodyResponse :String = response.body
        Ok(response.json)
      }
    }

  }

  def getOrganizationDataset(organizationId :String) = Action.async { implicit request =>
    def isNull(v: JsValue) = v match {
      case JsNull => true
      case _ => false
    }
    for {
      datasets <- getOrgs(organizationId)
      dataset: Seq[JsValue] <- getOrgDatasets(datasets)
    } yield {
       Ok(Json.obj("result" -> Json.toJson(dataset.filterNot(isNull(_))), "success" -> JsBoolean(true)))
    }
  }

}