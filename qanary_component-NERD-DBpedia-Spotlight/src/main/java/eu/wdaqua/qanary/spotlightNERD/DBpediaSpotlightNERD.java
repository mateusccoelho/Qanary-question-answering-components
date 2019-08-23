package eu.wdaqua.qanary.spotlightNERD;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.net.URLEncoder;
import java.util.Scanner;
import java.util.ArrayList;
import java.util.List;
import java.io.InputStream;
import org.json.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import eu.wdaqua.qanary.commons.QanaryMessage;
import eu.wdaqua.qanary.component.QanaryComponent;


@Component
/**
 * This component connected automatically to the Qanary pipeline.
 * The Qanary pipeline endpoint defined in application.properties (spring.boot.admin.url)
 * @see <a href="https://github.com/WDAqua/Qanary/wiki/How-do-I-integrate-a-new-component-in-Qanary%3F" target="_top">Github wiki howto</a>
 */
public class DBpediaSpotlightNERD extends QanaryComponent {
	private static final Logger logger = LoggerFactory.getLogger(DBpediaSpotlightNERD.class);

	/**
	 * implement this method encapsulating the functionality of your Qanary
	 * component
	 */
	@Override
	public QanaryMessage process(QanaryMessage myQanaryMessage) {
        long startTime = System.currentTimeMillis();
        org.apache.log4j.Logger.getRootLogger().setLevel(org.apache.log4j.Level.OFF);

        // retrive the question from Qanary KB
        QanaryUtils myQanaryUtils = this.getUtils(myQanaryMessage);
        QanaryQuestion<String> myQanaryQuestion = this.getQanaryQuestion(myQanaryMessage);
        String myQuestion = myQanaryQuestion.getTextualRepresentation();
        logger.info("Question: {}", myQuestion);

        // API address
        String request_url = "https://api.dbpedia-spotlight.org/en/annotate?text=";

        // builds the request url
        try {
            request_url += URLEncoder.encode(myQuestion, "UTF-8");
            logger.info("URL is: {}", request_url);
        } catch (Exception e) {
            e.printStackTrace();
            logger.warn(e.getMessage());
        }

        List<String> retLst = new ArrayList<String>();
        try {
            // makes the request
            URL url = new URL(request_url);
            URLConnection urlConnection = url.openConnection();
            urlConnection.setRequestProperty("accept", "application/json");
            HttpURLConnection connection = null;
            connection = (HttpURLConnection) urlConnection;

            // gets the request content
            InputStream is = connection.getInputStream();
            Scanner s = new java.util.Scanner(is).useDelimiter("\\A");
            String request_content = s.hasNext() ? s.next() : "";

            // parse JSON
            JSONObject json = new JSONObject(request_content);
            JSONArray resources = json.getJSONArray("Resources");
            for(int i = 0; i < resources.length(); i++) {
                JSONObject resource = arr.getJSONObject(i);
                String uri = resource.getString("@URI");
                String surface = resource.getString("@surfaceForm");
                String offset = resource.getString("@offset");
                int start = Integer.parseInt(offset)
                int end = surface.length() + Integer.parseInt(offset)

                // push each resource found by dbpedia spotlight into the Qanary KB
                String sparql = "PREFIX qa: <http://www.wdaqua.eu/qa#> " //
                              + "PREFIX oa: <http://www.w3.org/ns/openannotation/core/> " //
                              + "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#> " //
                              + "INSERT { " + "GRAPH <" + myQanaryQuestion.getOutGraph() + "> { " //
                              + "  ?a a qa:AnnotationOfInstance . " //
                              + "  ?a oa:hasTarget [ " //
                              + "           a    oa:SpecificResource; " //
                              + "           oa:hasSource    <" + myQanaryQuestion.getUri() + ">; " //
                              + "           oa:hasSelector  [ " //
                              + "                    a oa:TextPositionSelector ; " //
                              + "                    oa:start \"" + start + "\"^^xsd:nonNegativeInteger ; " //
                              + "                    oa:end  \"" + end + "\"^^xsd:nonNegativeInteger  " //
                              + "           ] " //
                              + "  ] . " //
                              + "  ?a oa:hasBody <" + uri + "> ;" //
                              + "     oa:annotatedBy <https://api.dbpedia-spotlight.org/en/> ; " //
                              + "	    oa:AnnotatedAt ?time  " + "}} " //
                              + "WHERE { " //
                              + "  BIND (IRI(str(RAND())) AS ?a) ."//
                              + "  BIND (now() as ?time) " //
                              + "}";
                myQanaryUtils.updateTripleStore(sparql, myQanaryQuestion.getEndpoint().toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

        long estimatedTime = System.currentTimeMillis() - startTime;
        logger.info("Time {}", estimatedTime);

		return myQanaryMessage;
	}

}
