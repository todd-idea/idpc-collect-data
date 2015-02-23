package org.ideaedu

import idea.data.rest.*
import java.util.*
import groovyx.net.http.RESTClient
import groovyx.net.http.ContentType
import groovy.json.JsonBuilder

/**
 * The Main class provides a way to pull IDEA Feedback System data from the
 * IDEA Data Portal. In this case, it pulls all diagnostic data available and
 * dumps the requested data points (raw and adjusted mean and t-score) to a CSV
 * file. It has some optional command line arguments that control the behavior.
 * The arguments include:
 * <ul>
 * <li>h (host) - the hostname of the IDEA Data Portal</li>
 * <li>p (port) - the port to communicate to the IDEA Data Portal on</li>
 * <li>v (verbose) - provide more output on the command line</li>
 * <li>a (app) - the client application name</li>
 * <li>k (key) - the client application key</li>
 * <li>? (help) - show the usage of this</li>
 * </ul>
 *
 * @author Todd Wallentine todd AT IDEAedu org
 */
public class Main {

    private static final def DEFAULT_HOSTNAME = "rest.ideasystem.org"
    private static final def DEFAULT_PORT = 443
    private static final def DEFAULT_BASE_PATH = "IDEA-REST-SERVER/v1"
    private static final def DEFAULT_AUTH_HEADERS = [ "X-IDEA-APPNAME": "", "X-IDEA-KEY": "" ]
    private static final def DEFAULT_PROTOCOL = "https"
    private static final def DEFAULT_REPORT_TYPE = "diagnostic" // short is another valid option here

    /** The maximum number of surveys to get before quitting. */
    private static final def MAX_SURVEYS = 100

    /** The number of surveys to get per page */
    private static final def PAGE_SIZE = 100

    private static def hostname = DEFAULT_HOSTNAME
    private static def protocol = DEFAULT_PROTOCOL
    private static def port = DEFAULT_PORT
    private static def basePath = DEFAULT_BASE_PATH
    private static def authHeaders = DEFAULT_AUTH_HEADERS
    private static def reportType = DEFAULT_REPORT_TYPE

    private static def verboseOutput = false

    private static RESTClient restClient

    public static void main(String[] args) {

        def cli = new CliBuilder( usage: 'Main -v -h host -p port -a "TestClient" -k "ABCDEFG123456"' )
        cli.with {
            v longOpt: 'verbose', 'verbose output'
            h longOpt: 'host', 'host name (default: rest.ideasystem.org)', args:1
            p longOpt: 'port', 'port number (default: 443)', args:1
            a longOpt: 'app', 'client application name', args:1
            k longOpt: 'key', 'client application key', args:1
            '?' longOpt: 'help', 'help'
        }
        def options = cli.parse(args)
        if(options.'?') {
            cli.usage()
            return
        }
        if(options.v) {
            verboseOutput = true
        }
        if(options.h) {
            hostname = options.h
        }
        if(options.p) {
            port = options.p.toInteger()
        }
        if(options.a) {
            authHeaders['X-IDEA-APPNAME'] = options.a
        }
        if(options.k) {
            authHeaders['X-IDEA-KEY'] = options.k
        }



        /*
         * The following will get all the surveys that are available of the
         * given type and print out the overall ratings for each survey subject.
         * This will print the raw and adjusted mean and t-score for each survey
         * subject.
         */
        def surveys = getAllSurveys(reportType)
        if(surveys) {
            // Print the CSV header
            println "SurveySubject,OverallRawMean,OverallRawTScore,OverallAdjMean,OverallAdjTScore"

            surveys.each { survey ->
                def surveySubject = getSurveySubject(survey)
                def courseInfo = getCourseInfo(survey)

                def report = getReport(survey.id, reportType)
                if(report && report.status == "available") {
                    def reportModel = getReportModel(report.id)
                    if(reportModel) {
                        def overallRatings = reportModel.aggregate_data?.overall_ratings
                        if(overallRatings) {
                            print "${surveySubject},${courseInfo},"
                            print "${overallRatings.result.raw.mean},${overallRatings.result.raw.tscore},"
                            println "${overallRatings.result.adjusted.mean},${overallRatings.result.adjusted.tscore}"
                        }
                    }
                }
            }
        } else {
            println "No surveys of that type (${reportType}) are available."
        }
    }

    /**
     * Get the course information (title and number) from the given survey.
     *
     * @param survey The survey to get the course information from.
     * @return The course information for the given survey; might be empty but never null.
     */
    static def getCourseInfo(survey) {
        def courseInfo = ""

        if(survey && survey.course) {
            courseInfo = "${survey.course.title} (${survey.course.number})"
        }

        return courseInfo
    }

    /**
     * Get the survey subject name from the given survey.
     *
     * @param survey The survey to get the survey subject from.
     * @return The survey subject name; might be empty but never null.
     */
    static def getSurveySubject(survey) {
        def surveySubject = ""

        if(survey) {
            def subject = survey.info_form?.respondents[0]
            surveySubject = "${subject.first_name} ${subject.last_name}"
        }

        return surveySubject
    }

    /**
     * Get all the surveys for the given type (chair, admin, diagnostic, short).
     *
     * @param type The type of surveys to retrieve.
     * @return A list of surveys of the given type; might be empty but never null.
     */
    static def getAllSurveys(type) {
        def surveys = []

        def client = getRESTClient()
        def resultsSeen = 0
        def totalResults = Integer.MAX_VALUE
        def currentResults = 0
        def page = 0
        while((totalResults > resultsSeen + currentResults) && (resultsSeen < MAX_SURVEYS)) {
            def response = client.get(
                path: "${basePath}/surveys",
                query: [ types: type, max: PAGE_SIZE, page: page ],
                requestContentType: ContentType.JSON,
                headers: authHeaders)
            if(response.status == 200) {
                if(verboseOutput) {
                    println "Surveys data: ${response.data}"
                }

                response.data.data.each { survey ->
                    surveys << survey
                }

                totalResults = response.data.total_results
                currentResults = response.data.data.size()
                resultsSeen += currentResults
                page++
            } else {
                println "An error occured while getting the surveys by type ${type}: ${response.status}"
                break
            }
        }

        return surveys
    }

    /**
     * Get the report of the given type (reportType) for the given survey (surveyID).
     *
     * @param surveyID The ID of the survey the report is associated with.
     * @param reportType The type of report (comment, extra_likert, diagnostic, short, etc.).
     * @return The report for the given type and survey; this will be null if not found.
     */
    static def getReport(surveyID, reportType) {
        def report

        def client = getRESTClient()
        def response = client.get(
            path: "${basePath}/reports",
            query: [ survey_id: surveyID, type: reportType ],
            requestContentType: ContentType.JSON,
            headers: authHeaders)
        if(response.status == 200) {
            if(verboseOutput) {
                println "Report data: ${response.data}"
            }

            if(response.data && response.data.data && response.data.data.size() > 0) {
                // take the first one ... not sure why we would end up with more than 1
                report = response.data.data.get(0)
            }
        } else {
            println "An error occured while getting the report with ID ${surveyID} and type ${reportType}: ${response.status}"
        }

        return report
    }

    /**
     * Get the report model for the given report (reportID).
     *
     * @param reportID The ID of the report to get the model for.
     * @return The report model for the given report; this will be null if not found.
     */
    static def getReportModel(reportID) {
        def reportModel

        def client = getRESTClient()
        def response = client.get(
            path: "${basePath}/report/${reportID}/model",
            requestContentType: ContentType.JSON,
            headers: authHeaders)
        if(response.status == 200) {
            if(verboseOutput) {
                println "Report model data: ${response.data}"
            }

            reportModel = response.data
        } else {
            println "An error occured while getting the report model with ID ${reportID}: ${response.status}"
        }

        return reportModel
    }

    /**
     * Get an instance of the RESTClient that can be used to access the REST API.
     *
     * @return RESTClient An instance that can be used to access the REST API.
     */
    private static RESTClient getRESTClient() {
        if(restClient == null) {
            if(verboseOutput) println "REST requests will be sent to ${hostname} on port ${port} with protocol ${protocol}"

            restClient = new RESTClient("${protocol}://${hostname}:${port}/")
            restClient.ignoreSSLIssues()
            restClient.handler.failure = { response ->
                if(verboseOutput) {
                    println "The REST call failed with status ${response.status}"
                }
                return response
            }
        }

        return restClient
    }
}